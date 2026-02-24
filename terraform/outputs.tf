output "api_endpoint" {
  description = "HTTP API invoke URL"
  value       = aws_apigatewayv2_stage.default.invoke_url
}

output "localstack_api_endpoint" {
  description = "HTTP API endpoint using LocalStack URL pattern"
  value       = "${var.localstack_endpoint}/restapis/${aws_apigatewayv2_api.vehicle_api.id}/$default/_user_request_"
}

output "state_machine_arn" {
  description = "Purchase saga state machine ARN"
  value       = aws_sfn_state_machine.purchase_saga.arn
}

output "sales_queue_url" {
  description = "SQS URL used for completed-sale notifications"
  value       = aws_sqs_queue.sales_notifications.id
}

output "reservations_table_name" {
  description = "Reservations table name"
  value       = aws_dynamodb_table.reservations.name
}
