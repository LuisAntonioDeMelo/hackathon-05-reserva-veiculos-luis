# Plataforma de Revenda de Veiculos - Serverless + SAGA

## Visao geral
Implementacao alinhada ao desenho de arquitetura:
- API na internet via API Gateway
- Servicos separados por dominio (`Vehicle`, `Buyer/Client`, `Purchase`)
- Orquestracao de compra com `Step Functions` em padrao `SAGA`
- Reserva explicita de veiculo em tabela dedicada (`ReservationsTable`)
- Integracao de pagamento mock/callback
- Notificacao de venda concluida via `SQS`
- Observabilidade com CloudWatch Logs, Alarms, Dashboard e X-Ray
- Protecao de dados sensiveis de cliente com criptografia AES-256 (app-layer) + hash

## Arquitetura implementada
- `VehicleApi` (`AWS::Serverless::HttpApi`)
- Lambdas por dominio:
  - Vehicle Service: cadastro/edicao/listagem de veiculos
  - Buyer/Client Service: cadastro de clientes
  - Purchase Service: inicio da compra, callback de pagamento e consulta de reserva/venda
- `Step Functions` (`vehicle-purchase-saga`) com compensacoes
- DynamoDB:
  - `VehiclesTable`
  - `ClientsTable` (buyer/client)
  - `SalesTable`
  - `ReservationsTable` (reserva explicita)
- `SQS`: `vehicle-sales-notifications`
- `CloudWatch`:
  - Logs
  - Dashboard `vehicle-platform-ops`
  - Alarmes:
    - `start-purchase-errors`
    - `purchase-saga-failures`
- `X-Ray` habilitado (Tracing ativo nas Functions e State Machine)
- Controles transversais do desenho (para ambiente AWS real):
  - `CloudTrail` (auditoria)
  - `GuardDuty` / `Security Hub` (postura de seguranca)
  - `KMS` + `Secrets Manager` (protecao de dados e segredos)
  - Observacao: esses itens nao sao provisionados no LocalStack desta entrega.

## Fluxo SAGA (compra)
State machine: `vehicle-purchase-saga`

Etapas:
1. `ValidateClient`
2. `ReserveVehicle` (grava reserva em `ReservationsTable`)
3. `GeneratePaymentCode`
4. `WaitPayment` (wait)
5. `CheckPaymentStatus` (check + retry loop)
6. `CompleteSale` (caminho feliz)
7. `NotifySale` (SQS)

Compensacao:
- `CancelSale` libera veiculo, cancela reserva e marca venda como cancelada.

## Endpoints
Base URL LocalStack:
`http://localhost:4566/restapis/{apiId}/$default/_user_request_`

### Veiculos
- `POST /vehicles`
- `PUT /vehicles/{vehicleId}`
- `GET /vehicles/for-sale` (ordenado por preco crescente)
- `GET /vehicles/sold` (ordenado por preco crescente)

### Clientes (Buyer)
- `POST /clients` (principal)
- `POST /buyers` (legado/compatibilidade)

### Compra / Reserva / Pagamento
- `POST /sales` inicia saga de compra
- `GET /sales/{saleId}` consulta venda
- `GET /reservations/{reservationId}` consulta reserva
- `POST /payments/callback` callback do gateway de pagamento

Exemplo `POST /sales`:
```json
{
  "vehicleId": "uuid-veiculo",
  "clientId": "uuid-cliente",
  "paymentApproved": true,
  "reservationTtlMinutes": 15,
  "maxPaymentChecks": 6
}
```

Exemplo `POST /payments/callback`:
```json
{
  "saleId": "uuid-venda",
  "paymentStatus": "PAID",
  "providerReference": "tx-123"
}
```

## Schemas DynamoDB
Schemas completos: [docs/dynamodb-schemas.md](E:\Dev\Hackaton-projeto-5\docs\dynamodb-schemas.md)
Curls dos endpoints: [docs/curl-endpoints.md](E:\Dev\Hackaton-projeto-5\docs\curl-endpoints.md)
Controles de seguranca para dados sensiveis: [docs/security-sensitive-data.md](E:\Dev\Hackaton-projeto-5\docs\security-sensitive-data.md)

## Deploy local (automatico) - SAM/CloudFormation
Pre-reqs:
- Docker + Docker Compose
- AWS CLI v2
- Java 17
- Maven opcional (script usa container Maven se `mvn` nao existir)

Comando:
```powershell
.\deploy-localstack.ps1
```

O script:
1. Sobe/verifica LocalStack
2. Build Java
3. Faz upload do `function.jar` para S3 local
4. Gera `template.packaged.yaml`
5. Executa `cloudformation deploy`
6. Exibe outputs da stack

## Deploy local (automatico) - Terraform
Pre-reqs:
- Docker + Docker Compose
- Java 17
- Maven opcional (script usa container Maven se `mvn` nao existir)
- Terraform

Comando:
```powershell
.\deploy-terraform-localstack.ps1
```

Opcao Bash (Linux/macOS/Git Bash):
```bash
./deploy.sh
```

Destroy:
```powershell
.\deploy-terraform-localstack.ps1 -Destroy
```

Destroy (Bash):
```bash
./deploy.sh --destroy
```

Detalhes da arquitetura Terraform:
- [docs/terraform-architecture.md](E:\Dev\Hackaton-projeto-5\docs\terraform-architecture.md)

## CI/CD
Pipeline de deploy Terraform no GitHub Actions:
- `.github/workflows/deploy-terraform.yml`
- Builda o `function.jar`, sobe LocalStack em service container e executa:
  - `terraform init`
  - `terraform fmt -check`
  - `terraform validate`
  - `terraform apply -auto-approve`

## Deploy.sh (Bash)
- Script unico para LocalStack:
  - `./deploy.sh` (modo `terraform`, default)
  - `./deploy.sh --mode sam` (SAM/CloudFormation)
