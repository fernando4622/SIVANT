import { HttpErrorResponse } from '@angular/common/http';
import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter, withComponentInputBinding } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { Subject, of, throwError } from 'rxjs';
import { Rol } from '../../../core/auth/rol';
import { Sesion } from '../../../core/auth/sesion';
import { TiempoReal } from '../../../core/realtime/tiempo-real';
import { sumaDinero } from '../../../shared/formato';
import { ProductosApi } from '../../productos/data-access/productos.api';
import { rutasCuentas } from '../cuentas.routes';
import { Comanda, Cuenta, ServicioApi } from '../data-access/servicio.api';
import { Mesas } from './mesas';

const COMANDA: Comanda = {
  id: '7',
  cuentaId: '3',
  estado: 'ENVIADA',
  fechaHoraEnvio: '2026-11-25T18:00:00Z',
  version: 2,
  subtotal: '55.50',
  detalles: [
    { id: '11', productoId: '1', producto: 'Agua', cantidad: 2, precioUnitario: '20.00', subtotal: '40.00', observaciones: '' },
    { id: '12', productoId: '2', producto: 'Galleta', cantidad: 1, precioUnitario: '15.50', subtotal: '15.50', observaciones: 'Sin azúcar' },
  ],
  acciones: ['EDITAR'],
};
const CUENTA: Cuenta = {
  id: '3',
  mesaId: '1',
  mesa: 5,
  mesero: 'Juan Pérez',
  estado: 'ABIERTA',
  fechaHoraApertura: '2026-11-25T17:00:00Z',
  version: 4,
  total: '55.50',
  comandas: [COMANDA],
  acciones: ['ENVIAR_COMANDA'],
};

const fallo = (status: number, codigo: string, detail: string) =>
  throwError(() => new HttpErrorResponse({ status, error: { codigo, detail } }));

