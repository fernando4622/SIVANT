import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';

const TRAZOS = {
  atras: 'M20 12H4m0 0 6-6m-6 6 6 6',
  mas: 'M12 4v16M4 12h16',
  buscar: 'M15.5 15.5 21 21M17 10a7 7 0 1 1-14 0 7 7 0 0 1 14 0Z',
  editar: 'M4 20l1-5L16 4l4 4L9 19l-5 1Z',
  basura: 'M4 7h16M9 7V4h6v3M6 7l1 13h10l1-13M10 11v5M14 11v5',
  guardar: 'M4 4h13l3 3v13H4V4Zm4 0v5h7V4M8 20v-6h8v6',
  info: 'M12 11v6M12 7.5v.01M21 12a9 9 0 1 1-18 0 9 9 0 0 1 18 0Z',
  persona: 'M16 8a4 4 0 1 1-8 0 4 4 0 0 1 8 0ZM4 21c0-4 3.5-6 8-6s8 2 8 6',
  empleados:
    'M12 8a3.5 3.5 0 1 1-7 0 3.5 3.5 0 0 1 7 0ZM2 20c0-3.5 2.8-5.5 6.5-5.5S15 16.500 15 20M15.500 4.800a3.500 3.500 0 0 1 0 6.400M17 14.800c3 .5 5 2.400 5 5.200',
  inicio: 'M3 11.500 12 4l9 7.500M5.500 10v10h13V10M10 20v-6h4v6',
  productos: 'M4 8h13v6a5 5 0 0 1-5 5H9a5 5 0 0 1-5-5V8Zm13 2h1.500a2.500 2.500 0 0 1 0 5H17M8 2v3M12 2v3',
  inventario: 'M3 8l9-5 9 5v9l-9 5-9-5V8Zm0 0 9 5m0 0 9-5m-9 5v9',
  ajuste: 'M9 4h6v3H9V4ZM9 5H5v16h14V5h-4M8.500 14l2.500 2.500 4.500-5',
  palomita: 'm4 12 6 6L20 6',
  reactivar: 'M3 4v6h6M3.5 10a9 9 0 1 1 0 4',
  ventas: 'M6 3h12v18l-3-2-3 2-3-2-3 2V3Zm3 5h6M9 12h6M9 16h3',
  cobro: 'M3 7h18v12H3V7Zm0 4h18M7 15h3',
  billete: 'M3 6h18v12H3V6Zm3 3a2 2 0 0 1-2 2m16-2a2 2 0 0 0 2 2M6 15a2 2 0 0 0-2-2m16 2a2 2 0 0 1 2-2M15 12a3 3 0 1 1-6 0 3 3 0 0 1 6 0Z',
  tarjeta: 'M3 6h18v13H3V6Zm0 4h18M7 15h4',
  calendario: 'M4 6h16v15H4V6Zm0 5h16M8 3v5M16 3v5',
  correo: 'M3 5h18v14H3V5Zm0 2 9 6 9-6',
  movil: 'M7 2h10a2 2 0 0 1 2 2v16a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2Zm3 3h4M11 19h2',
  candado: 'M5 11h14v10H5V11Zm3 0V7a4 4 0 0 1 8 0v4M12 15v2',
  mesas: 'M3 9h18M5 9v10M19 9v10M4 5h16l1 4H3l1-4Z',
  cocina: 'M4 10h16v4a7 7 0 0 1-7 7h-2a7 7 0 0 1-7-7v-4Zm4-7v3m4-4v4m4-3v3',
  campana: 'M6 10a6 6 0 1 1 12 0v5l2 3H4l2-3v-5Zm4 11h4',
  enviar: 'M3 11 21 3l-7 18-3-8-8-2Z',
  menos: 'M5 12h14',
  salir: 'M14 4H5v16h9M10 12h11m0 0-4-4m4 4-4 4',
  menu: 'M4 6h16M4 12h16M4 18h16',
  cerrar: 'M6 6l12 12M18 6 6 18',
  anterior: 'm14 6-6 6 6 6',
  siguiente: 'm10 6 6 6-6 6',
  ver: 'M2 12s3.500-7 10-7 10 7 10 7-3.500 7-10 7-10-7-10-7Zm13 0a3 3 0 1 1-6 0 3 3 0 0 1 6 0Z',
  ocultar: 'M3 3l18 18M10.6 5.1A11 11 0 0 1 12 5c6.5 0 10 7 10 7a17 17 0 0 1-2.8 3.5m-2.7 2.2A9.6 9.6 0 0 1 12 19c-6.5 0-10-7-10-7a17 17 0 0 1 4.3-4.7m3.6 2.6a3 3 0 0 0 4.2 4.2',
} as const;

export type NombreIcono = keyof typeof TRAZOS;

/** Decorativo: el texto accesible lo pone el control que lo contiene. */
@Component({
  selector: 'app-icono',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `<svg
    viewBox="0 0 24 24"
    [attr.width]="tamano()"
    [attr.height]="tamano()"
    fill="none"
    stroke="currentColor"
    stroke-width="2"
    stroke-linecap="round"
    stroke-linejoin="round"
    aria-hidden="true"
    focusable="false"
  >
    <path [attr.d]="trazo()" />
  </svg>`,
  styles: ':host { display: inline-flex }',
})
export class Icono {
  readonly nombre = input.required<NombreIcono>();
  readonly tamano = input(24);
  protected readonly trazo = computed(() => TRAZOS[this.nombre()]);
}
