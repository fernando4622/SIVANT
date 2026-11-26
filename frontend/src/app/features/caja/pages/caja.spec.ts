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
import { Cuenta, ServicioApi } from '../../mesas-cuentas/data-access/servicio.api';
import { VentasApi } from '../../ventas/data-access/ventas.api';
import { rutasCaja } from '../caja.routes';
import { CajaApi, PagoEfectivo } from '../data-access/caja.api';

const CUENTA: Cuenta = {
  id: '3',
  mesaId: '1',
  mesa: 5,
  mesero: 'Juan Pérez',
  estado: 'ABIERTA',
  fechaHoraApertura: '2026-11-25T17:00:00Z',
  version: 4,
  total: '480.00',
  comandas: [
    {
      id: '7',
      cuentaId: '3',
      estado: 'SERVIDA',
      fechaHoraEnvio: '2026-11-25T18:00:00Z',
      version: 3,
      subtotal: '480.00',
      detalles: [
        { id: '11', productoId: '1', producto: 'Hamburguesa', cantidad: 4, precioUnitario: '120.00', subtotal: '480.00', observaciones: '' },
      ],
      acciones: [],
    },
  ],
  acciones: ['ENVIAR_COMANDA', 'COBRAR'],
};
const pago = (id: string, mesero: string, monto: string): PagoEfectivo => ({
  id,
  ventaId: 'V' + id,
  mesa: 5,
  meseroId: mesero,
  mesero: mesero === '2' ? 'Juan Pérez' : 'Ana Mesera',
  fechaHora: '2026-11-25T19:00:00Z',
  monto,
  estadoVenta: 'COMPLETADA',
});
const fallo = (status: number, codigo: string, detail: string) =>
  throwError(() => new HttpErrorResponse({ status, error: { codigo, detail } }));

describe('Caja: recibir el efectivo y dar el cambio', () => {
  const servicio = { cuenta: vi.fn() };
  const caja = { pendientes: vi.fn(), recibir: vi.fn() };
  const perfil = signal<{ id: string; nombre: string; rol: Rol }>({ id: '5', nombre: 'M', rol: 'CAJERO' });
  let harness: RouterTestingHarness;
  let el: HTMLElement;

  beforeAll(
    () =>
      Promise.all([
        import('../../mesas-cuentas/pages/detalle-cuenta'),
        import('../../cobro/pages/cobrar-cuenta'),
        import('./entregas'),
      ]),
    60_000,
  );

  beforeEach(async () => {
    vi.resetAllMocks();
    sessionStorage.clear();
    perfil.set({ id: '5', nombre: 'M', rol: 'CAJERO' });
    servicio.cuenta.mockReturnValue(of(CUENTA));
    caja.pendientes.mockReturnValue(of([pago('3', '2', '480.00'), pago('2', '2', '15.50'), pago('1', '4', '30.00')]));
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

  async function escribir(id: string, valor: string) {
    const control = el.querySelector<HTMLInputElement>('#' + id)!;
    control.value = valor;
    control.dispatchEvent(new Event('input'));
    await refrescar();
  }

  async function registrar() {
    [...el.querySelectorAll<HTMLButtonElement>('button')].find((b) => b.textContent?.includes('Registrar'))!.click();
    await refrescar();
  }

  it('CP-F76 la cuenta ya no ofrece fondo de cambio y el cobro en efectivo indica llevar el dinero a caja', async () => {
    await abrir('/cuentas/3');
    expect(el.textContent).not.toContain('fondo de cambio');
    perfil.set({ id: '2', nombre: 'J', rol: 'MESERO' });
    await abrir('/inicio');
    await abrir('/cuentas/3/cobro');
    const radios = [...el.querySelectorAll<HTMLInputElement>('input[type=radio]')];
    expect(radios.some((r) => r.disabled)).toBe(false);
    expect(el.textContent).toContain('lleva el efectivo a caja');
  });

  it('CP-F77 agrupa por mesero y propone el cobro más reciente con lo recibido igual al importe', async () => {
    await abrir('/caja/entregas');
    const opciones = [...el.querySelectorAll('#mesero option')].map((o) => o.textContent?.trim());
    expect(opciones).toEqual(['Juan Pérez · 2 pago(s)', 'Ana Mesera · 1 pago(s)']);
    expect(el.querySelector<HTMLInputElement>('#recibido-3')!.value).toBe('480.00');
    expect(el.querySelector('#recibido-2')).toBeNull();
    expect(el.querySelector('.total')!.textContent).toContain('$0.00');
  });

  it('CP-F78 con 500 recibidos calcula 20 de cambio; si no cubre el pago lo indica sin llamar a la API', async () => {
    caja.recibir.mockReturnValue(
      of({ id: '8', fechaHora: '', mesero: 'Juan Pérez', cajero: 'M', monto: '480.00', cambio: '20.00', pagos: [{}] }),
    );
    await abrir('/caja/entregas');
    await escribir('recibido-3', '450');
    await registrar();
    expect(el.textContent).toContain('No cubre el pago de $480.00.');
    expect(caja.recibir).not.toHaveBeenCalled();
    await escribir('recibido-3', '500');
    expect(el.querySelector('.total')!.textContent).toContain('$20.00');
    await registrar();
    expect(caja.recibir).toHaveBeenCalledWith(
      { meseroId: '2', pagos: [{ pagoId: '3', montoRecibido: '500' }] },
      expect.any(String),
    );
    expect(el.querySelector('.comprobante')?.textContent).toContain('Recepción registrada');
    expect(el.querySelector('.comprobante')?.textContent).toContain('Comprobante 8');
    expect(el.querySelector('.comprobante')?.textContent).toContain('Juan Pérez');
    expect(el.querySelector('.comprobante__cambio')?.textContent).toContain('$20.00');
    expect(caja.pendientes).toHaveBeenCalledTimes(2);
  });

  it('CP-F79 al elegir otro pago suma importes y cambios; al quitarlo deja de contarlo', async () => {
    await abrir('/caja/entregas');
    el.querySelectorAll<HTMLInputElement>('.pagos input[type=checkbox]')[1].click();
    await refrescar();
    await escribir('recibido-2', '20');
    expect(el.textContent).toContain('$495.50');
    expect(el.querySelector('.total')!.textContent).toContain('$4.50');
    el.querySelectorAll<HTMLInputElement>('.pagos input[type=checkbox]')[0].click();
    await refrescar();
    expect(el.textContent).toContain('$15.50');
    expect(el.querySelector('#recibido-3')).toBeNull();
  });

  it('CP-F80 si el pago ya se recibió en otra caja muestra el motivo y actualiza la lista', async () => {
    caja.recibir.mockReturnValue(fallo(409, 'PAGO_ENTREGADO', 'Un pago ya se recibió en caja. Actualiza la lista.'));
    await abrir('/caja/entregas');
    await registrar();
    expect(el.textContent).toContain('Un pago ya se recibió en caja');
    expect(caja.pendientes).toHaveBeenCalledTimes(2);
  });
});
