/** La API envía decimales como cadenas: se muestran sin convertirlos a número para no perder precisión. */
function conSeparadores(decimal: string): string {
  const [entero, fraccion] = decimal.replace('-', '').split('.');
  const miles = entero.replace(/\B(?=(\d{3})+(?!\d))/g, ',');
  return (decimal.startsWith('-') ? '-' : '') + miles + (fraccion ? `.${fraccion}` : '');
}

export function dinero(decimal: string): string {
  return `$${conSeparadores(decimal)}`;
}

/** Suma exacta en centavos. El importe definitivo lo calcula siempre la API. */
export function sumaDinero(importes: string[]): string {
  const centavos = importes.reduce((total, importe) => {
    const [entero, fraccion = ''] = importe.split('.');
    return total + BigInt(entero + fraccion.padEnd(2, '0'));
  }, 0n);
  const texto = centavos.toString().padStart(3, '0');
  return `${texto.slice(0, -2)}.${texto.slice(-2)}`;
}

export function cantidad(decimal: string): string {
  return conSeparadores(decimal);
}

/** Con signo explícito: +50, -3. */
export function cambio(decimal: string): string {
  return decimal.startsWith('-') ? conSeparadores(decimal) : `+${conSeparadores(decimal)}`;
}

const FECHA_HORA = new Intl.DateTimeFormat('es-MX', {
  timeZone: 'America/Mexico_City',
  day: '2-digit',
  month: '2-digit',
  year: 'numeric',
  hour: '2-digit',
  minute: '2-digit',
  hourCycle: 'h23',
});

export function fechaHora(iso: string): string {
  return FECHA_HORA.format(new Date(iso));
}

const UNIDADES: Record<string, string> = {
  PIEZA: 'pza',
  GRAMO: 'g',
  KILOGRAMO: 'kg',
  MILILITRO: 'ml',
  LITRO: 'l',
};

export function unidad(codigo: string): string {
  return UNIDADES[codigo] ?? codigo;
}

export const NOMBRE_UNIDAD = {
  PIEZA: 'Pieza',
  GRAMO: 'Gramo',
  KILOGRAMO: 'Kilogramo',
  MILILITRO: 'Mililitro',
  LITRO: 'Litro',
} as const;

/** Acepta «12», «12.5» o «12,5» y devuelve el decimal que espera la API. */
export function normalizarDecimal(texto: string): string {
  return texto.trim().replace(',', '.');
}

export const PATRON_DINERO = /^\s*\d{1,12}([.,]\d{1,2})?\s*$/;
/** Como PATRON_DINERO, mayor que 0. */
export const PATRON_DINERO_POSITIVO = /^(?=.*[1-9])\s*\d{1,12}([.,]\d{1,2})?\s*$/;
export const PATRON_CANTIDAD = /^\s*\d{1,12}([.,]\d{1,6})?\s*$/;
export const PATRON_CANTIDAD_POSITIVA = /^(?=.*[1-9])\s*\d{1,12}([.,]\d{1,6})?\s*$/;
