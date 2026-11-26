import { ChangeDetectionStrategy, Component, OnInit, computed, inject, input, signal } from '@angular/core';
import { takeUntilDestroyed, toSignal } from '@angular/core/rxjs-interop';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { filter, startWith } from 'rxjs';
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
import {
  CausaSalida,
  DatosMovimiento,
  Existencia,
  Historial,
  InventarioApi,
  NOMBRE_CAUSA,
  TipoMovimiento,
} from '../data-access/inventario.api';

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
  protected readonly nivelActualizado = signal(false);
  protected readonly movimientoRegistrado = signal<TipoMovimiento | null>(null);

  protected readonly movimiento = this.fb.group({
    tipo: this.fb.control<TipoMovimiento>('ENTRADA_MANUAL'),
    cantidad: ['', [Validators.required, Validators.pattern(PATRON_CANTIDAD_POSITIVA)]],
    causa: this.fb.control<CausaSalida | ''>(''),
    nota: ['', [Validators.maxLength(300)]],
  });
  protected readonly causas = Object.entries(NOMBRE_CAUSA);
  private readonly valoresMovimiento = toSignal(
    this.movimiento.valueChanges.pipe(startWith(this.movimiento.getRawValue())),
    { initialValue: this.movimiento.getRawValue() },
  );
  protected readonly esSalida = computed(() => this.valoresMovimiento().tipo === 'SALIDA_MANUAL');
  protected readonly errorCausa = computed(() =>
    this.enviadoMovimiento() && this.esSalida() && !this.valoresMovimiento().causa ? 'Selecciona por qué sale.' : '',
  );
  protected readonly errorNota = computed(() => {
    const { causa, nota = '' } = this.valoresMovimiento();
    if (!this.enviadoMovimiento() || !this.esSalida()) {
      return '';
    }
    if (nota.length > 300) {
      return 'Usa 300 caracteres como máximo.';
    }
    return causa === 'OTRO' && !nota.trim() ? 'Describe por qué sale.' : '';
  });

  protected readonly minimo = this.fb.group({
    nivelMinimo: ['', [Validators.required, Validators.pattern(PATRON_CANTIDAD)]],
  });
  protected readonly enviadoMinimo = signal(false);
  protected readonly guardandoMinimo = signal(false);
  protected readonly errorServidorMinimo = signal<string | null>(null);
  private readonly valorMinimo = toSignal(this.minimo.valueChanges, { initialValue: this.minimo.getRawValue() });
  protected readonly errorMinimo = computed(() => {
    this.valorMinimo();
    if (this.enviadoMinimo() && this.minimo.invalid) {
      return 'Escribe una cantidad desde 0, con hasta 6 decimales.';
    }
    return this.errorServidorMinimo() ?? '';
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
    this.nivelActualizado.set(false);
    this.movimientoRegistrado.set(null);
    if (this.movimiento.invalid || this.errorCausa() || this.errorNota() || this.guardandoMovimiento()) {
      return;
    }
    const valor = this.movimiento.getRawValue();
    const datos: DatosMovimiento = { tipo: valor.tipo, cantidad: normalizarDecimal(valor.cantidad) };
    if (valor.tipo === 'SALIDA_MANUAL') {
      datos.causa = valor.causa as CausaSalida;
      if (valor.nota.trim()) {
        datos.nota = valor.nota.trim();
      }
    }
    this.guardandoMovimiento.set(true);
    this.api.registrarMovimiento(this.id(), datos, this.claveMovimiento.para(datos)).subscribe({
      next: ({ existencia }) => {
        this.claveMovimiento.completar();
        this.guardandoMovimiento.set(false);
        this.enviadoMovimiento.set(false);
        this.movimiento.controls.cantidad.reset();
        this.movimiento.controls.causa.reset();
        this.movimiento.controls.nota.reset();
        this.existencia.set(existencia);
        this.movimientoRegistrado.set(datos.tipo);
        this.cargarHistorial(0);
      },
      error: (error: unknown) => {
        const problema = leerProblema(error);
        this.guardandoMovimiento.set(false);
        const campos = problema.erroresCampo;
        this.errorMovimiento.set(campos['cantidad'] ?? campos['causa'] ?? campos['nota'] ?? problema.detalle);
        if (problema.estado === 409) {
          this.actualizarSaldo();
        }
      },
    });
  }

  protected cambiarMinimo(): void {
    this.enviadoMinimo.set(true);
    this.errorServidorMinimo.set(null);
    this.mensaje.set('');
    this.nivelActualizado.set(false);
    this.movimientoRegistrado.set(null);
    if (this.minimo.invalid || this.guardandoMinimo()) {
      return;
    }
    this.guardandoMinimo.set(true);
    this.api.cambiarNivelMinimo(this.id(), normalizarDecimal(this.minimo.controls.nivelMinimo.value)).subscribe({
      next: (existencia) => {
        this.guardandoMinimo.set(false);
        this.enviadoMinimo.set(false);
        this.minimo.reset();
        this.existencia.set(existencia);
        this.nivelActualizado.set(true);
      },
      error: (error: unknown) => {
        const problema = leerProblema(error);
        this.guardandoMinimo.set(false);
        this.errorServidorMinimo.set(problema.erroresCampo['nivelMinimo'] ?? problema.detalle);
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
