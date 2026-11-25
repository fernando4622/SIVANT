/**
 * Clave de una operación. Se conserva mientras los datos no cambien, para que reintentar tras un fallo de red
 * no aplique la operación dos veces.
 */
export class ClaveIdempotencia {
  private clave: string | null = null;
  private datos = '';

  para(datos: unknown): string {
    const huella = JSON.stringify(datos);
    if (this.clave === null || this.datos !== huella) {
      this.clave = crypto.randomUUID();
      this.datos = huella;
    }
    return this.clave;
  }

  /** Retoma un intento guardado, por ejemplo después de recargar la pestaña. */
  restaurar(clave: string, datos: unknown): void {
    this.clave = clave;
    this.datos = JSON.stringify(datos);
  }

  /** Al completarse, la siguiente operación usa otra clave aunque lleve los mismos datos. */
  completar(): void {
    this.clave = null;
  }
}
