import { HttpErrorResponse } from '@angular/common/http';
import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter, withComponentInputBinding } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { of, throwError } from 'rxjs';
import { Rol } from '../../../core/auth/rol';
import { Sesion } from '../../../core/auth/sesion';
import { cambio, cantidad, fechaHora } from '../../../shared/formato';
import { Existencia, Historial, InventarioApi, Solicitud } from '../data-access/inventario.api';
import { rutasInventario } from '../inventario.routes';

const CAFE: Existencia = {
  id: '5',
  articuloId: '1',
  nombre: 'Café',
  tipo: 'INSUMO',
  unidadMedida: 'GRAMO',
  activo: true,
  cantidadActual: '1200.5',
  nivelMinimo: '2000',
  nivelBajo: true,
  fechaActualizacion: '2026-11-25T18:00:00Z',
  version: 3,
};
const RECHAZADO: Historial = {
  id: '9',
  tipo: 'AJUSTE_RECHAZADO',
  existenciaId: '5',
  articulo: 'Café',
  unidadMedida: 'GRAMO',
  cambio: '-5',
  saldoAnterior: '20',
  fechaHora: '2026-11-25T18:00:00Z',
  empleado: 'Rodrigo',
  solicitudId: '9',
};
const SOLICITUD: Solicitud = {
  id: '9',
  existenciaId: '5',
  articulo: 'Café',
  unidadMedida: 'GRAMO',
  cantidadAnterior: '1200.5',
  cantidadSolicitada: '1000',
  cantidadActual: '1200.5',
  justificacion: 'Conteo físico',
  estado: 'PENDIENTE',
  almacenista: 'Pedro Torres',
  fechaHoraSolicitud: '2026-11-25T18:00:00Z',
  existenciaCambiada: false,
};

const pagina = (...contenido: unknown[]) =>
  of({ contenido, page: 0, size: 10, totalElementos: contenido.length, totalPaginas: 1 });
const fallo = (status: number, error: object) => throwError(() => new HttpErrorResponse({ status, error }));

