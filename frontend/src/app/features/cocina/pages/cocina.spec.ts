import { HttpErrorResponse } from '@angular/common/http';
import { signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { Subject, of, throwError } from 'rxjs';
import { Sesion } from '../../../core/auth/sesion';
import { Bandeja } from '../../../core/notificaciones/bandeja';
import { Notificacion, NotificacionesApi } from '../../../core/notificaciones/notificaciones.api';
import { TiempoReal } from '../../../core/realtime/tiempo-real';
import { Notificaciones } from '../../notificaciones/pages/notificaciones';
import { CocinaApi, ComandaCocina } from '../data-access/cocina.api';
import { Cocina } from './cocina';

const ENVIADA: ComandaCocina = {
  id: '7',
  cuentaId: '3',
  mesa: 5,
  mesero: 'Juan',
  estado: 'ENVIADA',
  fechaHoraEnvio: '2026-11-25T18:00:00Z',
  version: 2,
  detalles: [{ id: '1', producto: 'Café', cantidad: 2, observaciones: 'Sin azúcar' }],
};
const PREPARACION: ComandaCocina = { ...ENVIADA, id: '8', estado: 'EN_PREPARACION', version: 1 };

const aviso = (id: string, tipo: Notificacion['tipo'], leida = false): Notificacion => ({
  id,
  tipo,
  mensaje: `Aviso ${id}`,
  fechaHora: '2026-11-25T18:00:00Z',
  leida,
  contexto: { mesa: 5, cuentaId: '3', comandaId: '7' },
});
const pagina = (...contenido: Notificacion[]) =>
  of({ contenido, page: 0, size: 20, totalElementos: contenido.length, totalPaginas: 1 });

describe('Cocina y avisos', () => {
  const cocina = { cola: vi.fn(), iniciar: vi.fn(), marcarLista: vi.fn() };
  const notificaciones = { listar: vi.fn(), leer: vi.fn(), descartar: vi.fn() };
  const conectado = signal(true);
  const perfil = signal({ id: '1', nombre: 'A', rol: 'COCINERO' });
  let eventos: Subject<null>;
  let el: HTMLElement;
  let fixture: ComponentFixture<unknown>;

  beforeEach(() => {
    vi.resetAllMocks();
    conectado.set(true);
    perfil.set({ id: '1', nombre: 'A', rol: 'COCINERO' });
    eventos = new Subject();
    cocina.cola.mockReturnValue(of([ENVIADA, PREPARACION]));
    notificaciones.listar.mockReturnValue(pagina());
    notificaciones.leer.mockReturnValue(of(undefined));
    notificaciones.descartar.mockReturnValue(of(undefined));
    TestBed.configureTestingModule({
      providers: [
        provideRouter([{ path: 'notificaciones', component: Notificaciones }]),
        { provide: CocinaApi, useValue: cocina },
        { provide: NotificacionesApi, useValue: notificaciones },
        { provide: Sesion, useValue: { perfil, activa: () => true } },
        { provide: TiempoReal, useValue: { cambios: () => eventos, conectado } },
      ],
    });
  });

  async function refrescar() {
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
  }

  async function abrir() {
    fixture = TestBed.createComponent(Cocina);
    el = fixture.nativeElement;
    await refrescar();
  }

  const boton = (texto: string) =>
    [...el.querySelectorAll('button')].find((b) => b.textContent?.includes(texto)) as HTMLButtonElement | undefined;

  it('CP-F52 la cola conserva el orden, muestra observaciones y filtra por estado', async () => {
    await abrir();
    const tarjetas = [...el.querySelectorAll('.cola > li')].map((t) => t.textContent ?? '');
    expect(tarjetas[0]).toContain('Comanda 7');
    expect(tarjetas[1]).toContain('Comanda 8');
    expect(tarjetas[0]).toContain('Sin azúcar');
    expect(el.textContent).not.toContain('$');
    boton('En preparación (1)')!.click();
    await refrescar();
    expect(el.querySelectorAll('.cola > li')).toHaveLength(1);
  });

  it('CP-F53 inicia preparación y marca lista con la versión mostrada', async () => {
    cocina.iniciar.mockReturnValue(of({ ...ENVIADA, estado: 'EN_PREPARACION', version: 3 }));
    cocina.marcarLista.mockReturnValue(of({ ...ENVIADA, estado: 'LISTA', version: 4 }));
    await abrir();
    boton('Iniciar preparación')!.click();
    await refrescar();
    expect(cocina.iniciar).toHaveBeenCalledWith('7', 2);
    boton('Marcar lista')!.click();
    await refrescar();
    expect(cocina.marcarLista).toHaveBeenCalledWith('7', 3);
    expect(el.textContent).toContain('Esperando al mesero');
  });

  it('CP-F54 ante un 409 muestra el motivo y actualiza la cola', async () => {
    cocina.iniciar.mockReturnValue(
      throwError(
        () => new HttpErrorResponse({ status: 409, error: { codigo: 'VERSION_OBSOLETA', detail: 'Cambió.' } }),
      ),
    );
    await abrir();
    cocina.cola.mockReturnValue(of([{ ...ENVIADA, version: 5 }]));
    boton('Iniciar preparación')!.click();
    await refrescar();
    expect(el.textContent).toContain('Cambió.');
    expect(el.querySelectorAll('.cola > li')).toHaveLength(1);
  });

  it('CP-F55 un evento actualiza la cola sin contar como actividad e indica si se perdió la conexión', async () => {
    await abrir();
    cocina.cola.mockReturnValue(of([]));
    eventos.next(null);
    await refrescar();
    expect(cocina.cola).toHaveBeenLastCalledWith(true);
    expect(el.textContent).toContain('No hay comandas.');
    conectado.set(false);
    await refrescar();
    expect(el.textContent).toContain('Sin conexión en vivo');
  });

  it('CP-F56 cocina ve los avisos sin leer y los marca', async () => {
    notificaciones.listar.mockReturnValue(pagina(aviso('1', 'MODIFICACION_COMANDA')));
    await abrir();
    expect(el.textContent).toContain('Aviso 1');
    notificaciones.listar.mockReturnValue(pagina());
    boton('Entendido')!.click();
    await refrescar();
    expect(notificaciones.leer).toHaveBeenCalledWith('1');
    expect(el.textContent).not.toContain('Aviso 1');
  });

  it('CP-F57 la bandeja cuenta los no leídos y destaca solo pedidos listos', async () => {
    notificaciones.listar.mockReturnValue(pagina(aviso('1', 'CANCELACION_COMANDA'), aviso('2', 'COMANDA_LISTA')));
    const bandeja = TestBed.inject(Bandeja);
    TestBed.tick();
    expect(notificaciones.listar).toHaveBeenCalledWith('false', 0, 20, true);
    expect(bandeja.noLeidas()).toBe(2);
    expect(bandeja.reciente()?.id).toBe('2');
    notificaciones.listar.mockReturnValue(pagina(aviso('1', 'CANCELACION_COMANDA')));
    eventos.next(null);
    expect(bandeja.noLeidas()).toBe(1);
    expect(bandeja.reciente()).toBeNull();
  });

  it('CP-F58 la pantalla de avisos filtra, enlaza la cuenta del mesero y marca lectura', async () => {
    perfil.set({ id: '2', nombre: 'M', rol: 'MESERO' });
    notificaciones.listar.mockReturnValue(pagina(aviso('1', 'COMANDA_LISTA'), aviso('2', 'COMANDA_LISTA', true)));
    const harness = await RouterTestingHarness.create();
    await harness.navigateByUrl('/notificaciones?leida=false');
    fixture = harness.fixture;
    el = fixture.nativeElement;
    await refrescar();
    expect(notificaciones.listar).toHaveBeenCalledWith('false', 0, 12, false);
    expect(el.querySelector('a[href="/cuentas/3"]')).not.toBeNull();
    const marcar = el.querySelectorAll<HTMLButtonElement>('[aria-label^="Marcar como leído"]');
    expect(marcar).toHaveLength(1);
    marcar[0].click();
    await refrescar();
    expect(notificaciones.leer).toHaveBeenCalledWith('1');
  });

  it('descarta un aviso sin eliminarlo desde la interfaz', async () => {
    notificaciones.listar.mockReturnValue(pagina(aviso('1', 'COMANDA_LISTA')));
    const harness = await RouterTestingHarness.create();
    await harness.navigateByUrl('/notificaciones');
    fixture = harness.fixture;
    el = fixture.nativeElement;
    await refrescar();
    el.querySelector<HTMLButtonElement>('[aria-label^="Descartar:"]')!.click();
    await refrescar();
    expect(notificaciones.descartar).toHaveBeenCalledWith('1');
  });

  it('CP-F64 el almacenista recibe el faltante destacado, con enlace a la existencia', async () => {
    perfil.set({ id: '3', nombre: 'P', rol: 'ALMACENISTA' });
    notificaciones.listar.mockReturnValue(
      pagina({
        ...aviso('4', 'ALERTA_INVENTARIO'),
        contexto: { mesa: 5, comandaId: '7', existenciaId: '9', faltante: '1040.5', unidadMedida: 'MILILITRO' },
      }),
    );
    const harness = await RouterTestingHarness.create();
    await harness.navigateByUrl('/notificaciones');
    fixture = harness.fixture;
    el = fixture.nativeElement;
    await refrescar();
    expect(TestBed.inject(Bandeja).reciente()?.id).toBe('4');
    expect(el.querySelector('a[href="/inventario/existencias/9"]')).not.toBeNull();
    expect(el.textContent).toContain('Faltan 1,040.5 ml');
  });

  it('CP-F65 un aviso nuevo recarga la pantalla de avisos sin contar como actividad', async () => {
    const harness = await RouterTestingHarness.create();
    await harness.navigateByUrl('/notificaciones');
    fixture = harness.fixture;
    el = fixture.nativeElement;
    await refrescar();
    notificaciones.listar.mockReturnValue(pagina(aviso('5', 'CANCELACION_COMANDA')));
    eventos.next(null);
    await refrescar();
    expect(el.textContent).toContain('Aviso 5');
    const posteriores = notificaciones.listar.mock.calls.slice(2);
    expect(posteriores.length).toBeGreaterThan(0);
    expect(posteriores.every((c) => c[3] === true)).toBe(true);
  });
});
