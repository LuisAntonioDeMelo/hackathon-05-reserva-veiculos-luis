# Relatorio de Arquitetura AWS, Seguranca e SAGA

## 1. Objetivo
Este documento justifica as escolhas de servicos AWS da solucao, define os controles de seguranca aplicados/recomendados e registra o modelo de orquestracao SAGA adotado para o processo de compra de veiculos.

Escopo da solucao:
- API HTTP para cadastro/consulta de veiculos, clientes, vendas e reservas.
- Processamento de compra com SAGA e compensacao.
- Persistencia em DynamoDB e notificacao assicrona via SQS.

## 2. Justificativa dos servicos AWS utilizados

| Servico | Uso na solucao | Justificativa tecnica |
|---|---|---|
| Amazon API Gateway (HTTP API) | Exposicao dos endpoints REST | Camada de entrada gerenciada, escalavel e sem gerenciamento de servidor. Facilita roteamento por dominio e integracao nativa com Lambda. |
| AWS Lambda | Funcoes de dominio e funcoes da SAGA | Modelo serverless adequado para carga variavel e eventos curtos. Reduz operacao de infraestrutura e permite evolucao por funcao. |
| AWS Step Functions | Orquestracao da SAGA de compra | Necessario para fluxo transacional distribuido com estado, retries, wait e compensacao explicita (`CancelSale`). |
| Amazon DynamoDB | Persistencia (`Vehicles`, `Clients`, `Sales`, `Reservations`) | Banco NoSQL gerenciado com alta disponibilidade, baixa latencia e escalabilidade automatica. Adequado para acesso por chave e GSI do dominio. |
| Amazon SQS | Notificacao de venda concluida | Desacopla a notificacao do caminho critico da compra, aumentando resiliencia e absorcao de picos. |
| Amazon CloudWatch (Logs, Alarms, Dashboard) | Logs, metricas e alertas operacionais | Fornece observabilidade centralizada, deteccao de falhas e acompanhamento de saude da aplicacao. |
| AWS X-Ray | Tracing distribuido | Permite rastrear chamadas entre API, Lambda e Step Functions para diagnostico de latencia/erros. |

Referencia visual:
- [aws-architecture-diagram.md](E:\Dev\Hackaton-projeto-5\docs\aws-architecture-diagram.md)

## 3. Servicos de seguranca da nuvem e justificativa de uso

### 3.1 Controles ja implementados no projeto
- IAM Roles e Policies para Lambda e Step Functions.
  - Motivo: controle de autorizacao entre componentes e base para menor privilegio.
- Criptografia em repouso do DynamoDB (SSE habilitado).
  - Motivo: reduzir risco de exposicao de dados em armazenamento.
- Criptografia de dados sensiveis na aplicacao (AES-256/GCM) + hash SHA-256.
  - Motivo: proteger PII mesmo em caso de leitura indevida da base.
- CloudWatch + X-Ray.
  - Motivo: deteccao de anomalias e rastreabilidade operacional.

### 3.2 Servicos de seguranca recomendados para producao AWS
- AWS KMS (CMK dedicada).
  - Motivo: controle central de chaves, rotacao e trilha de uso criptografico.
- AWS Secrets Manager.
  - Motivo: armazenamento seguro e rotacao da `CLIENT_DATA_ENCRYPTION_KEY` e segredos de integracao.
- AWS WAF no API Gateway.
  - Motivo: mitigacao de ataques de camada 7 (injecao, bots, abuso de requests).
- Amazon Cognito + JWT Authorizer no API Gateway.
  - Motivo: autenticacao/autorizacao de clientes e eliminacao de endpoints publicos anonimos.
- AWS CloudTrail.
  - Motivo: auditoria de chamadas de API e investigacao forense.
- Amazon GuardDuty + AWS Security Hub.
  - Motivo: deteccao de ameacas e consolidacao de postura de seguranca.
- AWS Config.
  - Motivo: governanca continua e deteccao de drift de configuracao de seguranca.
- AWS Shield Standard (nativo) e, se necessario, Shield Advanced.
  - Motivo: protecao contra DDoS na borda.

## 4. Relatorio de seguranca de dados

### 4.1 Dados armazenados pela solucao
- `VehiclesTable`: dados de inventario e estado do veiculo.
- `ClientsTable`: dados cadastrais do cliente e campos sensiveis cifrados/hash.
- `SalesTable`: dados da venda e status de pagamento.
- `ReservationsTable`: dados da reserva e ciclo de vida da compra.

Detalhamento dos atributos:
- [dynamodb-schemas.md](E:\Dev\Hackaton-projeto-5\docs\dynamodb-schemas.md)

