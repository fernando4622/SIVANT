import { HttpClient } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { Observable, catchError, map, of, switchMap, tap } from 'rxjs';
import { Rol } from './rol';

export interface Perfil {
  id: string;
  nombre: string;
  rol: Rol;
}

const API = '/api/v1/auth';
const INTERVALO_VIGILANCIA = 60_000;

/** Perfil de la sesión. La cookie es HttpOnly: el navegador la envía y este código no la ve. */
@Injectable({ providedIn: 'root' })
export class Sesion {
  private readonly http = inject(HttpClient);
  private readonly router = inject(Router);
  private readonly perfilActual = signal<Perfil | null>(null);
  private vigilancia: ReturnType<typeof setInterval> | undefined;

  readonly perfil = this.perfilActual.asReadonly();
  readonly activa = computed(() => this.perfilActual() !== null);

  /** Al abrir la aplicación obtiene el token CSRF y recupera la sesión si sigue vigente. */
  recuperar(): Observable<void> {
    return this.renovarCsrf().pipe(
      switchMap(() => this.http.get<Perfil>(`${API}/me`)),
      tap((perfil) => this.abrir(perfil)),
      catchError(() => of(null)),
      map(() => undefined),
    );
  }

  iniciar(correo: string, contrasena: string): Observable<Perfil> {
    return this.renovarCsrf().pipe(
      switchMap(() => this.http.post<Perfil>(`${API}/login`, { correo, contrasena })),
      switchMap((perfil) => this.renovarCsrf().pipe(map(() => perfil))),
      tap((perfil) => this.abrir(perfil)),
    );
  }

  cerrar(): Observable<unknown> {
    return this.http.post<void>(`${API}/logout`, null).pipe(
      tap(() => this.olvidar()),
      switchMap(() => this.renovarCsrf().pipe(catchError(() => of(undefined)))),
    );
  }

  /** El servidor ya no reconoce la sesión: expiró, se desactivó la cuenta o cambiaron sus permisos. */
  terminada(motivo: string): void {
    if (!this.activa()) {
      return;
    }
    const volver = this.router.url;
    this.olvidar();
    void this.router.navigate(['/login'], { queryParams: { motivo, volver } });
  }

  private abrir(perfil: Perfil): void {
    this.perfilActual.set(perfil);
    clearInterval(this.vigilancia);
    this.vigilancia = setInterval(() => this.comprobar(), INTERVALO_VIGILANCIA);
  }

  private olvidar(): void {
    this.perfilActual.set(null);
    clearInterval(this.vigilancia);
  }

  /** Detecta que la sesión terminó en el servidor. El encabezado evita que esta consulta cuente como actividad. */
  comprobar(): void {
    this.http
      .get<Perfil>(`${API}/me`, { headers: { 'X-Solicitud-Pasiva': 'true' } })
      .pipe(catchError(() => of(null)))
      .subscribe((perfil) => {
        if (perfil && this.activa()) {
          this.perfilActual.set(perfil);
        }
      });
  }

  private renovarCsrf(): Observable<unknown> {
    return this.http.get(`${API}/csrf`);
  }
}
