# API - Exemplos de Curl

Observacao:
- Os exemplos que capturam IDs usam `jq`.
- Endpoint Lambda LocalStack (direto): `http://localhost.localstack.cloud:4566/2015-03-31/functions/`

## 0) Lambda API do LocalStack (direto)

### Listar funcoes Lambda
```bash
curl -sS "http://localhost.localstack.cloud:4566/2015-03-31/functions/" | jq
```

### Invocar Lambda diretamente (recomendado: AWS CLI)
```bash
PAYLOAD='{"body":"{\"brand\":\"Toyota\",\"model\":\"Corolla\",\"year\":2023,\"color\":\"Prata\",\"price\":85000}"}'

aws --endpoint-url=http://localhost.localstack.cloud:4566 \
  --region us-east-1 \
  lambda invoke \
  --function-name vehicle-platform-create_vehicle \
  --payload "$PAYLOAD" \
  /tmp/lambda-response.json

cat /tmp/lambda-response.json
```

## 1) Definir `BASE_URL`

### Opcao A: stack Terraform
```bash
BASE_URL="$(terraform -chdir=terraform output -raw localstack_api_endpoint)"
BASE_URL="${BASE_URL/localhost:4566/localhost.localstack.cloud:4566}"
echo "$BASE_URL"
```

### Opcao B: stack SAM/CloudFormation
```bash
BASE_URL="$(aws --endpoint-url=http://localhost.localstack.cloud:4566 cloudformation describe-stacks \
  --stack-name vehicle-platform \
  --region us-east-1 \
  --query "Stacks[0].Outputs[?OutputKey=='LocalStackApiEndpoint'].OutputValue" \
  --output text)"
BASE_URL="${BASE_URL/localhost:4566/localhost.localstack.cloud:4566}"
echo "$BASE_URL"
```

## 2) Veiculos

### Criar veiculo
```bash
VEHICLE_ID="$(curl -sS -X POST "$BASE_URL/vehicles" \
  -H "Content-Type: application/json" \
  -d '{
    "brand": "Toyota",
    "model": "Corolla",
    "year": 2023,
    "color": "Prata",
    "price": 85000
  }' | jq -r '.vehicleId')"

echo "$VEHICLE_ID"
```

### Editar veiculo
```bash
curl -sS -X PUT "$BASE_URL/vehicles/$VEHICLE_ID" \
  -H "Content-Type: application/json" \
  -d '{
    "color": "Preto",
    "price": 84500
  }'
```

### Listar veiculos a venda (preco asc)
```bash
curl -sS "$BASE_URL/vehicles/for-sale"
```

### Listar veiculos vendidos (preco asc)
```bash
curl -sS "$BASE_URL/vehicles/sold"
```

## 3) Clientes

### Criar cliente (endpoint principal)
```bash
CLIENT_ID="$(curl -sS -X POST "$BASE_URL/clients" \
  -H "Content-Type: application/json" \
  -d '{
    "fullName": "Maria Silva",
    "email": "maria.silva@example.com",
    "documentNumber": "12345678900",
    "paymentKey": "pix-maria",
    "address": "Rua A, 100"
  }' | jq -r '.clientId')"

echo "$CLIENT_ID"
```

### Criar cliente (legado `/buyers`)
```bash
curl -sS -X POST "$BASE_URL/buyers" \
  -H "Content-Type: application/json" \
  -d '{
    "fullName": "Cliente Legado",
    "email": "legado@example.com",
    "documentNumber": "99999999999",
    "paymentKey": "pix-legado",
    "address": "Rua B, 200"
  }'
```

## 4) Compra (SAGA)

### Iniciar compra
```bash
SALE_ID="$(curl -sS -X POST "$BASE_URL/sales" \
  -H "Content-Type: application/json" \
  -d "{
    \"vehicleId\": \"$VEHICLE_ID\",
    \"clientId\": \"$CLIENT_ID\",
    \"paymentApproved\": true,
    \"reservationTtlMinutes\": 15,
    \"maxPaymentChecks\": 6
  }" | jq -r '.saleId')"

echo "$SALE_ID"
```

### Consultar venda
```bash
curl -sS "$BASE_URL/sales/$SALE_ID"
```

### Capturar `reservationId` da venda
```bash
RESERVATION_ID="$(curl -sS "$BASE_URL/sales/$SALE_ID" | jq -r '.reservationId')"
echo "$RESERVATION_ID"
```

### Consultar reserva
```bash
curl -sS "$BASE_URL/reservations/$RESERVATION_ID"
```

## 5) Callback de Pagamento

### Marcar pagamento como `PAID`
```bash
curl -sS -X POST "$BASE_URL/payments/callback" \
  -H "Content-Type: application/json" \
  -d "{
    \"saleId\": \"$SALE_ID\",
    \"paymentStatus\": \"PAID\",
    \"providerReference\": \"tx-12345\"
  }"
```

### Marcar pagamento como `FAILED`
```bash
curl -sS -X POST "$BASE_URL/payments/callback" \
  -H "Content-Type: application/json" \
  -d "{
    \"saleId\": \"$SALE_ID\",
    \"paymentStatus\": \"FAILED\",
    \"providerReference\": \"tx-erro-01\"
  }"
```
