import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';
import { Login } from './login';
import { Sesion } from './sesion';

describe('Inicio de sesión', () => {
  const iniciar = vi.fn();

  beforeEach(() => {
    iniciar.mockReset().mockImplementation(() => of({ id: '1', nombre: 'Ana', rol: 'MESERO' }));
    localStorage.clear();
  });

  function crear(volver?: string) {
    TestBed.configureTestingModule({ providers: [provideRouter([]), { provide: Sesion, useValue: { iniciar } }] });
    const fixture = TestBed.createComponent(Login);
    fixture.componentRef.setInput('volver', volver);
    fixture.detectChanges();
    const el: HTMLElement = fixture.nativeElement;
    const escribir = (id: string, valor: string) => {
      const campo = el.querySelector<HTMLInputElement>(`#${id}`)!;
      campo.value = valor;
      campo.dispatchEvent(new Event('input'));
    };
    const enviar = () => {
      el.querySelector('form')!.dispatchEvent(new Event('submit'));
      fixture.detectChanges();
    };
    return { el, fixture, escribir, enviar, navegar: vi.spyOn(TestBed.inject(Router), 'navigateByUrl').mockResolvedValue(true) };
  }

  it('CP-F08 no envía con campos vacíos y muestra el error de cada uno', () => {
    const { el, enviar } = crear();
    enviar();
    expect(iniciar).not.toHaveBeenCalled();
    expect(el.textContent).toContain('Escribe tu correo.');
    expect(el.textContent).toContain('Escribe tu contraseña.');
  });

  it.each([
    ['/empleados?page=1', '/empleados?page=1'],
    ['//sitio.externo', '/inicio'],
    ['/\\sitio.externo', '/inicio'],
    ['https://sitio.externo', '/inicio'],
    [undefined, '/inicio'],
  ])('CP-F09 tras entrar, "volver=%s" navega a %s', (volver, destino) => {
    const { escribir, enviar, navegar } = crear(volver);
    escribir('correo', 'a@sivant.test');
    escribir('contrasena', 'clave');
    enviar();
    expect(navegar).toHaveBeenCalledWith(destino);
  });

  it('CP-F34 «Recordarme» guarda solo el correo y lo propone después', () => {
    const { el, escribir, enviar } = crear();
    expect(el.querySelector<HTMLInputElement>('input[type=checkbox]')!.checked).toBe(false);
    escribir('correo', ' ana@sivant.test ');
    escribir('contrasena', 'clave-secreta');
    el.querySelector<HTMLInputElement>('input[type=checkbox]')!.click();
    enviar();
    expect(localStorage.getItem('sivant.correo')).toBe('ana@sivant.test');
    expect(JSON.stringify({ ...localStorage })).not.toContain('clave-secreta');

    TestBed.resetTestingModule();
    const otra = crear();
    expect(otra.el.querySelector<HTMLInputElement>('#correo')!.value).toBe('ana@sivant.test');
    expect(otra.el.querySelector<HTMLInputElement>('#contrasena')!.value).toBe('');
  });

  it('CP-F35 sin «Recordarme» se olvida el correo; un login fallido no lo guarda', () => {
    localStorage.setItem('sivant.correo', 'previo@sivant.test');
    const { el, escribir, enviar } = crear();
    el.querySelector<HTMLInputElement>('input[type=checkbox]')!.click();
    escribir('contrasena', 'clave');
    enviar();
    expect(localStorage.getItem('sivant.correo')).toBeNull();

    TestBed.resetTestingModule();
    iniciar.mockImplementation(() => throwError(() => new Error('rechazado')));
    const otra = crear();
    otra.escribir('correo', 'x@sivant.test');
    otra.escribir('contrasena', 'clave');
    otra.el.querySelector<HTMLInputElement>('input[type=checkbox]')!.click();
    otra.enviar();
    expect(localStorage.getItem('sivant.correo')).toBeNull();
  });

  it('CP-F36 «¿Olvidaste tu contraseña?» indica acudir al administrador, sin llamar a la API', () => {
    const { el, fixture } = crear();
    const enlace = el.querySelector<HTMLButtonElement>('.enlace')!;
    expect(enlace.getAttribute('aria-expanded')).toBe('false');
    enlace.click();
    fixture.detectChanges();
    expect(enlace.getAttribute('aria-expanded')).toBe('true');
    expect(el.textContent).toContain('Pide a un administrador');
    expect(iniciar).not.toHaveBeenCalled();
  });

  it('CP-F37 el botón del ojo muestra y oculta la contraseña', () => {
    const { el, fixture } = crear();
    const ojo = el.querySelector<HTMLButtonElement>('.boton-icono')!;
    const campo = el.querySelector<HTMLInputElement>('#contrasena')!;
    expect(campo.type).toBe('password');
    ojo.click();
    fixture.detectChanges();
    expect(campo.type).toBe('text');
    expect(ojo.getAttribute('aria-pressed')).toBe('true');
    ojo.click();
    fixture.detectChanges();
    expect(campo.type).toBe('password');
  });
});