describe('Mesas, cuentas y comandas', () => {
  const api = {
    mesas: vi.fn(),
    abrir: vi.fn(),
    cuenta: vi.fn(),
    cerrarSinCobro: vi.fn(),
    enviar: vi.fn(),
    editar: vi.fn(),
    cancelar: vi.fn(),
  };
  const productos = { listar: vi.fn() };
  const perfil = signal<{ id: string; nombre: string; rol: Rol }>({ id: '1', nombre: 'J', rol: 'MESERO' });
  let eventos: Subject<null>;
  let harness: RouterTestingHarness;
  let el: HTMLElement;

  beforeAll(
    () =>
      Promise.all([
        import('./detalle-cuenta'),
        import('../../comandas/pages/nueva-comanda'),
        import('../../comandas/pages/modificar-comanda'),
        import('../../comandas/pages/cancelar-comanda'),
      ]),
    60_000,
  );

  beforeEach(async () => {
    vi.resetAllMocks();
    perfil.set({ id: '1', nombre: 'J', rol: 'MESERO' });
    eventos = new Subject();
    api.mesas.mockReturnValue(
      of([
        { id: '1', numero: 1, disponible: true },
        { id: '2', numero: 2, disponible: false, cuentaId: '3', mesero: 'Juan Pérez' },
        { id: '4', numero: 4, disponible: false, mesero: 'Ana' },
      ]),
    );
    api.cuenta.mockReturnValue(of(CUENTA));
    productos.listar.mockReturnValue(
      of({
        contenido: [{ id: '1', nombre: 'Agua', precioVenta: '20.00', tipo: 'REVENTA', activo: true }],
        page: 0,
        size: 10,
        totalElementos: 1,
        totalPaginas: 1,
      }),
    );
    HTMLDialogElement.prototype.showModal = function () {
      this.open = true;
    };
    HTMLDialogElement.prototype.close = function () {
      this.open = false;
    };
    TestBed.configureTestingModule({
      providers: [
        provideRouter(
          [
            { path: 'mesas', component: Mesas },
            { path: 'cuentas/:id', children: rutasCuentas },
            { path: 'inicio', children: [] },
          ],
          withComponentInputBinding(),
        ),
        { provide: ServicioApi, useValue: api },
        { provide: ProductosApi, useValue: productos },
        { provide: Sesion, useValue: { perfil } },
        { provide: TiempoReal, useValue: { cambios: () => eventos, conectado: signal(true) } },
      ],
    });
    harness = await RouterTestingHarness.create();
  });

  async function refrescar() {
    harness.fixture.detectChanges();
    await harness.fixture.whenStable();
    harness.fixture.detectChanges();
  }

  async function abrir(url: string) {
    await harness.navigateByUrl(url);
    await refrescar();
    el = harness.fixture.nativeElement;
  }

  const boton = (texto: string) =>
    [...el.querySelectorAll('button, a')].find((b) =>
      (b.getAttribute('aria-label') ?? b.textContent)?.includes(texto),
    ) as HTMLButtonElement | undefined;

  async function pulsar(texto: string) {
    boton(texto)!.click();
    await refrescar();
  }

  it('CP-F42 el mesero abre una mesa libre y solo ve sus cuentas', async () => {
    api.abrir.mockReturnValue(of(CUENTA));
    await abrir('/mesas');
    const tarjetas = [...el.querySelectorAll('li')].map((t) => t.textContent);
    expect(tarjetas[0]).toContain('Abrir cuenta');
    expect(tarjetas[1]).toContain('Ver cuenta');
    expect(tarjetas[2]).not.toMatch(/Abrir|Ver/);
    await pulsar('Abrir cuenta');
    expect(api.abrir).toHaveBeenCalledWith('1', expect.any(String));
    expect(TestBed.inject(Router).url).toBe('/cuentas/3');
  });

  it('CP-F43 si otro mesero ganó la mesa, muestra el motivo y actualiza', async () => {
    api.abrir.mockReturnValue(fallo(409, 'MESA_OCUPADA', 'La mesa ya tiene una cuenta abierta.'));
    await abrir('/mesas');
    await pulsar('Abrir cuenta');
    expect(el.textContent).toContain('ya tiene una cuenta');
    expect(api.mesas).toHaveBeenCalledTimes(2);
  });

  it('CP-F44 el cajero consulta mesas sin abrir cuentas', async () => {
    perfil.set({ id: '2', nombre: 'C', rol: 'CAJERO' });
    await abrir('/mesas');
    expect(boton('Abrir cuenta')).toBeUndefined();
    expect(boton('Ver cuenta')).toBeDefined();
  });

  it('CP-F45 la cuenta muestra comandas, total y acciones de la API; un evento la actualiza sin contar como actividad', async () => {
    await abrir('/cuentas/3');
    expect(el.textContent).toContain('Mesa 5');
    expect(el.textContent).toContain('Sin azúcar');
    expect(boton('Modificar comanda 7')).toBeDefined();
    expect(boton('Cancelar comanda')).toBeUndefined();
    expect(boton('Cerrar sin cobro')).toBeUndefined();
    api.cuenta.mockReturnValue(
      of({
        ...CUENTA,
        total: '0.00',
        acciones: ['ENVIAR_COMANDA', 'CERRAR_SIN_COBRO'],
        comandas: [
          {
            ...COMANDA,
            estado: 'CANCELADA',
            acciones: [],
            cancelacion: { cajero: 'María', fechaHora: '2026-11-25T18:05:00Z', justificacion: 'Se retiró' },
          },
        ],
      }),
    );
    eventos.next(null);
    await refrescar();
    expect(api.cuenta).toHaveBeenLastCalledWith('3', true);
    expect(el.textContent).toContain('Cancelada por María');
    expect(el.textContent).toContain('Se retiró');
    expect(el.textContent).toContain('$0.00');
  });

  it('CP-F46 cerrar sin cobro exige confirmar', async () => {
    api.cuenta.mockReturnValue(of({ ...CUENTA, comandas: [], acciones: ['ENVIAR_COMANDA', 'CERRAR_SIN_COBRO'] }));
    api.cerrarSinCobro.mockReturnValue(of(CUENTA));
    await abrir('/cuentas/3');
    await pulsar('Cerrar sin cobro');
    expect(api.cerrarSinCobro).not.toHaveBeenCalled();
    el.querySelector<HTMLButtonElement>('dialog .boton--peligro')!.click();
    await refrescar();
    expect(api.cerrarSinCobro).toHaveBeenCalledWith('3', 4, expect.any(String));
    expect(TestBed.inject(Router).url).toBe('/mesas');
  });

  it('CP-F47 la comanda nueva acumula productos, calcula y conserva lo capturado si falla', async () => {
    api.enviar.mockReturnValueOnce(fallo(0, '', ''));
    api.enviar.mockReturnValue(of(COMANDA));
    await abrir('/cuentas/3/comandas/nueva');
    await vi.waitFor(async () => {
      await refrescar();
      expect(boton('Agregar Agua')).toBeDefined();
    });
    expect(productos.listar).toHaveBeenCalledWith({ q: '', tipo: '', page: 0, activo: 'true' });
    expect(boton('Enviar a cocina')!.disabled).toBe(true);
    await pulsar('Agregar Agua');
    await pulsar('Agregar Agua');
    expect(el.querySelector('.total')!.textContent).toContain('$40.00');
    const nota = el.querySelector<HTMLInputElement>('[aria-label="Observaciones de Agua"]')!;
    nota.value = 'Fría';
    nota.dispatchEvent(new Event('input'));
    await pulsar('Enviar a cocina');
    expect(el.textContent).toContain('No hay conexión');
    await pulsar('Enviar a cocina');
    const [primera, segunda] = api.enviar.mock.calls;
    expect(primera[1]).toEqual([{ productoId: '1', cantidad: 2, observaciones: 'Fría' }]);
    expect(segunda[2]).toBe(primera[2]);
    expect(TestBed.inject(Router).url).toBe('/cuentas/3');
  });

  it('CP-F48 modificar cambia cantidades y elimina, pero conserva un producto', async () => {
    api.editar.mockReturnValue(of(CUENTA));
    await abrir('/cuentas/3/comandas/7/modificar');
    await pulsar('Agregar uno de Agua');
    expect(el.querySelector('.total')!.textContent).toContain('$75.50');
    await pulsar('Eliminar Galleta');
    expect(boton('Eliminar Agua')!.disabled).toBe(true);
    expect(boton('Quitar uno de Agua')!.disabled).toBe(false);
    await pulsar('Guardar cambios');
    expect(api.editar).toHaveBeenCalledWith('7', 2, [{ detalleId: '11', cantidad: 3 }]);
  });

  it('CP-F49 ante un 409 muestra el estado vigente y conserva lo editado', async () => {
    api.editar.mockReturnValue(fallo(409, 'VERSION_OBSOLETA', 'La información cambió.'));
    await abrir('/cuentas/3/comandas/7/modificar');
    await pulsar('Agregar uno de Agua');
    api.cuenta.mockReturnValue(
      of({ ...CUENTA, comandas: [{ ...COMANDA, version: 3, estado: 'EN_PREPARACION' }] }),
    );
    await pulsar('Guardar cambios');
    expect(el.textContent).toContain('La información cambió.');
    expect(el.textContent).toContain('cocina recibirá un aviso');
    expect(el.querySelector('[aria-label="3 de Agua"]')).not.toBeNull();
    api.editar.mockReturnValue(of(CUENTA));
    await pulsar('Guardar cambios');
    expect(api.editar.mock.lastCall![1]).toBe(3);
  });

  it('CP-F50 cancelar exige justificación y es solo del cajero', async () => {
    await abrir('/cuentas/3/comandas/7/cancelar');
    expect(TestBed.inject(Router).url).toBe('/inicio');

    perfil.set({ id: '2', nombre: 'C', rol: 'CAJERO' });
    api.cuenta.mockReturnValue(of({ ...CUENTA, comandas: [{ ...COMANDA, acciones: ['CANCELAR'] }] }));
    api.cancelar.mockReturnValue(of(COMANDA));
    await abrir('/cuentas/3/comandas/7/cancelar');
    const formulario = el.querySelector('form')!;
    formulario.dispatchEvent(new Event('submit'));
    await refrescar();
    expect(api.cancelar).not.toHaveBeenCalled();
    expect(el.textContent).toContain('Escribe la justificación');
    const texto = el.querySelector('textarea')!;
    texto.value = 'Se retiró';
    texto.dispatchEvent(new Event('input'));
    formulario.dispatchEvent(new Event('submit'));
    await refrescar();
    expect(api.cancelar).toHaveBeenCalledWith('7', 2, 'Se retiró', expect.any(String));
  });

  it('CP-F51 suma importes sin errores de redondeo', () => {
    expect(sumaDinero([])).toBe('0.00');
    expect(sumaDinero(['0.10', '0.20'])).toBe('0.30');
    expect(sumaDinero(['999999999999.99', '0.01'])).toBe('1000000000000.00');
  });
});
