import { ChangeDetectionStrategy, Component, computed, effect, inject, signal, viewChild } from '@angular/core';
import { takeUntilDestroyed, toSignal } from '@angular/core/rxjs-interop';
import { NonNullableFormBuilder, ReactiveFormsModule } from '@angular/forms';
import { ActivatedRoute, Params, Router, RouterLink } from '@angular/router';
import { BehaviorSubject, catchError, combineLatest, debounceTime, map, of, startWith, switchMap } from 'rxjs';
import { NOMBRE_ROL, ROLES } from '../../../core/auth/rol';
import { leerProblema } from '../../../core/http/problema';
import { Confirmacion } from '../../../shared/ui/confirmacion';
import { Icono } from '../../../shared/ui/icono';
import { Empleado, EmpleadosApi, FiltroEmpleados, PaginaEmpleados } from '../data-access/empleados.api';

interface Vista {
  cargando: boolean;
  pagina?: PaginaEmpleados;
  error?: string;
}

@Component({
  selector: 'app-listado-empleados',
  imports: [ReactiveFormsModule, RouterLink, Icono, Confirmacion],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './listado-empleados.html',
  styleUrl: './listado-empleados.css',
})
export class ListadoEmpleados {
  private readonly api = inject(EmpleadosApi);
  private readonly ruta = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly recargar = new BehaviorSubject<void>(undefined);

  protected readonly roles = ROLES;
  protected readonly nombreRol = NOMBRE_ROL;

  /** Los filtros viven en la URL: se conservan al recargar o volver. */
  private readonly filtro$ = this.ruta.queryParamMap.pipe(
    map(
      (p): FiltroEmpleados => ({
        q: p.get('q') ?? '',
        rol: p.get('rol') ?? '',
        estado: p.get('estado') ?? '',
        page: Math.max(0, Math.trunc(Number(p.get('page') ?? 0)) || 0),
      }),
    ),
  );

  protected readonly filtros = inject(NonNullableFormBuilder).group({ q: '', rol: '', estado: '' });

  protected readonly vista = toSignal(
    combineLatest([this.filtro$, this.recargar]).pipe(
      switchMap(([filtro]) =>
        this.api.listar(filtro).pipe(
          map((pagina): Vista => ({ cargando: false, pagina })),
          catchError((error: unknown) => of<Vista>({ cargando: false, error: leerProblema(error).detalle })),
          startWith<Vista>({ cargando: true }),
        ),
      ),
    ),
    { initialValue: { cargando: true } as Vista },
  );

  /** Mientras llega la siguiente página se conserva la anterior a la vista. */
  protected readonly pagina = signal<PaginaEmpleados | undefined>(undefined);
  protected readonly rango = computed(() => {
    const pagina = this.pagina();
    if (!pagina || pagina.totalElementos === 0) {
      return '';
    }
    const desde = pagina.page * pagina.size + 1;
    return `Mostrando ${desde}–${desde + pagina.contenido.length - 1} de ${pagina.totalElementos} empleados`;
  });

  protected readonly porDesactivar = signal<Empleado | null>(null);
  protected readonly desactivando = signal(false);
  protected readonly errorDesactivacion = signal<string | null>(null);
  protected readonly mensaje = signal('');
  private readonly confirmacion = viewChild.required(Confirmacion);

  constructor() {
    effect(() => {
      const { pagina } = this.vista();
      if (!pagina) {
        return;
      }
      this.pagina.set(pagina);
      // La última página puede desaparecer al cambiar los datos.
      if (pagina.contenido.length === 0 && pagina.page > 0) {
        this.irA(Math.max(0, pagina.totalPaginas - 1));
      }
    });
    this.filtro$.pipe(takeUntilDestroyed()).subscribe(({ q, rol, estado }) => {
      if (q !== this.filtros.controls.q.value.trim()) {
        this.filtros.controls.q.setValue(q, { emitEvent: false });
      }
      this.filtros.patchValue({ rol, estado }, { emitEvent: false });
    });
    this.filtros.valueChanges.pipe(debounceTime(300), takeUntilDestroyed()).subscribe(() => {
      const { q, rol, estado } = this.filtros.getRawValue();
      this.navegar({ q: q.trim() || null, rol: rol || null, estado: estado || null, page: null });
    });
  }

  protected iniciales(nombre: string): string {
    const palabras = nombre.trim().split(/\s+/);
    return palabras
      .slice(0, 2)
      .map((p) => p.charAt(0).toUpperCase())
      .join('');
  }

  protected irA(page: number): void {
    this.navegar({ page: page || null });
  }

  protected reintentar(): void {
    this.recargar.next();
  }

  protected pedirConfirmacion(empleado: Empleado): void {
    this.errorDesactivacion.set(null);
    this.mensaje.set('');
    this.porDesactivar.set(empleado);
    this.confirmacion().abrir();
  }

  protected desactivar(): void {
    const empleado = this.porDesactivar();
    if (!empleado || this.desactivando()) {
      return;
    }
    this.desactivando.set(true);
    this.api.desactivar(empleado.id).subscribe({
      next: () => {
        this.desactivando.set(false);
        this.confirmacion().cerrar();
        this.mensaje.set(`${empleado.nombreCompleto} quedó inactivo y ya no puede acceder.`);
        this.recargar.next();
      },
      error: (error: unknown) => {
        this.desactivando.set(false);
        this.errorDesactivacion.set(leerProblema(error).detalle);
      },
    });
  }

  private navegar(queryParams: Params): void {
    void this.router.navigate([], { relativeTo: this.ruta, queryParams, queryParamsHandling: 'merge' });
  }
}
