import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router, provideRouter, withComponentInputBinding } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { of, throwError } from 'rxjs';
import { Sesion } from '../../../core/auth/sesion';
import { Empleado, EmpleadosApi } from '../data-access/empleados.api';
import { rutasEmpleados } from '../empleados.routes';

const ANA: Empleado = { id: '7', nombreCompleto: 'Ana Ruiz', correo: 'ana@sivant.test', rol: 'CAJERO', estado: 'ACTIVO' };
const CIRO: Empleado = { id: '8', nombreCompleto: 'Ciro Paz', correo: 'ciro@sivant.test', rol: 'MESERO', estado: 'INACTIVO' };

const pagina = (...contenido: Empleado[]) =>
  of({ contenido, page: 0, size: 10, totalElementos: contenido.length, totalPaginas: 1 });
const error = (status: number, cuerpo: object) => throwError(() => new HttpErrorResponse({ status, error: cuerpo }));

describe('Gestionar empleados', () => {
  const api = {
    listar: vi.fn(),
    obtener: vi.fn(),
    registrar: vi.fn(),
    editar: vi.fn(),
    desactivar: vi.fn(),
    reactivar: vi.fn(),
  };
  let harness: RouterTestingHarness;
  let el: HTMLElement;

  // La carga diferida de las pantallas no debe contar en el tiempo de cada caso.
  beforeAll(() => Promise.all([import('./listado-empleados'), import('./formulario-empleado')]), 60_000);

  beforeEach(async () => {
    vi.resetAllMocks();
    api.listar.mockReturnValue(pagina(ANA, CIRO));
    api.obtener.mockReturnValue(of(ANA));
    // jsdom no implementa los diálogos modales.
    HTMLDialogElement.prototype.showModal = function () {
      this.open = true;
    };
    HTMLDialogElement.prototype.close = function () {
      this.open = false;
    };
    TestBed.configureTestingModule({
      providers: [
        provideRouter([{ path: 'empleados', children: rutasEmpleados }], withComponentInputBinding()),
        { provide: EmpleadosApi, useValue: api },
        { provide: Sesion, useValue: { perfil: () => ({ id: '1', nombre: 'Admin', rol: 'ADMINISTRADOR' }) } },
      ],
    });
    harness = await RouterTestingHarness.create();
  });

  async function abrir(url: string) {
    await harness.navigateByUrl(url);
    await refrescar();
    el = harness.routeNativeElement!.parentElement!;
  }

  async function refrescar() {
    const fixture: ComponentFixture<unknown> = harness.fixture;
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
  }

  function escribir(id: string, valor: string) {
    const campo = el.querySelector<HTMLInputElement | HTMLSelectElement>(`#${id}`)!;
    campo.value = valor;
    campo.dispatchEvent(new Event(campo.tagName === 'SELECT' ? 'change' : 'input'));
  }

  async function enviar() {
    el.querySelector('form')!.dispatchEvent(new Event('submit'));
    await refrescar();
  }

  const boton = (texto: string) =>
    [...el.querySelectorAll('button')].find((b) => (b.getAttribute('aria-label') ?? b.textContent)?.includes(texto));

  it('CP-F10 ofrece desactivar a los activos y reactivar a los inactivos', async () => {
    await abrir('/empleados?q=a&rol=CAJERO&estado=ACTIVO&page=2');
    expect(api.listar).toHaveBeenCalledWith({ q: 'a', rol: 'CAJERO', estado: 'ACTIVO', page: 2 });
    expect(el.textContent).toContain('Ana Ruiz');
    expect(el.textContent).toContain('Inactivo');
    expect(boton('Desactivar a Ana')).toBeDefined();
    expect(boton('Desactivar a Ciro')).toBeUndefined();
    expect(boton('Reactivar a Ciro')).toBeDefined();
    expect(boton('Reactivar a Ana')).toBeUndefined();
  });

  it('reactiva al empleado tras confirmar y actualiza el listado', async () => {
    await abrir('/empleados');
    const activo = { ...CIRO, estado: 'ACTIVO' } as const;
    api.reactivar.mockReturnValue(of(activo));
    api.listar.mockReturnValueOnce(pagina(ANA, activo));

    boton('Reactivar a Ciro')!.click();
    await refrescar();
    expect(el.querySelector('dialog')!.open).toBe(true);
    expect(api.reactivar).not.toHaveBeenCalled();
    el.querySelector<HTMLButtonElement>('dialog .boton--principal')!.click();
    await refrescar();

    expect(api.reactivar).toHaveBeenCalledWith('8');
    expect(el.querySelector('dialog')!.open).toBe(false);
    expect(el.textContent).toContain('Ciro Paz ya puede iniciar sesión.');
    expect(boton('Desactivar a Ciro')).toBeDefined();
  });

  it('CP-F11 desactivar exige confirmar; cancelar no llama a la API', async () => {
    await abrir('/empleados');
    boton('Desactivar a Ana')!.click();
    await refrescar();
    expect(el.querySelector('dialog')!.open).toBe(true);
    boton('Cancelar')!.click();
    await refrescar();
    expect(el.querySelector('dialog')!.open).toBe(false);
    expect(api.desactivar).not.toHaveBeenCalled();

    api.desactivar.mockReturnValue(of({ ...ANA, estado: 'INACTIVO' }));
    boton('Desactivar a Ana')!.click();
    await refrescar();
    el.querySelector<HTMLButtonElement>('dialog .boton--peligro')!.click();
    await refrescar();
    expect(api.desactivar).toHaveBeenCalledWith('7');
    expect(api.listar).toHaveBeenCalledTimes(2);
    expect(el.textContent).toContain('Ana Ruiz quedó inactivo');
  });

  it('CP-F12 si la API rechaza la desactivación, el diálogo muestra el motivo', async () => {
    api.desactivar.mockReturnValue(error(409, { codigo: 'ULTIMO_ADMINISTRADOR', detail: 'Debe permanecer uno.' }));
    await abrir('/empleados');
    boton('Desactivar a Ana')!.click();
    el.querySelector<HTMLButtonElement>('dialog .boton--peligro')!.click();
    await refrescar();
    expect(el.querySelector('dialog')!.textContent).toContain('Debe permanecer uno.');
  });

  it('CP-F13 muestra el error de carga y permite reintentar', async () => {
    api.listar.mockReturnValueOnce(error(0, {}));
    await abrir('/empleados');
    expect(el.textContent).toContain('No hay conexión');
    boton('Reintentar')!.click();
    await refrescar();
    expect(el.textContent).toContain('Ana Ruiz');
  });

  it('CP-F14 el alta valida antes de enviar', async () => {
    await abrir('/empleados/nuevo');
    escribir('correo', 'sin-arroba');
    escribir('contrasena', 'corta');
    await enviar();
    expect(api.registrar).not.toHaveBeenCalled();
    for (const texto of ['Escribe el nombre', 'correo válido', 'entre 8 y 72', 'Selecciona un cargo.']) {
      expect(el.textContent).toContain(texto);
    }
  });

  it('CP-F15 un 409 muestra el error en el correo y conserva lo capturado', async () => {
    api.registrar.mockReturnValue(
      error(409, { codigo: 'CORREO_DUPLICADO', detail: 'Repetido.', erroresCampo: { correo: 'Correo en uso.' } }),
    );
    await abrir('/empleados/nuevo');
    escribir('nombreCompleto', 'Laura');
    escribir('correo', 'ana@sivant.test');
    escribir('contrasena', 'Inicial.2026');
    escribir('rol', 'MESERO');
    await enviar();
    expect(el.querySelector('#correo-error')!.textContent).toContain('Correo en uso.');
    expect(el.querySelector<HTMLInputElement>('#nombreCompleto')!.value).toBe('Laura');
  });

  it('CP-F16 la edición envía solo lo modificado y vuelve al listado', async () => {
    api.editar.mockReturnValue(of(ANA));
    await abrir('/empleados/7');
    await enviar();
    expect(api.editar).not.toHaveBeenCalled();
    expect(el.textContent).toContain('No hay cambios por guardar.');

    escribir('rol', 'MESERO');
    await enviar();
    expect(api.editar).toHaveBeenCalledWith('7', { rol: 'MESERO' });
    expect(TestBed.inject(Router).url).toBe('/empleados');
  });

  it('CP-F17 un empleado inexistente muestra el error', async () => {
    api.obtener.mockReturnValue(error(404, { codigo: 'NO_ENCONTRADO', detail: 'El empleado no existe.' }));
    await abrir('/empleados/999');
    expect(el.textContent).toContain('El empleado no existe.');
    expect(el.querySelector('form')).toBeNull();
  });
});
