resource "aws_cloudwatch_log_group" "purchase_saga" {
  name              = "/aws/vendedlogs/states/${local.state_machine_name}"
  retention_in_days = var.log_retention_days
}

resource "aws_sfn_state_machine" "purchase_saga" {
  name     = local.state_machine_name
  role_arn = aws_iam_role.stepfunctions_exec.arn
  type     = "STANDARD"

  definition = local.purchase_saga_definition

  logging_configuration {
    include_execution_data = true
    level                  = "ALL"
    log_destination        = "${aws_cloudwatch_log_group.purchase_saga.arn}:*"
  }

  tracing_configuration {
    enabled = true
  }

  depends_on = [aws_iam_role_policy.stepfunctions_exec]
}

resource "aws_cloudwatch_metric_alarm" "start_purchase_errors" {
  alarm_name          = "start-purchase-errors"
  namespace           = "AWS/Lambda"
  metric_name         = "Errors"
  statistic           = "Sum"
  period              = 60
  evaluation_periods  = 1
  threshold           = 1
  comparison_operator = "GreaterThanOrEqualToThreshold"
  treat_missing_data  = "notBreaching"

  dimensions = {
    FunctionName = aws_lambda_function.service["start_purchase"].function_name
  }
}

resource "aws_cloudwatch_metric_alarm" "saga_failed_executions" {
  alarm_name          = "purchase-saga-failures"
  namespace           = "AWS/States"
  metric_name         = "ExecutionsFailed"
  statistic           = "Sum"
  period              = 60
  evaluation_periods  = 1
  threshold           = 1
  comparison_operator = "GreaterThanOrEqualToThreshold"
  treat_missing_data  = "notBreaching"

  dimensions = {
    StateMachineArn = aws_sfn_state_machine.purchase_saga.arn
  }
}

resource "aws_cloudwatch_dashboard" "operations" {
  dashboard_name = "${var.project_name}-ops"
  dashboard_body = local.operations_dashboard_body
}
