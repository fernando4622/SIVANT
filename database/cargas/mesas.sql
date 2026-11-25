-- Carga autorizada de mesas. La aplicación no ofrece su alta en esta etapa.
-- Ajustar el rango al número de mesas del restaurante. Es repetible: omite las que ya existen.
\set ON_ERROR_STOP on
INSERT INTO sivant.mesa (numero)
SELECT n FROM generate_series(1, 10) AS n
ON CONFLICT (numero) DO NOTHING;
