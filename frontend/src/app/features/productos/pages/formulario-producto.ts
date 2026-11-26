import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  OnInit,
  computed,
  inject,
  input,
  signal,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { Observable, of, switchMap, tap } from 'rxjs';
import { leerProblema } from '../../../core/http/problema';
import {
  NOMBRE_UNIDAD,
  PATRON_CANTIDAD,
  PATRON_CANTIDAD_POSITIVA,
  PATRON_DINERO_POSITIVO,
  cantidad,
  normalizarDecimal,
  unidad,
} from '../../../shared/formato';
import { Icono } from '../../../shared/ui/icono';
import {
  AltaProducto,
  CambiosProducto,
  Insumo,
  NOMBRE_TIPO,
  Producto,
  ProductosApi,
  TAMANO_MAXIMO_IMAGEN,
  TIPOS_IMAGEN,
  TipoProducto,
  UnidadMedida,
} from '../data-access/productos.api';

/** Alta (RF07) y edición (RF09). La edición cambia nombre, descripción, precio e imagen. */
@Component({
  selector: 'app-formulario-producto',
  imports: [ReactiveFormsModule, RouterLink, Icono],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './formulario-producto.html',
  styleUrl: './formulario-producto.css',
})
export class FormularioProducto implements OnInit {
  readonly id = input<string>();

  private readonly api = inject(ProductosApi);
  private readonly router = inject(Router);
  private readonly fb = inject(NonNullableFormBuilder);

  protected readonly nombreTipo = NOMBRE_TIPO;
  protected readonly unidades = Object.entries(NOMBRE_UNIDAD) as [UnidadMedida, string][];
  protected readonly unidad = unidad;
  protected readonly cantidad = cantidad;

  protected readonly formulario = this.fb.group({
    nombre: ['', [Validators.required, Validators.pattern(/\S/), Validators.maxLength(120)]],
    descripcion: ['', [Validators.required, Validators.pattern(/\S/), Validators.maxLength(500)]],
    precioVenta: ['', [Validators.required, Validators.pattern(PATRON_DINERO_POSITIVO)]],
    activo: true,
    tipo: this.fb.control<TipoProducto>('PREPARADO'),
    unidadMedida: this.fb.control<UnidadMedida>('PIEZA'),
    nivelMinimo: ['', [Validators.required, Validators.pattern(PATRON_CANTIDAD)]],
    receta: this.fb.array([this.componente()]),
  });

  protected readonly insumos = signal<Insumo[]>([]);
  protected readonly original = signal<Producto | null>(null);
  protected readonly cargando = signal(false);
  protected readonly guardando = signal(false);
  protected readonly enviado = signal(false);
  protected readonly errorCarga = signal<string | null>(null);
  protected readonly error = signal<string | null>(null);
  protected readonly erroresServidor = signal<Record<string, string>>({});
  protected readonly edicion = computed(() => this.id() !== undefined);

  /** Imagen elegida y aún sin guardar; `null` si se pidió quitar la actual. */
  protected readonly imagenNueva = signal<File | null | undefined>(undefined);
  protected readonly errorImagen = signal<string | null>(null);
  /** Producto ya registrado cuya imagen no se pudo guardar: no se vuelve a dar de alta. */
  protected readonly creado = signal<string | null>(null);
  private readonly urlLocal = signal<string | null>(null);
  protected readonly vistaPrevia = computed(() => {
    const nueva = this.imagenNueva();
    if (nueva === null) {
      return null;
    }
    return nueva ? this.urlLocal() : (this.original()?.imagen ?? null);
  });

  constructor() {
    inject(DestroyRef).onDestroy(() => this.liberarVista());
    this.formulario.valueChanges.pipe(takeUntilDestroyed()).subscribe(() => this.erroresServidor.set({}));
    this.formulario.controls.tipo.valueChanges.pipe(takeUntilDestroyed()).subscribe((tipo) => this.segunTipo(tipo));
  }