describe('Inventario', () => {
  const api = {
    existencias: vi.fn(),
    existencia: vi.fn(),
    movimientos: vi.fn(),
    movimientosDe: vi.fn(),
    registrarMovimiento: vi.fn(),
    solicitarAjuste: vi.fn(),
    ajustes: vi.fn(),
    ajuste: vi.fn(),
    resolver: vi.fn(),
  };
  const perfil = signal<{ id: string; nombre: string; rol: Rol }>({ id: '1', nombre: 'P', rol: 'ALMACENISTA' });
  let harness: RouterTestingHarness;
  let el: HTMLElement;

  // La carga diferida de las pantallas no debe contar en el tiempo de cada caso.
  beforeAll(() => Promise.all([import('./existencias'), import('./movimientos'), import('./detalle-existencia'), import('./ajustes'), import('./detalle-ajuste')]), 60_000);

  beforeEach(async () => {
    vi.resetAllMocks();
    perfil.set({ id: '1', nombre: 'P', rol: 'ALMACENISTA' });
    api.existencias.mockReturnValue(pagina(CAFE));
    api.existencia.mockReturnValue(of(CAFE));
    api.movimientos.mockReturnValue(pagina(RECHAZADO));
    api.movimientosDe.mockReturnValue(pagina());
    api.ajuste.mockReturnValue(of(SOLICITUD));
    api.ajustes.mockReturnValue(pagina(SOLICITUD));
    TestBed.configureTestingModule({
      providers: [
        provideRouter([{ path: 'inventario', children: rutasInventario }], withComponentInputBinding()),
        { provide: InventarioApi, useValue: api },
        { provide: Sesion, useValue: { perfil } },
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
    const campo = el.querySelector<HTMLInputElement | HTMLTextAreaElement>(`#${id}`)!;
    campo.value = valor;
    campo.dispatchEvent(new Event('input'));
    await refrescar();
  }

  async function enviar(titulo: string) {
    el.querySelector(`form[aria-labelledby="${titulo}"]`)!.dispatchEvent(new Event('submit'));
    await refrescar();
  }

  const boton = (texto: string) =>
    [...el.querySelectorAll('button')].find((b) => b.textContent?.includes(texto)) as HTMLButtonElement | undefined;

  it('CP-F24 muestra cantidades, unidad y nivel bajo sin perder precisión', async () => {
    await abrir('/inventario?nivelBajo=true');
    expect(api.existencias).toHaveBeenCalledWith({ q: '', tipo: '', nivelBajo: 'true', page: 0 });
    expect(el.textContent).toContain('1,200.5 g');
    expect(el.textContent).toContain('Bajo');
    expect(cantidad('999999999999.123456')).toBe('999,999,999,999.123456');
    expect(cambio('-3')).toBe('-3');
    expect(cambio('50')).toBe('+50');
    expect(fechaHora('2026-03-11T05:59:00Z')).toContain('10/03/2026');
  });

  it('CP-F25 el historial distingue un ajuste rechazado que no cambió el saldo', async () => {
    await abrir('/inventario/movimientos?tipo=AJUSTE_RECHAZADO');
    expect(el.textContent).toContain('Ajuste rechazado');
    expect(el.textContent).toContain('Sin cambio');
    expect(el.querySelector('s')!.textContent).toContain('-5 g');
  });

  it('CP-F26 el administrador consulta el detalle sin registrar movimientos ni solicitar ajustes', async () => {
    perfil.set({ id: '2', nombre: 'A', rol: 'ADMINISTRADOR' });
    await abrir('/inventario/existencias/5');
    expect(el.textContent).toContain('Información del artículo');
    expect(el.querySelector('form')).toBeNull();
  });

  it('CP-F27 un fallo de red reutiliza la misma clave; tras el éxito usa otra', async () => {
    api.registrarMovimiento.mockReturnValueOnce(fallo(0, {}));
    api.registrarMovimiento.mockReturnValue(of({ movimiento: RECHAZADO, existencia: CAFE }));
    await abrir('/inventario/existencias/5');
    await escribir('cantidad', '4,5');
    await enviar('titulo-movimiento');
    expect(el.textContent).toContain('No hay conexión');
    await enviar('titulo-movimiento');
    await escribir('cantidad', '4,5');
    await enviar('titulo-movimiento');
    const llamadas = api.registrarMovimiento.mock.calls;
    expect(llamadas[0][1]).toEqual({ tipo: 'ENTRADA_MANUAL', cantidad: '4.5' });
    expect(llamadas[1][2]).toBe(llamadas[0][2]);
    expect(llamadas[2][2]).not.toBe(llamadas[0][2]);
  });

  it('CP-F28 una salida excedida muestra el motivo, conserva la cantidad y actualiza el saldo', async () => {
    api.registrarMovimiento.mockReturnValue(
      fallo(409, {
        codigo: 'EXISTENCIA_INSUFICIENTE',
        detail: 'x',
        erroresCampo: { cantidad: 'La cantidad supera la existencia disponible.' },
      }),
    );
    await abrir('/inventario/existencias/5');
    await escribir('cantidad', '9999');
    await enviar('titulo-movimiento');
    expect(el.textContent).toContain('supera la existencia');
    expect(el.querySelector<HTMLInputElement>('#cantidad')!.value).toBe('9999');
    expect(api.existencia).toHaveBeenCalledTimes(2);
  });

  it('CP-F29 la solicitud de ajuste exige justificación y lleva a su detalle', async () => {
    api.solicitarAjuste.mockReturnValue(of(SOLICITUD));
    await abrir('/inventario/existencias/5');
    await escribir('cantidadSolicitada', '1000');
    await enviar('titulo-ajuste');
    expect(api.solicitarAjuste).not.toHaveBeenCalled();
    expect(el.textContent).toContain('Escribe la justificación');
    await escribir('justificacion', 'Conteo físico');
    await enviar('titulo-ajuste');
    expect(api.solicitarAjuste).toHaveBeenCalledWith(
      { existenciaId: '5', cantidadSolicitada: '1000', justificacion: 'Conteo físico' },
      expect.any(String),
    );
    expect(TestBed.inject(Router).url).toBe('/inventario/ajustes/9');
  });

  it('CP-F30 solo el administrador puede resolver', async () => {
    await abrir('/inventario/ajustes/9');
    expect(el.textContent).toContain('pendiente de autorización');
    expect(boton('Autorizar')).toBeUndefined();
  });

  it('CP-F31 autorizar muestra el resultado', async () => {
    perfil.set({ id: '2', nombre: 'A', rol: 'ADMINISTRADOR' });
    api.resolver.mockReturnValue(
      of({
        ...SOLICITUD,
        estado: 'AUTORIZADA',
        administrador: 'Rodrigo',
        fechaHoraResolucion: '2026-11-25T19:00:00Z',
      }),
    );
    await abrir('/inventario/ajustes/9');
    boton('Autorizar')!.click();
    await refrescar();
    expect(api.resolver).toHaveBeenCalledWith('9', 'AUTORIZADA', expect.any(String));
    expect(el.textContent).toContain('Autorizada por Rodrigo');
    expect(boton('Autorizar')).toBeUndefined();
  });

  it('CP-F32 si la existencia cambió, solo se puede rechazar', async () => {
    perfil.set({ id: '2', nombre: 'A', rol: 'ADMINISTRADOR' });
    api.resolver.mockReturnValue(fallo(409, { codigo: 'EXISTENCIA_CAMBIADA', detail: 'La existencia cambió.' }));
    await abrir('/inventario/ajustes/9');
    api.ajuste.mockReturnValue(of({ ...SOLICITUD, existenciaCambiada: true }));
    boton('Autorizar')!.click();
    await refrescar();
    expect(el.textContent).toContain('La existencia cambió');
    expect(boton('Autorizar')!.disabled).toBe(true);
    expect(boton('Rechazar')!.disabled).toBe(false);
  });

  it('CP-F33 lista solicitudes por estado', async () => {
    await abrir('/inventario/ajustes?estado=PENDIENTE');
    expect(api.ajustes).toHaveBeenCalledWith({ estado: 'PENDIENTE', page: 0 });
    expect(el.textContent).toContain('Pendiente');
  });
});
