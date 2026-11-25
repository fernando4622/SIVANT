-- Catálogo fijo de roles. Carga idempotente.
INSERT INTO sivant.rol (nombre, descripcion) VALUES
    ('MESERO',        'Abre cuentas, registra comandas, sirve y cobra'),
    ('CAJERO',        'Cancela comandas, consulta ventas, recibe efectivo y entrega fondos'),
    ('COCINERO',      'Prepara comandas y las marca listas'),
    ('ADMINISTRADOR', 'Gestiona empleados y productos, cancela ventas y resuelve ajustes'),
    ('ALMACENISTA',   'Registra movimientos de inventario y solicita ajustes')
ON CONFLICT (nombre) DO NOTHING;
