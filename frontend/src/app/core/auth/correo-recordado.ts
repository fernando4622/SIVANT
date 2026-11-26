const CLAVE = 'sivant.correo';

/**
 * «Recordarme» guarda únicamente el correo en este navegador. No guarda la contraseña ni alarga la sesión.
 * El almacenamiento puede estar bloqueado: en ese caso simplemente no se recuerda.
 */
export const correoRecordado = {
  leer(): string {
    try {
      return localStorage.getItem(CLAVE) ?? '';
    } catch {
      return '';
    }
  },

  guardar(correo: string | null): void {
    try {
      if (correo) {
        localStorage.setItem(CLAVE, correo);
      } else {
        localStorage.removeItem(CLAVE);
      }
    } catch {
      // Sin almacenamiento disponible.
    }
  },
};
