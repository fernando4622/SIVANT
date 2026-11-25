import { ChangeDetectionStrategy, Component, OnInit, computed, inject, input, signal } from '@angular/core';
import { takeUntilDestroyed, toObservable } from '@angular/core/rxjs-interop';
import { Router, RouterLink } from '@angular/router';
import { catchError, debounceTime, of, switchMap } from 'rxjs';
import { ClaveIdempotencia } from '../../../core/http/clave-idempotencia';
import { leerProblema } from '../../../core/http/problema';
import { dinero, sumaDinero } from '../../../shared/formato';
import { Pagina } from '../../../shared/listado';
import { Icono } from '../../../shared/ui/icono';
import { Cuenta, ServicioApi } from '../../mesas-cuentas/data-access/servicio.api';
import { Producto, ProductosApi, TipoProducto } from '../../productos/data-access/productos.api';
import { PasoCantidad } from '../components/paso-cantidad';

interface Linea {
  clave: number;
  producto: Producto;
  cantidad: number;
  observaciones: string;
}

/** RF01: registrar una comanda y enviarla a cocina. */
@Component({
  selector: 'app-nueva-comanda',
  imports: [RouterLink, Icono, PasoCantidad],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './nueva-comanda.html',
  styleUrl: './comandas.css',
})
export class NuevaComanda implements OnInit {
  readonly id = input.required<string>();

  private readonly api = inject(ServicioApi);
  private readonly productosApi = inject(ProductosApi);
  private readonly router = inject(Router);
  private readonly clave = new ClaveIdempotencia();
  private siguiente = 0;

  protected readonly cuenta = signal<Cuenta | null>(null);
  protected readonly filtro = signal<{ q: string; tipo: TipoProducto | ''; page: number }>({ q: '', tipo: '', page: 0 });
  protected readonly catalogo = signal<Pagina<Producto> | null>(null);
  protected readonly errorCatalogo = signal<string | null>(null);
  protected readonly lineas = signal<Linea[]>([]);
  protected readonly total = computed(() => {
    const importes = this.lineas().flatMap((l) => Array<string>(l.cantidad).fill(l.producto.precioVenta));
    return sumaDinero(importes);
  });
  protected readonly enviando = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly dinero = dinero;
  protected readonly tipos: { valor: TipoProducto | ''; texto: string }[] = [
    { valor: '', texto: 'Todos' },
    { valor: 'PREPARADO', texto: 'Preparados' },
    { valor: 'REVENTA', texto: 'Reventa' },
  ];

  constructor() {
    toObservable(this.filtro)
      .pipe(
        debounceTime(250),
        switchMap((filtro) =>
          this.productosApi.listar({ ...filtro, activo: 'true' }).pipe(
            catchError((error: unknown) => {
              this.errorCatalogo.set(leerProblema(error).detalle);
              return of(null);
            }),
          ),
        ),
        takeUntilDestroyed(),
      )
      .subscribe((pagina) => {
        if (pagina) {
          this.errorCatalogo.set(null);
          this.catalogo.set(pagina);
        }
      });
  }

  ngOnInit(): void {
    this.api.cuenta(this.id()).subscribe({
      next: (cuenta) => this.cuenta.set(cuenta),
      error: (error: unknown) => this.error.set(leerProblema(error).detalle),
    });
  }

  protected buscar(q: string): void {
    this.filtro.update((f) => ({ ...f, q: q.trim(), page: 0 }));
  }

  protected filtrarTipo(tipo: TipoProducto | ''): void {
    this.filtro.update((f) => ({ ...f, tipo, page: 0 }));
  }

  protected irA(page: number): void {
    this.filtro.update((f) => ({ ...f, page }));
  }

  protected agregar(producto: Producto): void {
    this.error.set(null);
    const existente = this.lineas().find((l) => l.producto.id === producto.id && !l.observaciones);
    if (existente) {
      this.cambiarCantidad(existente.clave, Math.min(999, existente.cantidad + 1));
      return;
    }
    const clave = this.siguiente++;
    this.lineas.update((lineas) => [...lineas, { clave, producto, cantidad: 1, observaciones: '' }]);
  }

  protected cambiarCantidad(clave: number, cantidad: number): void {
    this.lineas.update((lineas) => lineas.map((l) => (l.clave === clave ? { ...l, cantidad } : l)));
  }

  protected anotar(clave: number, observaciones: string): void {
    this.lineas.update((lineas) => lineas.map((l) => (l.clave === clave ? { ...l, observaciones } : l)));
  }

  protected quitar(clave: number): void {
    this.lineas.update((lineas) => lineas.filter((l) => l.clave !== clave));
  }

  protected enviar(): void {
    if (this.enviando() || this.lineas().length === 0) {
      return;
    }
    const detalles = this.lineas().map((l) => ({
      productoId: l.producto.id,
      cantidad: l.cantidad,
      observaciones: l.observaciones.trim(),
    }));
    this.error.set(null);
    this.enviando.set(true);
    this.api.enviar(this.id(), detalles, this.clave.para(detalles)).subscribe({
      next: () => void this.router.navigate(['/cuentas', this.id()]),
      error: (error: unknown) => {
        // Se conserva lo capturado.
        const problema = leerProblema(error);
        this.enviando.set(false);
        this.error.set(Object.values(problema.erroresCampo)[0] ?? problema.detalle);
        if (problema.codigo === 'PRODUCTO_NO_DISPONIBLE') {
          this.filtro.update((f) => ({ ...f }));
        }
      },
    });
  }
}
