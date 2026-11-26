import { HttpErrorResponse } from '@angular/common/http';
import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter, withComponentInputBinding } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { Subject, of, throwError } from 'rxjs';
import { Rol } from '../../../core/auth/rol';
import { Sesion } from '../../../core/auth/sesion';
import { TiempoReal } from '../../../core/realtime/tiempo-real';
import { rutasCuentas } from '../../mesas-cuentas/cuentas.routes';
import { Comanda, Cuenta, ServicioApi } from '../../mesas-cuentas/data-access/servicio.api';
import { Venta, VentasApi } from '../data-access/ventas.api';
import { rutasVentas } from '../ventas.routes';

const COMANDA: Comanda = {
  id: '7',
  cuentaId: '3',
  estado: 'SERVIDA',
  fechaHoraEnvio: '2026-11-25T18:00:00Z',
  version: 3,
  subtotal: '55.50',
  detalles: [
    { id: '11', productoId: '1', producto: 'Agua', cantidad: 2, precioUnitario: '20.00', subtotal: '40.00', observaciones: '' },
    { id: '12', productoId: '2', producto: 'Galleta', cantidad: 1, precioUnitario: '15.50', subtotal: '15.50', observaciones: '' },
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
  total: '55.50',
  comandas: [COMANDA],
  acciones: ['ENVIAR_COMANDA', 'COBRAR'],
};
const VENTA: Venta = {
  id: '9',
  cuentaId: '3',
  mesa: 5,
  mesero: 'Juan Pérez',
  fechaHora: '2026-11-25T19:00:00Z',
  estado: 'COMPLETADA',
  total: '55.50',
  pago: { id: '9', metodo: 'TARJETA', monto: '55.50', referenciaExterna: 'A1', fechaHora: '2026-11-25T19:00:00Z', mesero: 'Juan Pérez' },
  comandas: [COMANDA],
  acciones: ['CANCELAR'],
};
const COBRADA = {
  venta: { id: '9', fechaHora: '2026-11-25T19:00:00Z', total: '55.50', estado: 'COMPLETADA' },
  pago: VENTA.pago,
  cuenta: { ...CUENTA, estado: 'CERRADA', acciones: [] },
};
const pagina = <T>(...contenido: T[]) => of({ contenido, page: 0, size: 10, totalElementos: contenido.length, totalPaginas: 1 });
const fallo = (status: number, codigo: string, detail: string, erroresCampo = {}) =>
  throwError(() => new HttpErrorResponse({ status, error: codigo ? { codigo, detail, erroresCampo } : null }));

describe('Cobro e historial de ventas', () => {
  const servicio = { cuenta: vi.fn() };
  const ventas = { cobrar: vi.fn(), ventas: vi.fn(), opciones: vi.fn(), venta: vi.fn(), cancelar: vi.fn() };
  const perfil = signal<{ id: string; nombre: string; rol: Rol }>({ id: '1', nombre: 'J', rol: 'MESERO' });
  let harness: RouterTestingHarness;
  let el: HTMLElement;

  beforeAll(
    () =>
      Promise.all([
        import('../../mesas-cuentas/pages/detalle-cuenta'),
        import('../../cobro/pages/cobrar-cuenta'),
        import('./ventas'),
        import('./detalle-venta'),
        import('./cancelar-venta'),
      ]),
    60_000,
  );

  beforeEach(async () => {
    vi.resetAllMocks();
    sessionStorage.clear();
    perfil.set({ id: '1', nombre: 'J', rol: 'MESERO' });
    servicio.cuenta.mockReturnValue(of(CUENTA));
    ventas.venta.mockReturnValue(of(VENTA));
    ventas.opciones.mockReturnValue(
      of({ meseros: [{ id: '4', nombre: 'Juan Pérez' }], mesas: [{ id: '1', nombre: 'Mesa 5' }] }),
    );
    ventas.ventas.mockReturnValue(
      pagina({ id: '9', fechaHora: VENTA.fechaHora, mesa: 5, mesero: 'Juan Pérez', metodo: 'TARJETA', total: '55.50', estado: 'COMPLETADA' }),
    );
    TestBed.configureTestingModule({
      providers: [
        provideRouter(
          [
            { path: 'cuentas/:id', children: rutasCuentas },
            { path: 'ventas', children: rutasVentas },
            { path: 'mesas', children: [] },
            { path: 'inicio', children: [] },
          ],
          withComponentInputBinding(),
        ),
        { provide: ServicioApi, useValue: servicio },
        { provide: VentasApi, useValue: ventas },
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

  async function pulsar(texto: string) {
    boton(texto)!.click();
    await refrescar();
  }

  async function escribir(id: string, valor: string) {
    const control = el.querySelector<HTMLInputElement | HTMLTextAreaElement>('#' + id)!;
    control.value = valor;
    control.dispatchEvent(new Event('input'));
    await refrescar();
  }

  async function elegir(metodo: string) {
    const nombre = { EFECTIVO: 'Efectivo', TARJETA: 'Tarjeta', TRANSFERENCIA: 'Transferencia' }[metodo]!;
    [...el.querySelectorAll('label.metodo')]
      .find((l) => l.textContent?.includes(nombre))!
      .querySelector<HTMLInputElement>('input')!
      .click();
    await refrescar();
  }

  async function enviar() {
    el.querySelector<HTMLButtonElement>('button[type=submit]')!.click();
    await refrescar();
  }

  it('CP-F66 la cuenta ofrece cobrar solo si la API lo permite y muestra el cobro de una cuenta cerrada', async () => {
    await abrir('/cuentas/3');
    expect(boton('Cobrar $55.50')?.getAttribute('href')).toBe('/cuentas/3/cobro');
    servicio.cuenta.mockReturnValue(
      of({ ...CUENTA, acciones: ['ENVIAR_COMANDA'], comandas: [{ ...COMANDA, estado: 'LISTA' }] }),
    );
    await abrir('/inicio');
    await abrir('/cuentas/3');
    expect(boton('Cobrar')).toBeUndefined();
    expect(el.textContent).toContain('deben estar servidas');
    servicio.cuenta.mockReturnValue(
      of({
        ...CUENTA,
        estado: 'CERRADA',
        acciones: [],
        cobro: { fechaHora: VENTA.fechaHora, metodo: 'TARJETA', total: '55.50', referenciaExterna: 'A1', estado: 'CANCELADA' },
      }),
    );
    await abrir('/inicio');
    await abrir('/cuentas/3');
    expect(el.textContent).toContain('Tarjeta');
    expect(el.textContent).toContain('A1');
    expect(el.textContent).toContain('Cancelada');
  });

  it('CP-F67 cobra en efectivo con la versión y el total consultados, sin referencia, y muestra el comprobante', async () => {
    ventas.cobrar.mockReturnValue(of(COBRADA));
    await abrir('/cuentas/3/cobro');
    expect(el.textContent).toContain('Recibe $55.50 en efectivo');
    expect(el.querySelector('#referencia')).toBeNull();
    await enviar();
    expect(ventas.cobrar).toHaveBeenCalledWith(
      '3',
      { versionCuenta: 4, metodo: 'EFECTIVO', referenciaExterna: '', totalEsperado: '55.50' },
      expect.any(String),
    );
    expect(el.textContent).toContain('Cobro registrado');
    expect(el.textContent).toContain('la mesa 5 está libre');
    expect(sessionStorage.length).toBe(0);
  });

  it('CP-F68 con tarjeta exige la referencia y ningún método acepta el número de una tarjeta', async () => {
    ventas.cobrar.mockReturnValue(of(COBRADA));
    await abrir('/cuentas/3/cobro');
    await elegir('TARJETA');
    expect(el.textContent).toContain('Cobra $55.50 en la terminal');
    await enviar();
    expect(el.textContent).toContain('Escribe la referencia de autorización.');
    await escribir('referencia', '4111 1111 1111 1111');
    expect(el.textContent).toContain('letras, números o guiones');
    await escribir('referencia', '4111-1111-1111-1111');
    expect(el.textContent).toContain('No captures el número de la tarjeta');
    await elegir('TRANSFERENCIA');
    await enviar();
    expect(el.textContent).toContain('No captures el número de la tarjeta');
    expect(ventas.cobrar).not.toHaveBeenCalled();
    await elegir('TARJETA');
    await escribir('referencia', ' 778899 ');
    await enviar();
    expect(ventas.cobrar.mock.lastCall![1]).toMatchObject({ metodo: 'TARJETA', referenciaExterna: '778899' });
  });

  it('CP-F69 tras un fallo de red conserva la clave, también al recargar, y no registra dos veces', async () => {
    ventas.cobrar.mockReturnValueOnce(fallo(0, '', ''));
    await abrir('/cuentas/3/cobro');
    await elegir('TARJETA');
    await escribir('referencia', 'A1');
    await enviar();
    expect(el.textContent).toContain('No hay conexión');
    const clave = ventas.cobrar.mock.calls[0][2];

    await abrir('/inicio');
    ventas.cobrar.mockReturnValue(of(COBRADA));
    await abrir('/cuentas/3/cobro');
    expect(el.textContent).toContain('No se confirmó el último intento');
    expect(el.querySelector<HTMLInputElement>('#referencia')!.value).toBe('A1');
    await enviar();
    expect(ventas.cobrar.mock.calls[1][2]).toBe(clave);
    expect(el.textContent).toContain('Cobro registrado');
    expect(sessionStorage.length).toBe(0);
  });

  it('CP-F70 si la cuenta cambió después del cargo, indica revertirlo con su referencia e importe hasta confirmarlo', async () => {
    ventas.cobrar.mockReturnValue(fallo(409, 'VERSION_OBSOLETA', 'La información cambió.'));
    await abrir('/cuentas/3/cobro');
    await elegir('TARJETA');
    await escribir('referencia', 'A1');
    servicio.cuenta.mockReturnValue(of({ ...CUENTA, version: 5, total: '75.50' }));
    await enviar();
    expect(el.textContent).toContain('el cobro no se registró');
    expect(el.textContent).toContain('referencia A1 por $55.50');
    expect(el.textContent).toContain('total nuevo de $75.50');
    expect(el.querySelector('form')).toBeNull();

    await abrir('/inicio');
    await abrir('/cuentas/3/cobro');
    expect(el.textContent).toContain('referencia A1 por $55.50');
    await pulsar('Ya lo revertí');
    expect(el.querySelector<HTMLInputElement>('#referencia')).toBeDefined();
    expect(el.textContent).toContain('Cobrar $75.50');
    expect(sessionStorage.length).toBe(0);
  });

  it('CP-F71 una referencia repetida o una cuenta ya cobrada muestran el motivo', async () => {
    ventas.cobrar.mockReturnValue(
      fallo(409, 'REFERENCIA_REPETIDA', 'Esa referencia ya se registró hoy en otro cobro.', {
        referenciaExterna: 'Esa referencia ya se registró hoy en otro cobro.',
      }),
    );
    await abrir('/cuentas/3/cobro');
    await elegir('TARJETA');
    await escribir('referencia', 'A1');
    await enviar();
    expect(el.querySelector('#referencia')!.getAttribute('aria-invalid')).toBe('true');
    expect(el.textContent).toContain('ya se registró hoy');

    servicio.cuenta.mockReturnValue(
      of({ ...CUENTA, estado: 'CERRADA', acciones: [], cobro: { fechaHora: '', metodo: 'EFECTIVO', total: '55.50', estado: 'COMPLETADA' } }),
    );
    await abrir('/inicio');
    await abrir('/cuentas/3/cobro');
    expect(el.textContent).toContain('ya se cobró por $55.50');
  });

  it('CP-F72 el historial filtra desde la URL y enlaza al detalle', async () => {
    perfil.set({ id: '2', nombre: 'C', rol: 'CAJERO' });
    await abrir('/ventas?estado=CANCELADA&metodo=TARJETA&meseroId=4&desde=2026-11-01');
    expect(ventas.ventas).toHaveBeenLastCalledWith({
      desde: '2026-11-01',
      hasta: '',
      estado: 'CANCELADA',
      metodo: 'TARJETA',
      meseroId: '4',
      mesaId: '',
      page: 0,
    });
    expect(el.querySelector<HTMLSelectElement>('#meseroId')!.value).toBe('4');
    expect(el.querySelector('#mesaId')!.textContent).toContain('Mesa 5');
    const fila = el.querySelector('tbody tr')!.textContent!;
    expect(fila).toContain('Venta 9');
    expect(fila).toContain('$55.50');
    expect(fila).toContain('Tarjeta');
    expect(boton('Ver venta 9')!.getAttribute('href')).toBe('/ventas/9');
  });

  it('CP-F73 el detalle muestra pago, comandas y cancelación; solo ofrece cancelar si la API lo permite', async () => {
    perfil.set({ id: '2', nombre: 'C', rol: 'CAJERO' });
    ventas.venta.mockReturnValue(
      of({
        ...VENTA,
        estado: 'CANCELADA',
        acciones: [],
        cancelacion: { administrador: 'Ada', fechaHora: VENTA.fechaHora, justificacion: 'Error', montoDevuelto: '55.50', referenciaReversion: 'R1' },
      }),
    );
    await abrir('/ventas/9');
    expect(el.textContent).toContain('Comanda 7');
    expect(el.textContent).toContain('A1');
    expect(el.textContent).toContain('Ada');
    expect(el.textContent).toContain('R1');
    expect(boton('Cancelar venta')).toBeUndefined();
    perfil.set({ id: '3', nombre: 'A', rol: 'ADMINISTRADOR' });
    ventas.venta.mockReturnValue(of(VENTA));
    await abrir('/inicio');
    await abrir('/ventas/9');
    expect(boton('Cancelar venta')!.getAttribute('href')).toBe('/ventas/9/cancelar');
  });

  it('CP-F74 cancelar una venta con tarjeta exige motivo, referencia y confirmación', async () => {
    perfil.set({ id: '3', nombre: 'A', rol: 'ADMINISTRADOR' });
    ventas.cancelar.mockReturnValue(of({ ...VENTA, estado: 'CANCELADA' }));
    await abrir('/ventas/9/cancelar');
    expect(el.textContent).toContain('Revierte $55.50 en la terminal');
    await enviar();
    expect(el.textContent).toContain('Escribe el motivo');
    expect(el.textContent).toContain('Escribe la referencia de la reversión.');
    expect(el.textContent).toContain('Confirma la devolución.');
    await escribir('justificacion', 'Cobro duplicado');
    await escribir('referencia', 'R-1');
    el.querySelector<HTMLInputElement>('input[type=checkbox]')!.click();
    await refrescar();
    await enviar();
    expect(ventas.cancelar).toHaveBeenCalledWith('9', 'Cobro duplicado', 'R-1', expect.any(String));
    expect(TestBed.inject(Router).url).toBe('/ventas/9');
  });

  it('CP-F75 en efectivo no pide referencia; si ya estaba cancelada muestra el motivo y actualiza', async () => {
    perfil.set({ id: '3', nombre: 'A', rol: 'ADMINISTRADOR' });
    ventas.venta.mockReturnValue(of({ ...VENTA, pago: { ...VENTA.pago, metodo: 'EFECTIVO', referenciaExterna: undefined } }));
    ventas.cancelar.mockReturnValue(fallo(409, 'VENTA_CANCELADA', 'La venta ya está cancelada.'));
    await abrir('/ventas/9/cancelar');
    expect(el.textContent).toContain('Entrega $55.50 al cliente desde caja');
    expect(el.querySelector('#referencia')).toBeNull();
    await escribir('justificacion', 'Devolución');
    el.querySelector<HTMLInputElement>('input[type=checkbox]')!.click();
    await refrescar();
    ventas.venta.mockReturnValue(of({ ...VENTA, estado: 'CANCELADA', acciones: [] }));
    await enviar();
    expect(ventas.cancelar).toHaveBeenCalledWith('9', 'Devolución', '', expect.any(String));
    expect(el.textContent).toContain('La venta ya está cancelada.');
    expect(ventas.venta).toHaveBeenCalledTimes(2);
  });
});
