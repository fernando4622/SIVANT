export const ROLES = ['ADMINISTRADOR', 'ALMACENISTA', 'CAJERO', 'COCINERO', 'MESERO'] as const;

export type Rol = (typeof ROLES)[number];

export const NOMBRE_ROL: Record<Rol, string> = {
  ADMINISTRADOR: 'Administrador',
  ALMACENISTA: 'Almacenista',
  CAJERO: 'Cajero',
  COCINERO: 'Cocinero',
  MESERO: 'Mesero',
};
