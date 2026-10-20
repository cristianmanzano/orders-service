# cURL de orders-service

Requisitos: `docker compose up -d` y la app con perfil `local` (usuarios `alice`, `bob`, `admin`).
Los comandos usan sintaxis bash (Git Bash / Linux / macOS). En Postman: **Import → Raw text** y pegar cualquiera de ellos.

```bash
BASE=http://localhost:8080
PAY=http://localhost:8089
```

## 1. Auth

```bash
# Token alice (USER)
curl -X POST "$BASE/api/v1/auth/token" \
  -H "Content-Type: application/json" \
  -d '{"username":"alice","password":"alice123"}'

# Token bob (USER)
curl -X POST "$BASE/api/v1/auth/token" \
  -H "Content-Type: application/json" \
  -d '{"username":"bob","password":"bob123"}'

# Token admin (USER, ADMIN)
curl -X POST "$BASE/api/v1/auth/token" \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin123"}'

# Credenciales inválidas -> 401
curl -X POST "$BASE/api/v1/auth/token" \
  -H "Content-Type: application/json" \
  -d '{"username":"alice","password":"wrong"}'
```

Guardar los tokens en variables (requiere `jq`):

```bash
TOKEN=$(curl -s -X POST "$BASE/api/v1/auth/token" -H "Content-Type: application/json" \
  -d '{"username":"alice","password":"alice123"}' | jq -r .accessToken)
BOB_TOKEN=$(curl -s -X POST "$BASE/api/v1/auth/token" -H "Content-Type: application/json" \
  -d '{"username":"bob","password":"bob123"}' | jq -r .accessToken)
ADMIN_TOKEN=$(curl -s -X POST "$BASE/api/v1/auth/token" -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin123"}' | jq -r .accessToken)
```

## 2. Orders: flujo feliz

```bash
# Crear pedido (10 unidades -> descuento por volumen) -> 201
curl -X POST "$BASE/api/v1/orders" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"items":[{"sku":"MOUSE-01","quantity":8},{"sku":"MONITOR-27","quantity":2}]}'

ORDER_ID=<id devuelto>

# Consultar pedido -> 200
curl "$BASE/api/v1/orders/$ORDER_ID" \
  -H "Authorization: Bearer $TOKEN"

# Listar mis pedidos -> 200
curl "$BASE/api/v1/orders" \
  -H "Authorization: Bearer $TOKEN"

# Pagar pedido -> 200 (status PAID)
curl -X POST "$BASE/api/v1/orders/$ORDER_ID/payment" \
  -H "Authorization: Bearer $TOKEN"

# Crear pedido para cancelar -> 201
curl -X POST "$BASE/api/v1/orders" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"items":[{"sku":"KEYBOARD-01","quantity":1}]}'

CANCEL_ORDER_ID=<id devuelto>

# Cancelar pedido -> 200 (status CANCELLED)
curl -X POST "$BASE/api/v1/orders/$CANCEL_ORDER_ID/cancellation" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"reason":"El cliente cambió de opinión"}'
```

## 3. Orders: errores

```bash
# Pagar pedido ya pagado -> 409
curl -X POST "$BASE/api/v1/orders/$ORDER_ID/payment" \
  -H "Authorization: Bearer $TOKEN"

# Cancelar pedido pagado -> 409
curl -X POST "$BASE/api/v1/orders/$ORDER_ID/cancellation" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"reason":"Demasiado tarde"}'

# Pagar pedido cancelado -> 409
curl -X POST "$BASE/api/v1/orders/$CANCEL_ORDER_ID/payment" \
  -H "Authorization: Bearer $TOKEN"

# Crear pedido de alto valor (5 x 1200 = 6000 USD) -> 201
curl -X POST "$BASE/api/v1/orders" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"items":[{"sku":"LAPTOP-14","quantity":5}]}'

BIG_ORDER_ID=<id devuelto>

# Pagar pedido de alto valor -> 422 (la pasarela simulada lo rechaza)
curl -X POST "$BASE/api/v1/orders/$BIG_ORDER_ID/payment" \
  -H "Authorization: Bearer $TOKEN"

# Pedido ajeno: bob consulta el pedido de alice -> 404
curl "$BASE/api/v1/orders/$ORDER_ID" \
  -H "Authorization: Bearer $BOB_TOKEN"

# Pedido inexistente -> 404
curl "$BASE/api/v1/orders/00000000-0000-0000-0000-000000000000" \
  -H "Authorization: Bearer $TOKEN"

# Validación de entrada -> 400 con detalle por campo
curl -X POST "$BASE/api/v1/orders" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"items":[{"sku":"bad sku!","quantity":0}]}'

# SKU inexistente -> 422
curl -X POST "$BASE/api/v1/orders" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"items":[{"sku":"NOT-EXISTS","quantity":1}]}'

# Sin token -> 401
curl "$BASE/api/v1/orders"
```

## 4. Auditoría (solo ADMIN)

```bash
# Traza de auditoría (Kafka -> DynamoDB) -> 200
curl "$BASE/api/v1/orders/$ORDER_ID/audit" \
  -H "Authorization: Bearer $ADMIN_TOKEN"

# Auditoría como USER -> 403
curl "$BASE/api/v1/orders/$ORDER_ID/audit" \
  -H "Authorization: Bearer $TOKEN"
```

## 5. Actuator

```bash
curl "$BASE/actuator/health"
curl "$BASE/actuator/health/liveness"
curl "$BASE/actuator/health/readiness"
curl "$BASE/actuator/info"
curl "$BASE/actuator/prometheus"

# Solo ADMIN
curl "$BASE/actuator/circuitbreakers" -H "Authorization: Bearer $ADMIN_TOKEN"
curl "$BASE/actuator/retries"         -H "Authorization: Bearer $ADMIN_TOKEN"
curl "$BASE/actuator/metrics"         -H "Authorization: Bearer $ADMIN_TOKEN"
```

## 6. Pasarela simulada (WireMock, puerto 8089)

```bash
# Cargo aprobado
curl -X POST "$PAY/v1/charges" \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: 11111111-1111-1111-1111-111111111111" \
  -d '{"orderId":"11111111-1111-1111-1111-111111111111","amount":100.00,"currency":"USD"}'

# Cargo rechazado (amount > 5000)
curl -X POST "$PAY/v1/charges" \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: 22222222-2222-2222-2222-222222222222" \
  -d '{"orderId":"22222222-2222-2222-2222-222222222222","amount":6000.00,"currency":"USD"}'

# Stubs cargados y peticiones recibidas (API admin de WireMock)
curl "$PAY/__admin/mappings"
curl "$PAY/__admin/requests"
```
