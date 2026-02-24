# Arquitetura Terraform

## Componentes provisionados
- `aws_apigatewayv2_api` + stage `$default`
- `15` funções Lambda Java (`Vehicle`, `Client`, `Purchase` e handlers SAGA)
- `aws_sfn_state_machine` para orquestração SAGA (`reserve -> payment -> confirm/cancel`)
- `4` tabelas DynamoDB:
  - `vehicle-platform-vehicles`
  - `vehicle-platform-clients`
  - `vehicle-platform-sales`
  - `vehicle-platform-reservations`
- `aws_sqs_queue` para notificação de venda concluída
- CloudWatch:
  - log groups de Lambda
  - log group da Step Functions
  - alarmes (`start-purchase-errors`, `purchase-saga-failures`)
  - dashboard (`vehicle-platform-ops`)

## Fluxo da SAGA
1. `ValidateClient`
2. `ReserveVehicle`
3. `GeneratePaymentCode`
4. `WaitPayment`
5. `CheckPaymentStatus`
6. `CompleteSale` + `NotifySale` (SQS)

Compensação:
- `CancelSale` para rollback de reserva/venda.

## Schemas DynamoDB
Os atributos de cada entidade e índices estão em:
- [dynamodb-schemas.md](E:/Dev/Hackaton-projeto-5/docs/dynamodb-schemas.md)

## Deploy local com Terraform
Pré-requisitos:
- Docker
- Java 17
- Maven
- Terraform

Comando:
```powershell
.\deploy-terraform-localstack.ps1
```

Observacao:
- As Lambdas usam `AWS_ENDPOINT_OVERRIDE=http://localhost.localstack.cloud:4566`
  para acessar LocalStack a partir do runtime da funcao.

Destroy:
```powershell
.\deploy-terraform-localstack.ps1 -Destroy
```

## Pipeline GitHub Actions
- Workflow: `.github/workflows/deploy-terraform.yml`
- Fluxo:
  1. Build do `function.jar`
  2. Sobe LocalStack
  3. `terraform init`
  4. `terraform fmt -check`
  5. `terraform validate`
  6. `terraform apply -auto-approve`
