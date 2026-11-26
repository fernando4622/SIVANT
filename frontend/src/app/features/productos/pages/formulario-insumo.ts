import { ChangeDetectionStrategy, Component, DestroyRef, inject, signal } from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { Observable, of, switchMap } from 'rxjs';
import { leerProblema } from '../../../core/http/problema';
import { NOMBRE_UNIDAD, PATRON_CANTIDAD, normalizarDecimal } from '../../../shared/formato';
import { Icono } from '../../../shared/ui/icono';
import {
  InsumoCreado,
  ProductosApi,
  TAMANO_MAXIMO_IMAGEN,
  TIPOS_IMAGEN,
  UnidadMedida,
} from '../data-access/productos.api';

@Component({
  selector: 'app-formulario-insumo',
  imports: [ReactiveFormsModule, RouterLink, Icono],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './formulario-insumo.html',
  styleUrl: './formulario-insumo.css',
})
export class FormularioInsumo {
  private readonly api = inject(ProductosApi);
  private readonly router = inject(Router);
  private readonly fb = inject(NonNullableFormBuilder);

  protected readonly unidades = Object.entries(NOMBRE_UNIDAD) as [UnidadMedida, string][];
  protected readonly formulario = this.fb.group({
    nombre: ['', [Validators.required, Validators.pattern(/\S/), Validators.maxLength(120)]],
    unidadMedida: this.fb.control<UnidadMedida>('PIEZA', Validators.required),
    nivelMinimo: ['', [Validators.required, Validators.pattern(PATRON_CANTIDAD)]],
  });
  protected readonly enviado = signal(false);
  protected readonly guardando = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly erroresServidor = signal<Record<string, string>>({});
  protected readonly imagen = signal<File | null>(null);
  protected readonly errorImagen = signal<string | null>(null);
  protected readonly vistaPrevia = signal<string | null>(null);

  constructor() {
    inject(DestroyRef).onDestroy(() => this.liberarVista());
    this.formulario.valueChanges.subscribe(() => this.erroresServidor.set({}));
  }

  protected elegirImagen(selector: HTMLInputElement): void {
    const archivo = selector.files?.[0];
    selector.value = '';
    if (!archivo) return;
    if (!TIPOS_IMAGEN.includes(archivo.type)) {
      this.errorImagen.set('Usa una imagen JPG, PNG o WebP.');
      return;
    }
    if (archivo.size > TAMANO_MAXIMO_IMAGEN) {
      this.errorImagen.set('La imagen pesa más de 2 MB.');
      return;
    }
    this.liberarVista();
    this.imagen.set(archivo);
    this.vistaPrevia.set(URL.createObjectURL(archivo));
    this.errorImagen.set(null);
  }

  protected quitarImagen(): void {
    this.liberarVista();
    this.imagen.set(null);
    this.errorImagen.set(null);
  }

  protected errorDe(campo: 'nombre' | 'nivelMinimo'): string | null {
    if (this.erroresServidor()[campo]) return this.erroresServidor()[campo];
    const control = this.formulario.controls[campo];
    if (control.valid || !(control.touched || this.enviado())) return null;
    if (campo === 'nombre') {
      return control.hasError('maxlength') ? 'Usa 120 caracteres como máximo.' : 'Escribe el nombre.';
    }
    return 'Escribe un nivel mínimo válido.';
  }

  protected guardar(): void {
    this.enviado.set(true);
    this.error.set(null);
    if (this.guardando() || this.formulario.invalid) {
      if (this.formulario.invalid) this.error.set('Revisa los datos marcados.');
      return;
    }
    const valor = this.formulario.getRawValue();
    this.guardando.set(true);
    this.api
      .registrarInsumo({
        nombre: valor.nombre.trim(),
        unidadMedida: valor.unidadMedida,
        nivelMinimo: normalizarDecimal(valor.nivelMinimo),
      })
      .pipe(switchMap((insumo) => this.guardarImagen(insumo)))
      .subscribe({
        next: () => void this.router.navigateByUrl('/inventario'),
        error: (error: unknown) => {
          const problema = leerProblema(error);
          this.guardando.set(false);
          this.erroresServidor.set(problema.erroresCampo);
          this.errorImagen.set(problema.erroresCampo['archivo'] ?? null);
          this.error.set(Object.keys(problema.erroresCampo).length ? 'Revisa los datos marcados.' : problema.detalle);
        },
      });
  }

  private guardarImagen(insumo: InsumoCreado): Observable<InsumoCreado> {
    const archivo = this.imagen();
    return archivo ? this.api.subirImagenInsumo(insumo.id, archivo) : of(insumo);
  }

  private liberarVista(): void {
    const url = this.vistaPrevia();
    if (url) URL.revokeObjectURL(url);
    this.vistaPrevia.set(null);
  }
}
