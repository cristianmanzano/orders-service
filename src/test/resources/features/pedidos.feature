# language: es
Característica: Gestión de pedidos
  Como cliente de la tienda
  Quiero crear y pagar pedidos
  Para recibir mis productos con el mejor descuento disponible

  Antecedentes:
    Dado que el catálogo tiene los siguientes productos:
      | sku        | nombre     | precio |
      | MOUSE-01   | Mouse      | 25.00  |
      | MONITOR-27 | Monitor 27 | 300.00 |

  Escenario: Pedido sin descuento
    Cuando el cliente "alice" crea un pedido con:
      | sku      | cantidad |
      | MOUSE-01 | 2        |
    Entonces el pedido queda en estado "CREATED"
    Y el total del pedido es 50.00 USD
    Y se publica el evento "OrderCreated"

  Esquema del escenario: Se aplica el mejor descuento disponible
    Cuando el cliente "alice" crea un pedido con:
      | sku        | cantidad   |
      | MOUSE-01   | <mouses>   |
      | MONITOR-27 | <monitors> |
    Entonces el total del pedido es <total> USD

    Ejemplos:
      | mouses | monitors | total  | regla                         |
      | 1      | 1        | 325.00 | sin descuento                 |
      | 8      | 2        | 720.00 | volumen: 10 unidades, 10%     |
      | 1      | 2        | 593.75 | umbral: subtotal >= 500, 5%   |

  Escenario: No se puede pedir un producto inexistente
    Cuando el cliente "alice" crea un pedido con:
      | sku       | cantidad |
      | NO-EXISTE | 1        |
    Entonces la operación falla con el error "InvalidOrderException"
    Y no se publica ningún evento

  Escenario: Pago aprobado por la pasarela
    Dado que el cliente "alice" tiene un pedido con 1 unidad de "MONITOR-27"
    Y la pasarela de pagos aprueba los cobros
    Cuando el cliente "alice" paga el pedido
    Entonces el pedido queda en estado "PAID"
    Y se publica el evento "OrderPaid"

  Escenario: Pago rechazado por la pasarela
    Dado que el cliente "alice" tiene un pedido con 1 unidad de "MONITOR-27"
    Y la pasarela de pagos rechaza los cobros
    Cuando el cliente "alice" paga el pedido
    Entonces la operación falla con el error "PaymentRejectedException"
    Y el pedido queda en estado "CREATED"

  Escenario: Un cliente no puede ver los pedidos de otro cliente
    Dado que el cliente "alice" tiene un pedido con 1 unidad de "MOUSE-01"
    Cuando el cliente "bob" consulta el pedido
    Entonces la operación falla con el error "OrderNotFoundException"