  ngOnInit(): void {
    const id = this.id();
    this.cargando.set(true);
    if (id === undefined) {
      this.segunTipo('PREPARADO');
      this.api.insumos().subscribe({
        next: (insumos) => {
          this.insumos.set(insumos);
          this.cargando.set(false);
        },
        error: (error: unknown) => this.falloCarga(error),
      });
      return;
    }
    const { tipo, unidadMedida, nivelMinimo, receta } = this.formulario.controls;
    [tipo, unidadMedida, nivelMinimo, receta].forEach((control) => control.disable({ emitEvent: false }));
    this.api.obtener(id).subscribe({
      next: (producto) => {
        this.original.set(producto);
        const { nombre, descripcion, precioVenta, activo } = producto;
        this.formulario.patchValue({ nombre, descripcion, precioVenta, activo });
        this.cargando.set(false);
      },
      error: (error: unknown) => this.falloCarga(error),
    });
  }

  /** Se valida aquí para avisar antes de enviar; la API vuelve a revisar el contenido del archivo. */
  protected elegirImagen(selector: HTMLInputElement): void {
    const archivo = selector.files?.[0];
    selector.value = '';
    if (!archivo) {
      return;
    }
    if (!TIPOS_IMAGEN.includes(archivo.type)) {
      this.errorImagen.set('Usa una imagen JPG, PNG o WebP.');
      return;
    }
    if (archivo.size > TAMANO_MAXIMO_IMAGEN) {
      this.errorImagen.set('La imagen pesa más de 2 MB.');
      return;
    }
    this.errorImagen.set(null);
    this.liberarVista();
    this.urlLocal.set(URL.createObjectURL(archivo));
    this.imagenNueva.set(archivo);
  }

  protected quitarImagen(selector: HTMLInputElement): void {
    selector.value = '';
    this.errorImagen.set(null);
    this.liberarVista();
    this.imagenNueva.set(this.original()?.imagen ? null : undefined);
  }

  protected agregarInsumo(): void {
    this.formulario.controls.receta.push(this.componente());
  }

  protected quitarInsumo(indice: number): void {
    this.formulario.controls.receta.removeAt(indice);
  }

  protected unidadDe(insumoId: string): string {
    const insumo = this.insumos().find((i) => i.id === insumoId);
    return insumo ? unidad(insumo.unidadMedida) : '';
  }

  protected repetido(indice: number): boolean {
    const filas = this.formulario.controls.receta.controls;
    const id = filas[indice].controls.insumoId.value;
    return id !== '' && filas.some((fila, i) => i < indice && fila.controls.insumoId.value === id);
  }

  protected errorDe(campo: 'nombre' | 'descripcion' | 'precioVenta' | 'nivelMinimo'): string | null {
    const servidor = this.erroresServidor()[campo];
    if (servidor) {
      return servidor;
    }
    const control = this.formulario.controls[campo];
    if (control.disabled || control.valid || !(control.touched || this.enviado())) {
      return null;
    }
    switch (campo) {
      case 'nombre':
        return control.hasError('maxlength') ? 'Usa 120 caracteres como máximo.' : 'Escribe el nombre.';
      case 'descripcion':
        return control.hasError('maxlength') ? 'Usa 500 caracteres como máximo.' : 'Escribe la descripción.';
      case 'precioVenta':
        return 'Escribe un precio mayor que 0, con dos decimales como máximo.';
      case 'nivelMinimo':
        return 'Escribe un nivel mínimo válido.';
    }
  }

