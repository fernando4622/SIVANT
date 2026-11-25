import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { conRol, conSesion } from './guardas';
import { Rol } from './rol';
import { Sesion } from './sesion';
import { sesionInterceptor } from './sesion.interceptor';

describe('Sesión y guardas', () => {
  let sesion: Sesion;
  let http: HttpTestingController;
  let router: Router;

  beforeEach(() => {
    vi.useFakeTimers();
    TestBed.configureTestingModule({
      providers: [
        provideRouter([{ path: '**', children: [] }]),
        provideHttpClient(withInterceptors([sesionInterceptor])),
        provideHttpClientTesting(),
      ],
    });
    sesion = TestBed.inject(Sesion);
    http = TestBed.inject(HttpTestingController);
    router = TestBed.inject(Router);
  });

  afterEach(() => {
    http.verify();
    vi.useRealTimers();
  });

  function entrar(rol: Rol) {
    sesion.iniciar('a@sivant.test', 'clave').subscribe();
    http.expectOne('/api/v1/auth/csrf').flush({});
    http.expectOne('/api/v1/auth/login').flush({ id: '1', nombre: 'Ana', rol });
    http.expectOne('/api/v1/auth/csrf').flush({});
  }

  function guarda(g: typeof conSesion, url = '/empleados') {
    return TestBed.runInInjectionContext(() => g({} as never, { url } as never));
  }

  it('CP-F01 renueva CSRF antes y después del login y guarda el perfil', () => {
    entrar('MESERO');
    expect(sesion.perfil()).toEqual({ id: '1', nombre: 'Ana', rol: 'MESERO' });
  });

  it('CP-F02 sin sesión la guarda lleva al login y recuerda la ruta', () => {
    expect(String(guarda(conSesion))).toBe('/login?volver=%2Fempleados');
  });

  it.each<[Rol, boolean]>([
    ['ADMINISTRADOR', true],
    ['MESERO', false],
    ['CAJERO', false],
    ['COCINERO', false],
    ['ALMACENISTA', false],
  ])('CP-F03 %s: acceso a empleados = %s', (rol, permitido) => {
    entrar(rol);
    const r = guarda(conRol('ADMINISTRADOR'));
    expect(permitido ? r : String(r)).toBe(permitido ? true : '/inicio');
  });

  it('CP-F04 un 401 de la API cierra la sesión local y lleva al login con el motivo', () => {
    const navegar = vi.spyOn(router, 'navigate').mockResolvedValue(true);
    entrar('ADMINISTRADOR');
    TestBed.inject(HttpClient).get('/api/v1/empleados').subscribe({ error: () => undefined });
    http
      .expectOne('/api/v1/empleados')
      .flush({ codigo: 'SESION_INVALIDADA', detail: 'x' }, { status: 401, statusText: 'Unauthorized' });
    expect(sesion.activa()).toBe(false);
    expect(navegar).toHaveBeenCalledWith(['/login'], { queryParams: { motivo: 'SESION_INVALIDADA', volver: '/' } });
  });

  it('CP-F05 un 403 conserva la sesión', () => {
    entrar('MESERO');
    TestBed.inject(HttpClient).get('/api/v1/empleados').subscribe({ error: () => undefined });
    http.expectOne('/api/v1/empleados').flush({ codigo: 'ACCESO_DENEGADO' }, { status: 403, statusText: 'Forbidden' });
    expect(sesion.activa()).toBe(true);
  });

  it('CP-F06 vigila la sesión con solicitudes que no cuentan como actividad', () => {
    entrar('CAJERO');
    vi.advanceTimersByTime(60_000);
    const solicitud = http.expectOne('/api/v1/auth/me');
    expect(solicitud.request.headers.get('X-Solicitud-Pasiva')).toBe('true');
    solicitud.flush({ id: '1', nombre: 'Ana María', rol: 'CAJERO' });
    expect(sesion.perfil()?.nombre).toBe('Ana María');
  });

  it('CP-F07 logout olvida el perfil y deja de vigilar; si falla, conserva la sesión', () => {
    entrar('CAJERO');
    sesion.cerrar().subscribe({ error: () => undefined });
    http.expectOne('/api/v1/auth/logout').error(new ProgressEvent('error'));
    expect(sesion.activa()).toBe(true);

    sesion.cerrar().subscribe();
    http.expectOne('/api/v1/auth/logout').flush(null, { status: 204, statusText: 'No Content' });
    http.expectOne('/api/v1/auth/csrf').flush({});
    expect(sesion.activa()).toBe(false);
    vi.advanceTimersByTime(120_000);
  });
});
