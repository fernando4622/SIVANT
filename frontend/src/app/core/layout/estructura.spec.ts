import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { Rol } from '../auth/rol';
import { Sesion } from '../auth/sesion';
import { Bandeja } from '../notificaciones/bandeja';
import { Estructura } from './estructura';

describe('Menú principal', () => {
  it.each<[Rol, string[]]>([
    ['ADMINISTRADOR', ['Inicio', 'Empleados', 'Productos', 'Ventas', 'Inventario', 'Avisos']],
    ['MESERO', ['Inicio', 'Mesas', 'Productos', 'Avisos']],
    ['CAJERO', ['Inicio', 'Mesas', 'Caja', 'Ventas', 'Avisos']],
    ['COCINERO', ['Inicio', 'Cocina', 'Avisos']],
    ['ALMACENISTA', ['Inicio', 'Inventario', 'Avisos']],
  ])('CP-F83 %s ve solo sus opciones', async (rol, opciones) => {
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        { provide: Sesion, useValue: { perfil: signal({ id: '1', nombre: 'P', rol }), cerrar: vi.fn(), activa: () => true } },
        { provide: Bandeja, useValue: { noLeidas: signal(0), reciente: signal(null), leer: vi.fn() } },
      ],
    });
    const fixture = TestBed.createComponent(Estructura);
    fixture.detectChanges();
    await fixture.whenStable();
    const enlaces = [...(fixture.nativeElement as HTMLElement).querySelectorAll('nav[aria-label="Principal"] a')];
    expect(enlaces.map((a) => a.textContent!.trim())).toEqual(opciones);
  });
});
