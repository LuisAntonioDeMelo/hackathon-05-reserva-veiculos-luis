resource "aws_lambda_function" "service" {
  for_each = local.lambda_definitions

  function_name    = "${var.project_name}-${each.key}"
  role             = aws_iam_role.lambda_exec.arn
  handler          = each.value.handler
  runtime          = "java17"
  filename         = var.lambda_artifact_path
  source_code_hash = filebase64sha256(var.lambda_artifact_path)
  memory_size      = var.lambda_memory_mb
  timeout          = var.lambda_timeout_seconds

  tracing_config {
    mode = "Active"
  }

  environment {
    variables = merge(local.common_lambda_environment, try(each.value.environment, {}))
  }

  depends_on = [aws_iam_role_policy.lambda_exec]
}

resource "aws_cloudwatch_log_group" "lambda" {
  for_each = aws_lambda_function.service

  name              = "/aws/lambda/${each.value.function_name}"
  retention_in_days = var.log_retention_days
}

resource "aws_apigatewayv2_api" "vehicle_api" {
  name          = "${var.project_name}-http-api"
  protocol_type = "HTTP"
}

resource "aws_apigatewayv2_stage" "default" {
  api_id      = aws_apigatewayv2_api.vehicle_api.id
  name        = "$default"
  auto_deploy = true
}

resource "aws_apigatewayv2_integration" "lambda" {
  for_each = local.api_routes

  api_id                 = aws_apigatewayv2_api.vehicle_api.id
  integration_type       = "AWS_PROXY"
  integration_uri        = aws_lambda_function.service[each.value.function_key].invoke_arn
  integration_method     = "POST"
  payload_format_version = "2.0"
}

resource "aws_apigatewayv2_route" "route" {
  for_each = local.api_routes

  api_id    = aws_apigatewayv2_api.vehicle_api.id
  route_key = each.value.route_key
  target    = "integrations/${aws_apigatewayv2_integration.lambda[each.key].id}"
}

resource "aws_lambda_permission" "apigw_invoke" {
  for_each = local.api_routes

  statement_id  = "AllowApiGatewayInvoke-${each.key}"
  action        = "lambda:InvokeFunction"
  function_name = aws_lambda_function.service[each.value.function_key].function_name
  principal     = "apigateway.amazonaws.com"
  source_arn    = "${aws_apigatewayv2_api.vehicle_api.execution_arn}/*/*"
}
