# Importación de respaldos sin pérdida de datos

El respaldo es un archivo JSON que se exporta e importa manualmente. Importar nunca borra nada: se fusiona con lo que ya hay en la app, para poder restaurar un respaldo en un teléfono que ya tiene datos nuevos sin perderlos.

Para que la fusión funcione, cada Venta, Línea, Producto y Canal tiene un identificador único (UUID) asignado al crearse y una marca de última modificación. Al importar:

- Registro que no existe en la app → se agrega.
- Registro con el mismo identificador en ambos lados → gana la versión con la última modificación más reciente.
- Productos y Canales con distinto identificador pero el mismo nombre (sin distinguir mayúsculas ni espacios) son el mismo: lo importado se asocia al existente. Por eso los nombres son únicos en el catálogo.

## Considered Options

- **Reemplazar todo al importar**: más simple, pero restaurar un respaldo viejo borraría las ventas cargadas después.
- **Sincronización en la nube**: resuelve el respaldo automáticamente, pero exige cuenta, red y mucha más complejidad para una app de un solo usuario.

## Consequences

- Los borrados no viajan en el respaldo: un Producto borrado (solo se permite si no tiene ventas) reaparece si se importa un respaldo anterior al borrado. Se acepta.
- Las Ventas nunca se borran, solo se cancelan, así que el estado de cancelación viaja como una modificación más.
- Los identificadores no pueden ser autoincrementales: deben ser UUID desde el primer día.
