# Imágenes generadas de productos

Fotografías de catálogo generadas para los productos de la base de desarrollo.

- Formato: JPEG
- Dimensiones: 600 × 600 px
- Sin texto, logotipos ni marcas comerciales
- Fondo claro uniforme para las tarjetas de Productos e Inventario

Los productos se suben mediante `PUT /api/v1/productos/{id}/imagen` y los insumos mediante
`PUT /api/v1/insumos/{id}/imagen`.
El backend conserva la copia operativa en su almacén local no versionado.
