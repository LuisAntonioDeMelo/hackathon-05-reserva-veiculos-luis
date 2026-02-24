provider "aws" {
  region                      = var.aws_region
  access_key                  = "test"
  secret_key                  = "test"
  s3_use_path_style           = true
  skip_credentials_validation = true
  skip_metadata_api_check     = true
  skip_requesting_account_id  = true

  endpoints {
    apigateway   = var.localstack_endpoint
    apigatewayv2 = var.localstack_endpoint
    cloudwatch   = var.localstack_endpoint
    dynamodb     = var.localstack_endpoint
    iam          = var.localstack_endpoint
    lambda       = var.localstack_endpoint
    logs         = var.localstack_endpoint
    s3           = var.localstack_endpoint
    sfn          = var.localstack_endpoint
    sqs          = var.localstack_endpoint
    sts          = var.localstack_endpoint
    xray         = var.localstack_endpoint
  }
}
