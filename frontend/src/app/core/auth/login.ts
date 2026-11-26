import { ChangeDetectionStrategy, Component, inject, input, signal } from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { Icono } from '../../shared/ui/icono';
import { Logo } from '../../shared/ui/logo';
import { leerProblema } from '../http/problema';
import { correoRecordado } from './correo-recordado';
import { Sesion } from './sesion';

const MOTIVOS: Record<string, string> = {
  SESION_EXPIRADA: 'La sesión se cerró por inactividad.',
  SESION_INVALIDADA: 'Tu acceso cambió. Inicia sesión de nuevo o consulta con administración.',
  SESION_REQUERIDA: 'Inicia sesión para continuar.',
};

@Component({
  selector: 'app-login',
  imports: [ReactiveFormsModule, Icono, Logo],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './login.html',
  styleUrl: './login.css',
})
export class Login {
  readonly motivo = input<string>();
  readonly volver = input<string>();

  private readonly sesion = inject(Sesion);
  private readonly router = inject(Router);

  private readonly recordado = correoRecordado.leer();

  protected readonly formulario = inject(NonNullableFormBuilder).group({
    correo: [this.recordado, Validators.required],
    contrasena: ['', Validators.required],
    recordar: [this.recordado !== ''],
  });
  protected readonly verContrasena = signal(false);
  protected readonly ayuda = signal(false);
  protected readonly enviando = signal(false);
  protected readonly enviado = signal(false);
  protected readonly error = signal<string | null>(null);

  protected aviso(): string | undefined {
    return MOTIVOS[this.motivo() ?? ''];
  }

  protected entrar(): void {
    this.enviado.set(true);
    this.error.set(null);
    if (this.formulario.invalid || this.enviando()) {
      return;
    }
    this.enviando.set(true);
    const { correo, contrasena, recordar } = this.formulario.getRawValue();
    this.sesion.iniciar(correo, contrasena).subscribe({
      next: () => {
        correoRecordado.guardar(recordar ? correo.trim() : null);
        void this.router.navigateByUrl(this.destino());
      },
      error: (error: unknown) => {
        this.enviando.set(false);
        this.error.set(leerProblema(error).detalle);
        this.verContrasena.set(false);
        this.formulario.controls.contrasena.reset();
        this.enviado.set(false);
      },
    });
  }

  /** Solo rutas internas. */
  private destino(): string {
    const volver = this.volver() ?? '';
    return /^\/(?![/\\])/.test(volver) && !volver.startsWith('/login') ? volver : '/inicio';
  }
}