  protected guardar(): void {
    this.enviado.set(true);
    this.error.set(null);
    if (this.guardando() || this.creado()) {
      return;
    }
    const repetidos = this.formulario.controls.receta.controls.some((_, i) => this.repetido(i));
    if (this.formulario.invalid || (this.formulario.controls.receta.enabled && repetidos)) {
      this.error.set('Revisa los datos marcados.');
      return;
    }
    const id = this.id();
    let operacion;
    if (id === undefined) {
      operacion = this.api.registrar(this.alta()).pipe(tap((producto) => this.creado.set(producto.id)));
    } else {
      const cambios = this.cambios();
      const cambiaEstado = this.formulario.controls.activo.value !== this.original()?.activo;
      if (Object.keys(cambios).length === 0 && !cambiaEstado && this.imagenNueva() === undefined) {
        this.error.set('No hay cambios por guardar.');
        return;
      }
      const estado = () =>
        this.formulario.controls.activo.value ? this.api.reactivar(id) : this.api.desactivar(id);
      const actual = this.original()!;
      operacion = Object.keys(cambios).length
        ? this.api.editar(id, cambios).pipe(switchMap((producto) => (cambiaEstado ? estado() : this.resultado(producto))))
        : cambiaEstado
          ? estado()
          : of(actual);
    }
    operacion = operacion.pipe(switchMap((producto) => this.guardarImagen(producto)));
    this.guardando.set(true);
    operacion.subscribe({
      next: () => void this.router.navigateByUrl('/productos'),
      error: (error: unknown) => {
        // Se conservan los datos capturados para corregirlos.
        const problema = leerProblema(error);
        this.guardando.set(false);
        const deImagen = problema.erroresCampo['archivo'];
        if (deImagen) {
          this.errorImagen.set(deImagen);
        }
        if (this.creado()) {
          this.error.set(`El producto se registró, pero no se guardó la imagen: ${deImagen ?? problema.detalle}`);
          return;
        }
        this.erroresServidor.set(problema.erroresCampo);
        const campos = Object.keys(problema.erroresCampo);
        this.error.set(problema.erroresCampo['receta'] ?? (campos.length ? 'Revisa los datos marcados.' : problema.detalle));
      },
    });
  }

  /** La imagen se guarda después del producto, porque el alta necesita su ID. */
  private guardarImagen(producto: Producto): Observable<Producto> {
    const nueva = this.imagenNueva();
    if (nueva) {
      return this.api.subirImagen(producto.id, nueva);
    }
    return nueva === null ? this.api.quitarImagen(producto.id) : of(producto);
  }

  private liberarVista(): void {
    const url = this.urlLocal();
    if (url) {
      URL.revokeObjectURL(url);
      this.urlLocal.set(null);
    }
  }

  private componente() {
    return this.fb.group({
      insumoId: ['', Validators.required],
      cantidadRequerida: ['', [Validators.required, Validators.pattern(PATRON_CANTIDAD_POSITIVA)]],
    });
  }

  private segunTipo(tipo: TipoProducto): void {
    const { nivelMinimo, receta } = this.formulario.controls;
    const [activo, inactivo] = tipo === 'PREPARADO' ? [receta, nivelMinimo] : [nivelMinimo, receta];
    activo.enable({ emitEvent: false });
    inactivo.disable({ emitEvent: false });
    if (tipo === 'PREPARADO' && receta.length === 0) {
      this.agregarInsumo();
    }
  }

  private alta(): AltaProducto {
    const v = this.formulario.getRawValue();
    const comun = {
      nombre: v.nombre,
      descripcion: v.descripcion,
      precioVenta: normalizarDecimal(v.precioVenta),
      tipo: v.tipo,
      unidadMedida: v.unidadMedida,
    };
    return v.tipo === 'PREPARADO'
      ? {
          ...comun,
          receta: v.receta.map((c) => ({
            insumoId: c.insumoId,
            cantidadRequerida: normalizarDecimal(c.cantidadRequerida),
          })),
        }
      : { ...comun, nivelMinimo: normalizarDecimal(v.nivelMinimo) };
  }

  private cambios(): CambiosProducto {
    const v = this.formulario.getRawValue();
    const original = this.original();
    const cambios: CambiosProducto = {};
    if (v.nombre.trim() !== original?.nombre) {
      cambios.nombre = v.nombre;
    }
    if (v.descripcion.trim() !== original?.descripcion) {
      cambios.descripcion = v.descripcion;
    }
    if (normalizarDecimal(v.precioVenta) !== original?.precioVenta) {
      cambios.precioVenta = normalizarDecimal(v.precioVenta);
    }
    return cambios;
  }

  private resultado(producto: Producto): Observable<Producto> {
    return new Observable((suscriptor) => {
      suscriptor.next(producto);
      suscriptor.complete();
    });
  }

  private falloCarga(error: unknown): void {
    this.errorCarga.set(leerProblema(error).detalle);
    this.cargando.set(false);
  }
}
