import { HttpErrorResponse } from '@angular/common/http';
import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter, withComponentInputBinding } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { Subject, of, throwError } from 'rxjs';
import { Rol } from '../../../core/auth/rol';
import { Sesion } from '../../../core/auth/sesion';
import { TiempoReal } from '../../../core/realtime/tiempo-real';
import { rutasCuentas } from '../../mesas-cuentas/cuentas.routes';
import { Comanda, Cuenta, ServicioApi } from '../../mesas-cuentas/data-access/servicio.api';
import { VentasApi } from '../../ventas/data-access/ventas.api';
import { rutasCaja } from '../caja.routes';
import { CajaApi, PagoEfectivo } from '../data-access/caja.api';

const COMANDA: Comanda = {
  id: '7',
  cuentaId: '3',
  estado: 'SERVIDA',
  fechaHoraEnvio: '2026-11-25T18:00:00Z',
  version: 3,
  subtotal: '40.00',
  detalles: [
    { id: '11', productoId: '1', producto: 'Agua', cantidad: 2, precioUnitario: '20.00', subtotal: '40.00', observaciones: '' },
  ],
  acciones: [],
};
const CUENTA: Cuenta = {
  id: '3',
  mesaId: '1',
  mesa: 5,
  mesero: 'Juan Pérez',
  estado: 'ABIERTA',
  fechaHoraApertura: '2026-11-25T17:00:00Z',
  version: 4,
  total: '40.00',
  comandas: [COMANDA],
  acciones: ['ENTREGAR_FONDO'],
};
const PENDIENTE = { id: '20', monto: '200.00', estado: 'PENDIENTE', cajero: 'María', fechaHora: '2026-11-25T17:10:00Z' };
const pago = (id: string, mesero: string, monto: string, fondo?: string): PagoEfectivo => ({
  id,
  ventaId: 'V' + id,
  mesa: 5,
  meseroId: mesero,
  mesero: mesero === '2' ? 'Juan Pérez' : 'Ana Mesera',
  fechaHora: '2026-11-25T19:00:00Z',
  monto,
  estadoVenta: 'COMPLETADA',
  fondo,
});
const fallo = (status: number, codigo: string, detail: string) =>
  throwError(() => new HttpErrorResponse({ status, error: { codigo, detail } }));

