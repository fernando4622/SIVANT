import { HttpErrorResponse } from '@angular/common/http';

/** Error de la API en formato Problem Details (docs/openapi.yaml). */
export interface Problema {
  estado: number;
  codigo: string;
  detalle: string;
  erroresCampo: Record<string, string>;
}

const SIN_CONEXION = 'No hay conexión con el servidor. Revisa la red e intenta de nuevo.';

export function leerProblema(error: unknown): Problema {
  const respuesta = error instanceof HttpErrorResponse ? error : null;
  const cuerpo = (respuesta?.error ?? {}) as { codigo?: string; detail?: string; erroresCampo?: Record<string, string> };
  if (respuesta && cuerpo.codigo) {
    return {
      estado: respuesta.status,
      codigo: cuerpo.codigo,
      detalle: cuerpo.detail ?? 'No se pudo completar la operación.',
      erroresCampo: cuerpo.erroresCampo ?? {},
    };
  }
  // Sin cuerpo de la API: la red falló o el proxy no encontró el backend.
  return { estado: respuesta?.status ?? 0, codigo: 'SIN_CONEXION', detalle: SIN_CONEXION, erroresCampo: {} };
}
