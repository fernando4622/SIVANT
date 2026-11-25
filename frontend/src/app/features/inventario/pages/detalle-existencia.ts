import { ChangeDetectionStrategy, Component, OnInit, computed, inject, input, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { filter } from 'rxjs';
import { Sesion } from '../../../core/auth/sesion';
import { ClaveIdempotencia } from '../../../core/http/clave-idempotencia';
import { leerProblema } from '../../../core/http/problema';
import { TiempoReal } from '../../../core/realtime/tiempo-real';
import {
  PATRON_CANTIDAD,
  PATRON_CANTIDAD_POSITIVA,
  cantidad,
  fechaHora,
  normalizarDecimal,
  unidad,
} from '../../../shared/formato';
import { Pagina } from '../../../shared/listado';
import { Icono } from '../../../shared/ui/icono';
import { Paginacion } from '../../../shared/ui/paginacion';
import { TablaHistorial } from '../components/tabla-historial';
import { Existencia, Historial, InventarioApi, TipoMovimiento } from '../data-access/inventario.api';

/** Detalle de RF16. El almacenista registra movimientos (RF17) y solicita ajustes (RF18). */
@Component({
  selector: 'app-detalle-existencia',
  imports: [ReactiveFormsModule, RouterLink, Icono, Paginacion, TablaHistorial],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './detalle-existencia.html',
  styleUrl: './detalle-existencia.css',
})
export class DetalleExistencia implements OnInit {
  readonly id = input.required<string>();

  private readonly api = inject(InventarioApi);
  private readonly router = inject(Router);
  private readonly sesion = inject(Sesion);
  private readonly fb = inject(NonNullableFormBuilder);
  private readonly claveMovimiento = new ClaveIdempotencia();
  private readonly claveAjuste = new ClaveIdempotencia();

  protected readonly opera = computed(() => this.sesion.perfil()?.rol === 'ALMACENISTA');
  protected readonly cantidad = cantidad;
  protected readonly unidad = unidad;
  protected readonly fechaHora = fechaHora;

  protected readonly existencia = signal<Existencia | null>(null);
  protected readonly historial = signal<Pagina<Historial> | null>(null);
  protected readonly errorCarga = signal<string | null>(null);
  protected readonly mensaje = signal('');

  protected readonly movimiento = this.fb.group({
    tipo: this.fb.control<TipoMovimiento>('ENTRADA_MANUAL'),
    cantidad: ['', [Validators.required, Validators.pattern(PATRON_CANTIDAD_POSITIVA)]],
  });
  protected readonly enviadoMovimiento = signal(false);
  protected readonly guardandoMovimiento = signal(false);
  protected readonly errorMovimiento = signal<string | null>(null);

  protected readonly ajuste = this.fb.group({
    cantidadSolicitada: ['', [Validators.required, Validators.pattern(PATRON_CANTIDAD)]],
    justificacion: ['', [Validators.required, Validators.pattern(/\S/), Validators.maxLength(500)]],
  });
  protected readonly enviadoAjuste = signal(false);
  protected readonly guardandoAjuste = signal(false);
  protected readonly errorAjuste = signal<string | null>(null);

  constructor() {
    inject(TiempoReal)
      .cambios('EXISTENCIA_ACTUALIZADA')
      .pipe(
        filter((evento) => evento === null || evento.recursoId === this.id()),
        takeUntilDestroyed(),
      )
      .subscribe(() => {
        this.actualizarSaldo(true);
        this.cargarHistorial(this.historial()?.page ?? 0, true);
      });
  }

  ngOnInit(): void {
    this.cargar();
  }

  protected cargar(): void {
    this.errorCarga.set(null);
    this.api.existencia(this.id()).subscribe({
      next: (existencia) => this.existencia.set(existencia),
      error: (error: unknown) => this.errorCarga.set(leerProblema(error).detalle),
    });
    this.cargarHistorial(0);
  }

  protected cargarHistorial(page: number, automatica = false): void {
    this.api.movimientosDe(this.id(), { page }, automatica).subscribe({
      next: (pagina) => this.historial.set(pagina),
      error: (error: unknown) => this.errorCarga.set(leerProblema(error).detalle),
    });
  }

  protected registrarMovimiento(): void {
    this.enviadoMovimiento.set(true);
    this.errorMovimiento.set(null);
    this.mensaje.set('');
    if (this.movimiento.invalid || this.guardandoMovimiento()) {
      return;
    }
    const valor = this.movimiento.getRawValue();
    const datos = { tipo: valor.tipo, cantidad: normalizarDecimal(valor.cantidad) };
    this.guardandoMovimiento.set(true);
    this.api.registrarMovimiento(this.id(), datos, this.claveMovimiento.para(datos)).subscribe({
      next: ({ existencia }) => {
        this.claveMovimiento.completar();
        this.guardandoMovimiento.set(false);
        this.enviadoMovimiento.set(false);
        this.movimiento.controls.cantidad.reset();
        this.existencia.set(existencia);
        this.mensaje.set(
          `${datos.tipo === 'ENTRADA_MANUAL' ? 'Entrada' : 'Salida'} registrada. ` +
            `Existencia actual: ${cantidad(existencia.cantidadActual)} ${unidad(existencia.unidadMedida)}.`,
        );
        this.cargarHistorial(0);
      },
      error: (error: unknown) => {
        const problema = leerProblema(error);
        this.guardandoMovimiento.set(false);
        this.errorMovimiento.set(problema.erroresCampo['cantidad'] ?? problema.detalle);
        if (problema.estado === 409) {
          this.actualizarSaldo();
        }
      },
    });
  }

  protected solicitarAjuste(): void {
    this.enviadoAjuste.set(true);
    this.errorAjuste.set(null);
    if (this.ajuste.invalid || this.guardandoAjuste()) {
      return;
    }
    const valor = this.ajuste.getRawValue();
    const datos = {
      existenciaId: this.id(),
      cantidadSolicitada: normalizarDecimal(valor.cantidadSolicitada),
      justificacion: valor.justificacion,
    };
    this.guardandoAjuste.set(true);
    this.api.solicitarAjuste(datos, this.claveAjuste.para(datos)).subscribe({
      next: (solicitud) => {
        this.claveAjuste.completar();
        void this.router.navigate(['/inventario/ajustes', solicitud.id]);
      },
      error: (error: unknown) => {
        const problema = leerProblema(error);
        this.guardandoAjuste.set(false);
        this.errorAjuste.set(Object.values(problema.erroresCampo)[0] ?? problema.detalle);
      },
    });
  }

  /** Conserva lo capturado y muestra el saldo vigente. */
  private actualizarSaldo(automatica = false): void {
    this.api.existencia(this.id(), automatica).subscribe({
      next: (existencia) => this.existencia.set(existencia),
      error: () => undefined,
    });
  }
}
