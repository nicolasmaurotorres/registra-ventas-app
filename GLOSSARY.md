# Registra Ventas

Registro personal de las ventas de un emprendimiento de papelería creativa, para seguir sus envíos y ver estadísticas por producto.

## Ventas

**Venta**:
Un pedido de un cliente, con una o más Líneas, que se despacha y entrega como un todo.
_Avoid_: Pedido, orden, transacción

**Línea**:
Un Producto dentro de una Venta, con su cantidad y el Precio cobrado por unidad.
_Avoid_: Ítem, detalle

**Precio cobrado**:
El precio por unidad que realmente se cobró en una Línea; parte del Precio de referencia pero puede diferir (descuentos, ofertas).

**Cliente**:
Nombre libre y opcional de quien compró; no es una entidad con ficha propia.
_Avoid_: Comprador

**Costo de envío**:
Lo que el envío de una Venta le cuesta al emprendimiento; se resta de lo vendido. Es $0 cuando el envío está incluido o la entrega fue en mano.

**Comisión**:
Lo que la plataforma del Canal cobra por una Venta; se resta de lo vendido igual que el Costo de envío. Es $0 por defecto.

**Canal**:
La plataforma o lugar por donde se concretó una Venta (Mercado Libre, Facebook Marketplace, la tienda web, en mano). Cada Venta tiene exactamente uno.
_Avoid_: Plataforma, origen

**Venta cancelada**:
Una Venta que se conserva aparte del listado normal y no cuenta en las estadísticas; se puede reactivar en cualquier momento.

## Catálogo

**Producto**:
Un artículo del catálogo con un Precio de referencia, identificado por su nombre, que es único (sin distinguir mayúsculas ni espacios). Cada variante que se quiera medir por separado es un Producto distinto.
_Avoid_: Artículo, ítem

**Precio de referencia**:
El precio habitual de un Producto en el catálogo; cambiarlo no altera Ventas pasadas.

**Producto inactivo**:
Un Producto que ya no se ofrece al registrar Ventas nuevas, pero sigue presente en el historial y las estadísticas.
_Avoid_: Producto borrado, archivado

## Estadísticas

**Período**:
Una semana calendario (lunes a domingo), un mes o un año; las Ventas se asignan a un Período por su Fecha de venta.
_Avoid_: Últimos N días

**Monto**:
Suma de cantidad × Precio cobrado de las Líneas, en pesos argentinos con centavos; es el valor bruto, antes de costos.

**Neto**:
Monto menos Costos de envío y Comisiones. Solo existe para Ventas, Canales o Períodos completos, nunca por Producto.

## Fechas y estados

**Fecha de venta**:
El día en que se concretó la Venta; siempre presente.

**Fecha de despacho**:
El día en que la Venta salió hacia el Cliente; vacía mientras no se despacha.

**Fecha de entrega**:
El día en que la Venta llegó al Cliente; vacía mientras no se entrega. Una entrega en mano lleva las tres fechas iguales.

**Estado de envío**:
Se deduce de las fechas: _pendiente de despacho_ (sin despacho), _en camino_ (despachada, sin entrega) o _entregada_.
