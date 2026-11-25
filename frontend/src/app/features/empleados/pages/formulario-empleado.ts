import { ChangeDetectionStrategy, Component, OnInit, computed, inject, input, signal } from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { NOMBRE_ROL, ROLES, Rol } from '../../../core/auth/rol';
import { Sesion } from '../../../core/auth/sesion';
import { leerProblema } from '../../../core/http/problema';
import { Icono } from '../../../shared/ui/icono';
import { DatosEmpleado, Empleado, EmpleadosApi } from '../data-access/empleados.api';

type Campo = keyof DatosEmpleado;

const CORREO = /^\s*[A-Za-z0-9._%+'-]{1,64}@(?:[A-Za-z0-9-]{1,63}\.)+[A-Za-z0-9-]{2,63}\s*$/;

/** Alta (RF11) y edición (RF13). */
@Component({
  selector: 'app-formulario-empleado',
  imports: [ReactiveFormsModule, RouterLink, Icono],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './formulario-empleado.html',
  styleUrl: './formulario-empleado.css',
})
export class FormularioEmpleado implements OnInit {
  /** Parámetro de ruta. Sin él se registra un empleado nuevo. */
  readonly id = input<string>();

  private readonly api = inject(EmpleadosApi);
  private readonly router = inject(Router);
  private readonly sesion = inject(Sesion);
  private readonly fb = inject(NonNullableFormBuilder);

  protected readonly roles = ROLES;
  protected readonly nombreRol = NOMBRE_ROL;
  protected readonly formulario = this.fb.group({
    nombreCompleto: ['', [Validators.required, Validators.pattern(/\S/), Validators.maxLength(150)]],
    correo: ['', [Validators.required, Validators.pattern(CORREO), Validators.maxLength(254)]],
    contrasena: ['', [Validators.required, Validators.minLength(8), Validators.maxLength(72)]],
    rol: this.fb.control<Rol | ''>('', Validators.required),
  });

  protected readonly original = signal<Empleado | null>(null);
  protected readonly cargando = signal(false);
  protected readonly guardando = signal(false);
  protected readonly enviado = signal(false);
  protected readonly verContrasena = signal(false);
  protected readonly errorCarga = signal<string | null>(null);
  protected readonly error = signal<string | null>(null);
  protected readonly erroresServidor = signal<Partial<Record<Campo, string>>>({});
  protected readonly esPropio = computed(() => this.id() !== undefined && this.id() === this.sesion.perfil()?.id);

  ngOnInit(): void {
    if (this.id() !== undefined) {
      const contrasena = this.formulario.controls.contrasena;
      contrasena.removeValidators(Validators.required);
      contrasena.updateValueAndValidity();
      this.cargar();
    }
    for (const campo of Object.keys(this.formulario.controls) as Campo[]) {
      this.formulario.controls[campo].valueChanges.subscribe(() =>
        this.erroresServidor.update((errores) => ({ ...errores, [campo]: undefined })),
      );
    }
  }

  protected cargar(): void {
    this.cargando.set(true);
    this.errorCarga.set(null);
    this.api.obtener(this.id()!).subscribe({
      next: (empleado) => {
        const { nombreCompleto, correo, rol } = empleado;
        this.original.set(empleado);
        this.formulario.patchValue({ nombreCompleto, correo, rol });
        this.cargando.set(false);
      },
      error: (error: unknown) => {
        this.errorCarga.set(leerProblema(error).detalle);
        this.cargando.set(false);
      },
    });
  }

  protected errorDe(campo: Campo): string | null {
    const servidor = this.erroresServidor()[campo];
    if (servidor) {
      return servidor;
    }
    const control = this.formulario.controls[campo];
    if (control.valid || !(control.touched || this.enviado())) {
      return null;
    }
    switch (campo) {
      case 'nombreCompleto':
        return control.hasError('maxlength') ? 'Usa 150 caracteres como máximo.' : 'Escribe el nombre completo.';
      case 'correo':
        return control.hasError('required') ? 'Escribe el correo.' : 'Escribe un correo válido.';
      case 'contrasena':
        return 'Usa entre 8 y 72 caracteres.';
      case 'rol':
        return 'Selecciona un rol.';
    }
  }

  protected guardar(): void {
    this.enviado.set(true);
    this.error.set(null);
    if (this.guardando()) {
      return;
    }
    if (this.formulario.invalid) {
      this.enfocarPrimerError();
      return;
    }
    const datos = this.formulario.getRawValue() as DatosEmpleado;
    const id = this.id();
    let operacion;
    if (id === undefined) {
      operacion = this.api.registrar(datos);
    } else {
      const cambios = this.cambios(datos);
      if (Object.keys(cambios).length === 0) {
        this.error.set('No hay cambios por guardar.');
        return;
      }
      operacion = this.api.editar(id, cambios);
    }
    this.guardando.set(true);
    operacion.subscribe({
      next: () => void this.router.navigateByUrl('/empleados'),
      error: (error: unknown) => {
        // Se conservan los datos capturados para corregirlos.
        const problema = leerProblema(error);
        this.guardando.set(false);
        const porCampo = Object.keys(problema.erroresCampo).length > 0;
        this.error.set(porCampo ? 'Revisa los datos marcados.' : problema.detalle);
        this.erroresServidor.set(problema.erroresCampo);
        this.enfocarPrimerError();
      },
    });
  }

  private cambios(datos: DatosEmpleado): Partial<DatosEmpleado> {
    const original = this.original();
    const cambios: Partial<DatosEmpleado> = {};
    if (datos.nombreCompleto.trim() !== original?.nombreCompleto) {
      cambios.nombreCompleto = datos.nombreCompleto;
    }
    if (datos.correo.trim().toLowerCase() !== original?.correo) {
      cambios.correo = datos.correo;
    }
    if (datos.rol !== original?.rol) {
      cambios.rol = datos.rol;
    }
    if (datos.contrasena) {
      cambios.contrasena = datos.contrasena;
    }
    return cambios;
  }

  private enfocarPrimerError(): void {
    const campos = Object.keys(this.formulario.controls) as Campo[];
    const campo = campos.find((c) => this.errorDe(c));
    if (campo) {
      document.getElementById(campo)?.focus();
    }
  }
}