describe('Caja y fondo de cambio', () => {
  const servicio = { cuenta: vi.fn() };
  const caja = {
    fondosDe: vi.fn(),
    entregarFondo: vi.fn(),
    anularFondo: vi.fn(),
    fondos: vi.fn(),
    pendientes: vi.fn(),
    recibir: vi.fn(),
  };
  const perfil = signal<{ id: string; nombre: string; rol: Rol }>({ id: '5', nombre: 'M', rol: 'CAJERO' });
  let harness: RouterTestingHarness;
  let el: HTMLElement;

  beforeAll(
    () =>
      Promise.all([
        import('../../mesas-cuentas/pages/detalle-cuenta'),
        import('../../cobro/pages/cobrar-cuenta'),
        import('./fondo-cuenta'),
        import('./entregas'),
        import('./fondos'),
      ]),
    60_000,
  );

  beforeEach(async () => {
    vi.resetAllMocks();
    sessionStorage.clear();
    perfil.set({ id: '5', nombre: 'M', rol: 'CAJERO' });
    servicio.cuenta.mockReturnValue(of(CUENTA));
    caja.fondosDe.mockReturnValue(of([]));
    caja.pendientes.mockReturnValue(
      of([pago('1', '4', '30.00'), pago('2', '2', '40.00', '200.00'), pago('3', '2', '15.50')]),
    );
    TestBed.configureTestingModule({
      providers: [
        provideRouter(
          [
            { path: 'cuentas/:id', children: rutasCuentas },
            { path: 'caja', children: rutasCaja },
            { path: 'mesas', children: [] },
            { path: 'inicio', children: [] },
          ],
          withComponentInputBinding(),
        ),
        { provide: ServicioApi, useValue: servicio },
        { provide: CajaApi, useValue: caja },
        { provide: VentasApi, useValue: { cobrar: vi.fn() } },
        { provide: Sesion, useValue: { perfil } },
        { provide: TiempoReal, useValue: { cambios: () => new Subject(), conectado: signal(true) } },
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

  async function escribir(id: string, valor: string) {
    const control = el.querySelector<HTMLInputElement | HTMLTextAreaElement>('#' + id)!;
    control.value = valor;
    control.dispatchEvent(new Event('input'));
    await refrescar();
  }

  async function enviar() {
    el.querySelector<HTMLButtonElement>('button[type=submit]')!.click();
    await refrescar();
  }

  it('CP-F76 la cuenta ofrece al cajero el fondo y muestra al mesero su fondo y por qué no puede cerrarla', async () => {
    await abrir('/cuentas/3');
    expect(boton('Entregar fondo de cambio')!.getAttribute('href')).toBe('/cuentas/3/fondo');
    perfil.set({ id: '2', nombre: 'J', rol: 'MESERO' });
    servicio.cuenta.mockReturnValue(
      of({
        ...CUENTA,
        comandas: [{ ...COMANDA, estado: 'CANCELADA' }],
        total: '0.00',
        fondo: PENDIENTE,
        acciones: ['ENVIAR_COMANDA'],
      }),
    );
    await abrir('/inicio');
    await abrir('/cuentas/3');
    expect(boton('fondo de cambio')).toBeUndefined();
    expect(el.textContent).toContain('$200.00 · Pendiente');
    expect(el.textContent).toContain('regresa el fondo de cambio a caja');
  });

  it('CP-F77 entregar un fondo valida el importe y lo envía normalizado', async () => {
    caja.entregarFondo.mockReturnValue(of({}));
    await abrir('/cuentas/3/fondo');
    expect(el.textContent).toContain('Entrega el efectivo a Juan Pérez');
    for (const monto of ['', '0', 'abc', '1.005']) {
      await escribir('monto', monto);
      await enviar();
      expect(el.textContent).toContain('mayor que 0');
    }
    expect(caja.entregarFondo).not.toHaveBeenCalled();
    await escribir('monto', ' 150,5 ');
    await enviar();
    expect(caja.entregarFondo).toHaveBeenCalledWith('3', '150.5', expect.any(String));
    expect(servicio.cuenta).toHaveBeenCalledTimes(2);
  });

  it('CP-F78 anular el fondo pide el motivo y muestra el historial; un 409 muestra el motivo y actualiza', async () => {
    servicio.cuenta.mockReturnValue(of({ ...CUENTA, fondo: PENDIENTE, acciones: ['ANULAR_FONDO'] }));
    caja.fondosDe.mockReturnValue(
      of([
        { id: '20', cuentaId: '3', mesa: 5, mesero: 'Juan Pérez', cajero: 'María', monto: '200.00', fechaHora: PENDIENTE.fechaHora, estado: 'PENDIENTE' },
        { id: '19', cuentaId: '3', mesa: 5, mesero: 'Juan Pérez', cajero: 'María', monto: '50.00', fechaHora: PENDIENTE.fechaHora, estado: 'ANULADO', anulacion: { cajero: 'María', fechaHora: PENDIENTE.fechaHora, justificacion: 'Lo regresó' } },
      ]),
    );
    caja.anularFondo.mockReturnValue(fallo(409, 'SIN_FONDO_PENDIENTE', 'La cuenta no tiene un fondo de cambio pendiente.'));
    await abrir('/cuentas/3/fondo');
    expect(el.textContent).toContain('Recibe de Juan Pérez los $200.00 completos');
    expect(el.textContent).toContain('Lo regresó');
    await enviar();
    expect(el.textContent).toContain('Escribe el motivo');
    await escribir('justificacion', 'Pagó exacto');
    await enviar();
    expect(caja.anularFondo).toHaveBeenCalledWith('3', 'Pagó exacto', expect.any(String));
    expect(el.textContent).toContain('no tiene un fondo de cambio pendiente');
    expect(servicio.cuenta).toHaveBeenCalledTimes(2);
  });

  it('CP-F79 con un fondo pendiente el cobro solo admite efectivo', async () => {
    perfil.set({ id: '2', nombre: 'J', rol: 'MESERO' });
    servicio.cuenta.mockReturnValue(of({ ...CUENTA, fondo: PENDIENTE, acciones: ['ENVIAR_COMANDA', 'COBRAR'] }));
    await abrir('/cuentas/3/cobro');
    expect(el.textContent).toContain('fondo de cambio de $200.00: cóbrala en efectivo');
    const radios = [...el.querySelectorAll<HTMLInputElement>('input[type=radio]')];
    expect(radios.map((r) => r.disabled)).toEqual([false, true, true]);
    expect(radios[0].checked).toBe(true);
  });

  it('CP-F80 la recepción agrupa por mesero, suma pagos y fondo, y exige que coincidan con lo contado', async () => {
    caja.recibir.mockReturnValue(
      of({ id: '8', fechaHora: '', mesero: 'Juan Pérez', cajero: 'M', monto: '55.50', montoFondo: '200.00', pagos: [{}, {}] }),
    );
    await abrir('/caja/entregas');
    const opciones = [...el.querySelectorAll('#mesero option')].map((o) => o.textContent);
    expect(opciones).toEqual(['Ana Mesera · 1 pago(s)', 'Juan Pérez · 2 pago(s)']);
    const select = el.querySelector<HTMLSelectElement>('#mesero')!;
    select.value = '2';
    select.dispatchEvent(new Event('change'));
    await refrescar();
    expect(el.textContent).toContain('$55.50');
    expect(el.textContent).toContain('Fondo de cambio:');
    await escribir('montoRecibido', '55');
    await escribir('fondoRecibido', '200');
    await enviar();
    expect(el.textContent).toContain('deben ser $55.50');
    expect(caja.recibir).not.toHaveBeenCalled();
    await escribir('montoRecibido', '55,50');
    await enviar();
    expect(caja.recibir).toHaveBeenCalledWith(
      { meseroId: '2', pagoIds: ['2', '3'], montoRecibido: '55.50', fondoRecibido: '200' },
      expect.any(String),
    );
    expect(el.textContent).toContain('Entrega 8 registrada: $55.50 de 2 pago(s) de Juan Pérez');
    expect(caja.pendientes).toHaveBeenCalledTimes(2);
  });

  it('CP-F81 sin fondo no lo pide; quitar un pago recalcula y un pago ya entregado muestra el motivo y actualiza', async () => {
    caja.recibir.mockReturnValue(fallo(409, 'PAGO_ENTREGADO', 'Un pago ya se entregó a caja. Actualiza la lista.'));
    await abrir('/caja/entregas');
    expect(el.querySelector('#fondoRecibido')).toBeNull();
    const select = el.querySelector<HTMLSelectElement>('#mesero')!;
    select.value = '2';
    select.dispatchEvent(new Event('change'));
    await refrescar();
    el.querySelectorAll<HTMLInputElement>('.pagos input[type=checkbox]')[0].click();
    await refrescar();
    expect(el.querySelector('#fondoRecibido')).toBeNull();
    expect(el.textContent).toContain('$15.50');
    await escribir('montoRecibido', '15.50');
    await enviar();
    expect(caja.recibir.mock.lastCall![0]).toEqual({
      meseroId: '2',
      pagoIds: ['3'],
      montoRecibido: '15.50',
      fondoRecibido: '0',
    });
    expect(el.textContent).toContain('Un pago ya se entregó a caja');
    expect(caja.pendientes).toHaveBeenCalledTimes(2);
  });

  it('CP-F82 los fondos se filtran por estado desde la URL y muestran su seguimiento', async () => {
    caja.fondos.mockReturnValue(
      of({
        contenido: [
          { id: '20', cuentaId: '3', mesa: 5, mesero: 'Juan Pérez', cajero: 'María', monto: '200.00', fechaHora: PENDIENTE.fechaHora, estado: 'LIQUIDADO', ventaId: '9', entregaId: '8' },
        ],
        page: 0,
        size: 10,
        totalElementos: 1,
        totalPaginas: 1,
      }),
    );
    await abrir('/caja/fondos?estado=LIQUIDADO');
    expect(caja.fondos).toHaveBeenLastCalledWith('LIQUIDADO', 0);
    const fila = el.querySelector('tbody tr')!.textContent!;
    expect(fila).toContain('Regresado a caja');
    expect(fila).toContain('Entrega 8');
    expect(el.querySelector('tbody a')!.getAttribute('href')).toBe('/cuentas/3');
  });
});
