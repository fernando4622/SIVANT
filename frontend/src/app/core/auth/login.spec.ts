import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { of } from 'rxjs';
import { Login } from './login';
import { Sesion } from './sesion';

describe('Inicio de sesión', () => {
  const iniciar = vi.fn(() => of({ id: '1', nombre: 'Ana', rol: 'MESERO' }));

  function crear(volver?: string) {
    iniciar.mockClear();
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
    return { el, escribir, enviar, navegar: vi.spyOn(TestBed.inject(Router), 'navigateByUrl').mockResolvedValue(true) };
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
});
