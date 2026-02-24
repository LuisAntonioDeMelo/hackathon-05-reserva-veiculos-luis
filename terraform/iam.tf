data "aws_iam_policy_document" "lambda_assume_role" {
  statement {
    actions = ["sts:AssumeRole"]

    principals {
      type        = "Service"
      identifiers = ["lambda.amazonaws.com"]
    }
  }
}

resource "aws_iam_role" "lambda_exec" {
  name               = "${var.project_name}-lambda-exec-role"
  assume_role_policy = data.aws_iam_policy_document.lambda_assume_role.json
}

data "aws_iam_policy_document" "lambda_exec" {
  statement {
    sid = "CloudWatchLogs"
    actions = [
      "logs:CreateLogGroup",
      "logs:CreateLogStream",
      "logs:PutLogEvents"
    ]
    resources = ["arn:aws:logs:*:*:*"]
  }

  statement {
    sid = "XRayWrite"
    actions = [
      "xray:PutTraceSegments",
      "xray:PutTelemetryRecords"
    ]
    resources = ["*"]
  }

  statement {
    sid = "DynamoCrud"
    actions = [
      "dynamodb:GetItem",
      "dynamodb:PutItem",
      "dynamodb:UpdateItem",
      "dynamodb:DeleteItem",
      "dynamodb:Query",
      "dynamodb:Scan",
      "dynamodb:ConditionCheckItem"
    ]
    resources = [
      aws_dynamodb_table.vehicles.arn,
      "${aws_dynamodb_table.vehicles.arn}/index/*",
      aws_dynamodb_table.clients.arn,
      aws_dynamodb_table.sales.arn,
      aws_dynamodb_table.reservations.arn,
      "${aws_dynamodb_table.reservations.arn}/index/*"
    ]
  }

  statement {
    sid = "StartStepFunctionsExecution"
    actions = [
      "states:StartExecution"
    ]
    resources = ["*"]
  }
}

resource "aws_iam_role_policy" "lambda_exec" {
  name   = "${var.project_name}-lambda-exec-policy"
  role   = aws_iam_role.lambda_exec.id
  policy = data.aws_iam_policy_document.lambda_exec.json
}

data "aws_iam_policy_document" "stepfunctions_assume_role" {
  statement {
    actions = ["sts:AssumeRole"]

    principals {
      type        = "Service"
      identifiers = ["states.amazonaws.com"]
    }
  }
}

resource "aws_iam_role" "stepfunctions_exec" {
  name               = "${var.project_name}-stepfunctions-exec-role"
  assume_role_policy = data.aws_iam_policy_document.stepfunctions_assume_role.json
}

data "aws_iam_policy_document" "stepfunctions_exec" {
  statement {
    sid = "InvokeSagaLambdas"
    actions = [
      "lambda:InvokeFunction"
    ]
    resources = [
      aws_lambda_function.service["validate_client"].arn,
      aws_lambda_function.service["reserve_vehicle"].arn,
      aws_lambda_function.service["generate_payment_code"].arn,
      aws_lambda_function.service["check_payment_status"].arn,
      aws_lambda_function.service["complete_sale"].arn,
      aws_lambda_function.service["cancel_sale"].arn
    ]
  }

  statement {
    sid = "NotifySqs"
    actions = [
      "sqs:SendMessage"
    ]
    resources = [aws_sqs_queue.sales_notifications.arn]
  }

  statement {
    sid = "CloudWatchLogsDelivery"
    actions = [
      "logs:CreateLogDelivery",
      "logs:GetLogDelivery",
      "logs:UpdateLogDelivery",
      "logs:DeleteLogDelivery",
      "logs:ListLogDeliveries",
      "logs:PutResourcePolicy",
      "logs:DescribeResourcePolicies",
      "logs:DescribeLogGroups"
    ]
    resources = ["*"]
  }
}

resource "aws_iam_role_policy" "stepfunctions_exec" {
  name   = "${var.project_name}-stepfunctions-exec-policy"
  role   = aws_iam_role.stepfunctions_exec.id
  policy = data.aws_iam_policy_document.stepfunctions_exec.json
}
