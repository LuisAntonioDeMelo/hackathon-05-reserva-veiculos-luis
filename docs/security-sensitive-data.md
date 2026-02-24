# Seguranca para Dados Sensiveis

## Controles implementados neste projeto
- Criptografia em aplicacao para dados sensiveis de cliente:
  - `emailEncrypted`
  - `documentNumberEncrypted`
  - `paymentKeyEncrypted`
  - `addressEncrypted`
- Hashes para busca/comparacao sem expor valor original:
  - `emailHash`
  - `documentNumberHash`
  - `paymentKeyHash`
- Mascara minima para exibicao segura:
  - `documentNumberLast4`
- Criptografia em repouso no DynamoDB (`SSEEnabled: true`).
- Tracing e logs operacionais habilitados (`CloudWatch` + `X-Ray`) sem logar payload sensivel.

## Chave de criptografia
- Variavel obrigatoria nas Lambdas: `CLIENT_DATA_ENCRYPTION_KEY`.
- Formato: Base64 de 32 bytes (AES-256).
- Algoritmo usado: `AES/GCM/NoPadding`.
- Scripts de deploy geram chave aleatoria automaticamente quando a variavel nao e informada:
  - `deploy.sh`
  - `deploy-localstack.ps1`
  - `deploy-terraform-localstack.ps1`

## Recomendacoes para ambiente AWS real
- Guardar chave no `AWS Secrets Manager` e rotacionar periodicamente.
- Usar `KMS` para criptografar segredos e dados sensiveis.
- Habilitar trilhas organizacionais no `CloudTrail`.
- Ativar `Security Hub` e `GuardDuty`.
- Separar roles IAM por Lambda (menor privilegio estrito).
- Adotar WAF + rate limiting e autenticacao JWT (Cognito) na borda.
