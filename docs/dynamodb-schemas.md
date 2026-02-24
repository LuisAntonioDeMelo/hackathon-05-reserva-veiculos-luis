# DynamoDB Schemas

## VehiclesTable
**PK**
- `vehicleId` (S)

**Atributos**
- `brand` (S)
- `model` (S)
- `year` (N)
- `color` (S)
- `price` (N)
- `status` (S) -> `AVAILABLE | RESERVED | SOLD`
- `reservationId` (S, opcional)
- `saleId` (S, opcional)
- `clientId` (S, opcional)
- `reservedAt` (S, opcional ISO-8601)
- `soldAt` (S, opcional ISO-8601)
- `createdAt` (S, ISO-8601)
- `updatedAt` (S, ISO-8601)

**GSI**
- `status-price-index`
  - HASH: `status` (S)
  - RANGE: `price` (N)

## ClientsTable
**PK**
- `clientId` (S)

**Atributos**
- `fullName` (S)
- `email` (S)
- `documentNumber` (S)
- `paymentKey` (S)
- `address` (S)
- `status` (S) -> `ACTIVE | INACTIVE`
- `createdAt` (S, ISO-8601)

## ReservationsTable
**PK**
- `reservationId` (S)

**Atributos**
- `saleId` (S)
- `vehicleId` (S)
- `clientId` (S)
- `status` (S) -> `RESERVED | AWAITING_PAYMENT | CONFIRMED | CANCELLED`
- `paymentCode` (S, opcional)
- `reservedAt` (S, ISO-8601)
- `expiresAt` (S, ISO-8601)
- `confirmedAt` (S, opcional ISO-8601)
- `cancelledAt` (S, opcional ISO-8601)
- `cancelReason` (S, opcional)
- `updatedAt` (S, ISO-8601)

**GSI**
- `status-reservedAt-index`
  - HASH: `status` (S)
  - RANGE: `reservedAt` (S)

## SalesTable
**PK**
- `saleId` (S)

**Atributos**
- `reservationId` (S)
- `vehicleId` (S)
- `clientId` (S)
- `paymentCode` (S)
- `paymentStatus` (S) -> `PENDING | PAID | FAILED | CANCELLED`
- `status` (S) -> `RESERVED | PAID | COMPLETED | PAYMENT_TIMEOUT | PAYMENT_FAILED | CANCELLED`
- `totalPrice` (N)
- `providerReference` (S, opcional)
- `createdAt` (S, ISO-8601)
- `updatedAt` (S, ISO-8601)
- `paidAt` (S, opcional ISO-8601)
- `completedAt` (S, opcional ISO-8601)
- `cancelReason` (S, opcional)