### 4.2 Dados sensiveis
Dados considerados sensiveis (PII/financeiro):
- Email do cliente.
- Documento do cliente (ex.: CPF).
- Chave de pagamento.
- Endereco.

Forma de protecao:
- Armazenamento cifrado no `ClientsTable` em campos `*Encrypted`.
- Hashes (`*Hash`) para comparacao sem exposicao do valor original.
- Exibicao parcial de documento (`documentNumberLast4`).
- Criptografia em repouso no DynamoDB.

### 4.3 Politicas de acesso a dados implementadas
- Lambdas usam role de execucao dedicada (`lambda_exec`).
- Permissoes de dados concedidas por policy IAM para DynamoDB (CRUD + Query/Scan) nas tabelas do dominio.
- Permissao de iniciar a state machine (`states:StartExecution`) para fluxo de compra.
- Step Functions usa role propria (`stepfunctions_exec`) com:
  - `lambda:InvokeFunction` apenas nas lambdas da SAGA.
  - `sqs:SendMessage` apenas na fila de notificacao.

Observacao:
- O projeto atual compartilha uma role para todas as Lambdas. Em producao, o recomendado e uma role por funcao para menor privilegio estrito.

### 4.4 Politicas de seguranca operacional implementadas
- Logs centralizados em CloudWatch para Lambda e Step Functions.
- Alarmes ativos para erro de inicio de compra e falha da SAGA.
- Dashboard operacional para metricas criticas.
- Tracing ativo em Lambda e Step Functions via X-Ray.
- DynamoDB com `point_in_time_recovery` habilitado (recuperacao de dados).

### 4.5 Riscos existentes e acoes de mitigacao

| Risco | Impacto | Mitigacao recomendada |
|---|---|---|
| Endpoints sem autenticacao forte | Acesso nao autorizado a operacoes da API | Cognito + JWT Authorizer no API Gateway; escopos por perfil. |
| Chave de criptografia em variavel de ambiente | Exposicao da chave em caso de vazamento de configuracao | Secrets Manager + KMS + rotacao automatica. |
| Role unica para todas as Lambdas | Superpermissao lateral entre funcoes | Quebrar IAM por funcao e limitar recursos/acoes por dominio. |
| `states:StartExecution` com recurso amplo | Inicio indevido de outras state machines | Restringir permissao ao ARN especifico da state machine de compra. |
| Logs de execucao com dados de negocio | Exposicao indireta de dados sensiveis | Mascaramento/redacao em logs, reduzir payload logado e ajustar retention por criticidade. |
| Sem trilha de auditoria completa no ambiente alvo | Dificuldade de investigacao de incidente | Habilitar CloudTrail organizacional + envio para conta de seguranca. |
| Sem camada de protecao L7 dedicada | Maior risco de abuso/ataques HTTP | Ativar AWS WAF com regras gerenciadas e rate limiting. |

## 5. Relatorio de orquestracao SAGA

### 5.1 Tipo de SAGA recomendado
SAGA por **orquestracao centralizada** com AWS Step Functions (modelo ja adotado no projeto).

### 5.2 Justificativa da escolha
- Fluxo de compra possui etapas dependentes e estado temporal (ex.: `WaitPayment` e retries), o que favorece um orquestrador explicito.
- Necessidade de compensacao deterministica (`CancelSale`) para rollback de reserva/venda.
- Observabilidade, auditoria e depuracao melhores com trilha unica de execucao.
- Menor acoplamento temporal entre servicos do dominio, mantendo consistencia eventual controlada.

### 5.3 Fluxo resumido da SAGA implementada
1. `ValidateClient`
2. `ReserveVehicle`
3. `GeneratePaymentCode`
4. `WaitPayment`
5. `CheckPaymentStatus` (com retry)
6. `CompleteSale`
7. `NotifySale` (SQS)

Compensacao:
- `CancelSale` para desfazer reserva e atualizar status de venda/veiculo quando houver falha, timeout ou cancelamento.

### 5.4 Por que nao usar coreografia neste caso
- A coreografia aumentaria a dispersao de regras de compensacao e de timeout entre varios consumidores/eventos.
- Para este dominio (compra com dependencia sequencial forte), a orquestracao reduz complexidade operacional e risco de comportamento inconsistente.

## 6. Conclusao
A arquitetura escolhida e aderente ao objetivo serverless, com SAGA orquestrada para consistencia e mecanismos de seguranca de base ja aplicados. Para producao, o reforco principal e completar os controles gerenciados de seguranca (KMS, Secrets Manager, Cognito, WAF, CloudTrail, GuardDuty/Security Hub e IAM por funcao), elevando a maturidade de governanca e resposta a incidentes.


