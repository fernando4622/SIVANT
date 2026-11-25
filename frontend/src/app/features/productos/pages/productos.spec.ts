import { HttpErrorResponse } from '@angular/common/http';
import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter, withComponentInputBinding } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { of, throwError } from 'rxjs';
import { Rol } from '../../../core/auth/rol';
import { Sesion } from '../../../core/auth/sesion';
import { Producto, ProductosApi } from '../data-access/productos.api';
import { rutasProductos } from '../productos.routes';

const CAFE: Producto = {
  id: '3',
  nombre: 'Capuchino',
  descripcion: 'Con leche',
  precioVenta: '1055.00',
  tipo: 'PREPARADO',
  unidadMedida: 'PIEZA',
  activo: true,
  receta: [{ insumoId: '1', nombre: 'Café', unidadMedida: 'GRAMO', cantidadRequerida: '18' }],
};
const AGUA: Producto = { ...CAFE, id: '4', nombre: 'Agua', tipo: 'REVENTA', activo: false, receta: undefined };

describe('Gestionar productos', () => {
  const api = {
    listar: vi.fn(),
    obtener: vi.fn(),
    insumos: vi.fn(),
    registrar: vi.fn(),
    editar: vi.fn(),
    desactivar: vi.fn(),
  };
  let harness: RouterTestingHarness;
  let el: HTMLElement;
  const perfil = signal<{ id: string; nombre: string; rol: Rol }>({ id: '1', nombre: 'A', rol: 'ADMINISTRADOR' });

  beforeEach(async () => {
    vi.resetAllMocks();
    perfil.set({ id: '1', nombre: 'A', rol: 'ADMINISTRADOR' });
    api.listar.mockReturnValue(
      of({ contenido: [CAFE, AGUA], page: 0, size: 10, totalElementos: 2, totalPaginas: 1 }),
    );
    api.obtener.mockReturnValue(of(CAFE));
    api.insumos.mockReturnValue(
      of([
        { id: '1', nombre: 'Café', unidadMedida: 'GRAMO' },
        { id: '2', nombre: 'Leche', unidadMedida: 'MILILITRO' },
      ]),
    );
    HTMLDialogElement.prototype.showModal = function () {
      this.open = true;
    };
    HTMLDialogElement.prototype.close = function () {
      this.open = false;
    };
    TestBed.configureTestingModule({
      providers: [
        provideRouter(
          [
            { path: 'productos', children: rutasProductos },
            { path: 'inicio', children: [] },
          ],
          withComponentInputBinding(),
        ),
        { provide: ProductosApi, useValue: api },
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
    const campo = el.querySelector<HTMLInputElement | HTMLSelectElement>(`#${id}`)!;
    campo.value = valor;
    campo.dispatchEvent(new Event(campo.tagName === 'SELECT' ? 'change' : 'input'));
    await refrescar();
  }

  async function enviar() {
    el.querySelector('form')!.dispatchEvent(new Event('submit'));
    await refrescar();
  }

  async function datosComunes() {
    await escribir('nombre', 'Latte');
    await escribir('descripcion', 'Suave');
    await escribir('precioVenta', '60,5');
  }

  it('CP-F18 lista con precio y estado; solo los activos pueden desactivarse', async () => {
    await abrir('/productos?q=a&tipo=REVENTA&activo=false&page=1');
    expect(api.listar).toHaveBeenCalledWith({ q: 'a', tipo: 'REVENTA', activo: 'false', page: 1 });
    expect(el.textContent).toContain('$1,055.00');
    expect(el.querySelector('[aria-label="Desactivar Capuchino"]')).not.toBeNull();
    expect(el.querySelector('[aria-label="Desactivar Agua"]')).toBeNull();
  });

  it('CP-F19 el mesero ve el catálogo sin acciones y no entra a la edición', async () => {
    perfil.set({ id: '2', nombre: 'M', rol: 'MESERO' });
    await abrir('/productos');
    expect(el.textContent).toContain('Catálogo');
    expect(el.textContent).not.toContain('Nuevo producto');
    expect(el.querySelector('[aria-label^="Editar"], [aria-label^="Desactivar"]')).toBeNull();
    await harness.navigateByUrl('/productos/3');
    expect(TestBed.inject(Router).url).toBe('/inicio');
  });

  it('CP-F20 el alta de preparado valida la receta y normaliza decimales', async () => {
    api.registrar.mockReturnValue(of(CAFE));
    await abrir('/productos/nuevo');
    await enviar();
    expect(api.registrar).not.toHaveBeenCalled();
    expect(el.textContent).toContain('Selecciona un insumo.');

    await datosComunes();
    await escribir('insumo-0', '1');
    await escribir('cantidad-0', '0');
    await enviar();
    expect(el.textContent).toContain('mayor que cero');

    await escribir('cantidad-0', '18,5');
    [...el.querySelectorAll('button')].find((b) => b.textContent?.includes('Agregar insumo'))!.click();
    await refrescar();
    await escribir('insumo-1', '1');
    await escribir('cantidad-1', '2');
    await enviar();
    expect(el.textContent).toContain('ya está en la receta');
    expect(api.registrar).not.toHaveBeenCalled();

    await escribir('insumo-1', '2');
    await enviar();
    expect(api.registrar).toHaveBeenCalledWith({
      nombre: 'Latte',
      descripcion: 'Suave',
      precioVenta: '60.5',
      tipo: 'PREPARADO',
      unidadMedida: 'PIEZA',
      receta: [
        { insumoId: '1', cantidadRequerida: '18.5' },
        { insumoId: '2', cantidadRequerida: '2' },
      ],
    });
  });

  it('CP-F21 el alta de reventa pide nivel mínimo y no envía receta', async () => {
    api.registrar.mockReturnValue(of(AGUA));
    await abrir('/productos/nuevo');
    await datosComunes();
    await escribir('tipo', 'REVENTA');
    await enviar();
    expect(el.textContent).toContain('nivel mínimo válido');
    await escribir('nivelMinimo', '5');
    await enviar();
    expect(api.registrar).toHaveBeenCalledWith({
      nombre: 'Latte',
      descripcion: 'Suave',
      precioVenta: '60.5',
      tipo: 'REVENTA',
      unidadMedida: 'PIEZA',
      nivelMinimo: '5',
    });
    expect(TestBed.inject(Router).url).toBe('/productos');
  });

  it('CP-F22 la edición muestra la receta y envía solo lo modificado', async () => {
    api.editar.mockReturnValue(of(CAFE));
    await abrir('/productos/3');
    expect(el.textContent).toContain('18 g');
    await enviar();
    expect(api.editar).not.toHaveBeenCalled();
    await escribir('precioVenta', '60');
    await enviar();
    expect(api.editar).toHaveBeenCalledWith('3', { precioVenta: '60' });
  });

  it('CP-F23 un error de la API conserva lo capturado y marca el campo', async () => {
    api.editar.mockReturnValue(
      throwError(
        () =>
          new HttpErrorResponse({
            status: 400,
            error: { codigo: 'DATOS_INVALIDOS', detail: 'x', erroresCampo: { nombre: 'Nombre no válido.' } },
          }),
      ),
    );
    await abrir('/productos/3');
    await escribir('nombre', 'Otro');
    await enviar();
    expect(el.querySelector('#nombre-error')!.textContent).toContain('Nombre no válido.');
    expect(el.querySelector<HTMLInputElement>('#nombre')!.value).toBe('Otro');
  });
});
