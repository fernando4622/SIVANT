import { Signal, effect, inject, signal } from '@angular/core';
import { takeUntilDestroyed, toSignal } from '@angular/core/rxjs-interop';
import { FormControl, FormGroup } from '@angular/forms';
import { ActivatedRoute, ParamMap, Params, Router } from '@angular/router';
import { BehaviorSubject, Observable, catchError, combineLatest, debounceTime, map, of, startWith, switchMap } from 'rxjs';
import { leerProblema } from '../core/http/problema';

export interface Pagina<T> {
  contenido: T[];
  page: number;
  size: number;
  totalElementos: number;
  totalPaginas: number;
}

export const TAMANO_PAGINA = 10;

interface Vista<T> {
  cargando: boolean;
  pagina?: Pagina<T>;
  error?: string;
}

export interface Listado<T> {
  vista: Signal<Vista<T>>;
  /** Mientras llega la siguiente página se conserva la anterior a la vista. */
  pagina: Signal<Pagina<T> | undefined>;
  recargar(): void;
  irA(page: number): void;
  /** Cambia filtros en la URL y regresa a la primera página. */
  filtrar(filtros: Record<string, string>): void;
}

/** Los filtros viven en la URL: se conservan al recargar o volver. Se usa en un contexto de inyección. */
export function crearListado<T>(
  consultar: (parametros: ParamMap, page: number) => Observable<Pagina<T>>,
): Listado<T> {
  const ruta = inject(ActivatedRoute);
  const router = inject(Router);
  const recarga = new BehaviorSubject<void>(undefined);
  const pagina = signal<Pagina<T> | undefined>(undefined);

  const vista = toSignal(
    combineLatest([ruta.queryParamMap, recarga]).pipe(
      switchMap(([parametros]) =>
        consultar(parametros, Math.max(0, Math.trunc(Number(parametros.get('page') ?? 0)) || 0)).pipe(
          map((resultado): Vista<T> => ({ cargando: false, pagina: resultado })),
          catchError((error: unknown) => of<Vista<T>>({ cargando: false, error: leerProblema(error).detalle })),
          startWith<Vista<T>>({ cargando: true }),
        ),
      ),
    ),
    { initialValue: { cargando: true } as Vista<T> },
  );

  const navegar = (queryParams: Params) =>
    void router.navigate([], { relativeTo: ruta, queryParams, queryParamsHandling: 'merge' });
  const irA = (page: number) => navegar({ page: page || null });

  effect(() => {
    const actual = vista().pagina;
    if (!actual) {
      return;
    }
    pagina.set(actual);
    // La última página puede desaparecer al cambiar los datos.
    if (actual.contenido.length === 0 && actual.page > 0) {
      irA(Math.max(0, actual.totalPaginas - 1));
    }
  });

  return {
    vista,
    pagina,
    recargar: () => recarga.next(),
    irA,
    filtrar: (filtros) =>
      navegar({ ...Object.fromEntries(Object.entries(filtros).map(([k, v]) => [k, v.trim() || null])), page: null }),
  };
}

/** Mantiene el formulario de filtros y la URL con los mismos valores. Se usa en un contexto de inyección. */
export function conectarFiltros<C extends { [K in keyof C]: FormControl<string> }>(
  formulario: FormGroup<C>,
  listado: Pick<Listado<unknown>, 'filtrar'>,
): void {
  const controles = formulario.controls as unknown as Record<string, FormControl<string>>;
  inject(ActivatedRoute)
    .queryParamMap.pipe(takeUntilDestroyed())
    .subscribe((parametros) => {
      for (const [nombre, control] of Object.entries(controles)) {
        const valor = parametros.get(nombre) ?? '';
        if (valor !== control.value.trim()) {
          control.setValue(valor, { emitEvent: false });
        }
      }
    });
  formulario.valueChanges
    .pipe(debounceTime(300), takeUntilDestroyed())
    .subscribe(() => listado.filtrar(formulario.getRawValue() as Record<string, string>));
}
