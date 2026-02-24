locals {
  state_machine_name = "${var.project_name}-purchase-saga"
  state_machine_arn  = "arn:aws:states:${var.aws_region}:000000000000:stateMachine:${local.state_machine_name}"

  common_lambda_environment = {
    VEHICLES_TABLE        = aws_dynamodb_table.vehicles.name
    CLIENTS_TABLE         = aws_dynamodb_table.clients.name
    BUYERS_TABLE          = aws_dynamodb_table.clients.name
    SALES_TABLE           = aws_dynamodb_table.sales.name
    RESERVATIONS_TABLE    = aws_dynamodb_table.reservations.name
    AWS_ENDPOINT_OVERRIDE = var.aws_endpoint_override
    SALES_QUEUE_URL       = aws_sqs_queue.sales_notifications.id
    STATUS_PRICE_INDEX    = "status-price-index"
  }

  lambda_definitions = {
    create_vehicle = {
      handler = "hackthon.fiap.luis.handlers.CreateVehicleHandler::handleRequest"
    }
    update_vehicle = {
      handler = "hackthon.fiap.luis.handlers.UpdateVehicleHandler::handleRequest"
    }
    list_vehicles_for_sale = {
      handler = "hackthon.fiap.luis.handlers.ListVehiclesForSaleHandler::handleRequest"
    }
    list_sold_vehicles = {
      handler = "hackthon.fiap.luis.handlers.ListSoldVehiclesHandler::handleRequest"
    }
    create_client = {
      handler = "hackthon.fiap.luis.handlers.CreateClientHandler::handleRequest"
    }
    start_purchase = {
      handler = "hackthon.fiap.luis.handlers.StartPurchaseSagaHandler::handleRequest"
      environment = {
        STATE_MACHINE_ARN = local.state_machine_arn
      }
    }
    get_sale = {
      handler = "hackthon.fiap.luis.handlers.GetSaleHandler::handleRequest"
    }
    get_reservation = {
      handler = "hackthon.fiap.luis.handlers.GetReservationHandler::handleRequest"
    }
    payment_callback = {
      handler = "hackthon.fiap.luis.handlers.PaymentCallbackHandler::handleRequest"
    }
    validate_client = {
      handler = "hackthon.fiap.luis.saga.ValidateClientHandler::handleRequest"
    }
    reserve_vehicle = {
      handler = "hackthon.fiap.luis.saga.ReserveVehicleHandler::handleRequest"
    }
    generate_payment_code = {
      handler = "hackthon.fiap.luis.saga.GeneratePaymentCodeHandler::handleRequest"
    }
    check_payment_status = {
      handler = "hackthon.fiap.luis.saga.CheckPaymentStatusHandler::handleRequest"
    }
    complete_sale = {
      handler = "hackthon.fiap.luis.saga.CompleteSaleHandler::handleRequest"
    }
    cancel_sale = {
      handler = "hackthon.fiap.luis.saga.CancelSaleHandler::handleRequest"
    }
  }

  api_routes = {
    create_vehicle = {
      route_key    = "POST /vehicles"
      function_key = "create_vehicle"
    }
    update_vehicle = {
      route_key    = "PUT /vehicles/{vehicleId}"
      function_key = "update_vehicle"
    }
    list_vehicles_for_sale = {
      route_key    = "GET /vehicles/for-sale"
      function_key = "list_vehicles_for_sale"
    }
    list_sold_vehicles = {
      route_key    = "GET /vehicles/sold"
      function_key = "list_sold_vehicles"
    }
    create_client = {
      route_key    = "POST /clients"
      function_key = "create_client"
    }
    create_buyer_legacy = {
      route_key    = "POST /buyers"
      function_key = "create_client"
    }
    start_purchase = {
      route_key    = "POST /sales"
      function_key = "start_purchase"
    }
    get_sale = {
      route_key    = "GET /sales/{saleId}"
      function_key = "get_sale"
    }
    get_reservation = {
      route_key    = "GET /reservations/{reservationId}"
      function_key = "get_reservation"
    }
    payment_callback = {
      route_key    = "POST /payments/callback"
      function_key = "payment_callback"
    }
  }

  purchase_saga_definition = jsonencode({
    StartAt = "ValidateClient"
    States = {
      ValidateClient = {
        Type     = "Task"
        Resource = aws_lambda_function.service["validate_client"].arn
        Next     = "CheckCancelBeforeReserve"
        Catch = [
          {
            ErrorEquals = ["States.ALL"]
            ResultPath  = "$.error"
            Next        = "CancelSale"
          }
        ]
      }
      CheckCancelBeforeReserve = {
        Type = "Choice"
        Choices = [
          {
            Variable      = "$.customerCancelled"
            BooleanEquals = true
            Next          = "CancelSale"
          }
        ]
        Default = "ReserveVehicle"
      }
      ReserveVehicle = {
        Type     = "Task"
        Resource = aws_lambda_function.service["reserve_vehicle"].arn
        Next     = "GeneratePaymentCode"
        Catch = [
          {
            ErrorEquals = ["States.ALL"]
            ResultPath  = "$.error"
            Next        = "CancelSale"
          }
        ]
      }
      GeneratePaymentCode = {
        Type     = "Task"
        Resource = aws_lambda_function.service["generate_payment_code"].arn
        Next     = "CheckCancelBeforePayment"
        Catch = [
          {
            ErrorEquals = ["States.ALL"]
            ResultPath  = "$.error"
            Next        = "CancelSale"
          }
        ]
      }
      CheckCancelBeforePayment = {
        Type = "Choice"
        Choices = [
          {
            Variable      = "$.customerCancelled"
            BooleanEquals = true
            Next          = "CancelSale"
          }
        ]
        Default = "WaitPayment"
      }
      WaitPayment = {
        Type    = "Wait"
        Seconds = 5
        Next    = "CheckPaymentStatus"
      }
      CheckPaymentStatus = {
        Type     = "Task"
        Resource = aws_lambda_function.service["check_payment_status"].arn
        Next     = "PaymentDecision"
        Catch = [
          {
            ErrorEquals = ["States.ALL"]
            ResultPath  = "$.error"
            Next        = "CancelSale"
          }
        ]
      }
      PaymentDecision = {
        Type = "Choice"
        Choices = [
          {
            Variable     = "$.paymentStatus"
            StringEquals = "PAID"
            Next         = "CompleteSale"
          },
          {
            Variable      = "$.shouldRetry"
            BooleanEquals = true
            Next          = "WaitPayment"
          }
        ]
        Default = "CancelSale"
      }
      CompleteSale = {
        Type     = "Task"
        Resource = aws_lambda_function.service["complete_sale"].arn
        Next     = "NotifySale"
        Catch = [
          {
            ErrorEquals = ["States.ALL"]
            ResultPath  = "$.error"
            Next        = "CancelSale"
          }
        ]
      }
      NotifySale = {
        Type     = "Task"
        Resource = "arn:aws:states:::sqs:sendMessage"
        Parameters = {
          QueueUrl = aws_sqs_queue.sales_notifications.id
          MessageBody = {
            "saleId.$"        = "$.saleId"
            "reservationId.$" = "$.reservationId"
            "vehicleId.$"     = "$.vehicleId"
            "clientId.$"      = "$.clientId"
            "totalPrice.$"    = "$.totalPrice"
            "paymentCode.$"   = "$.paymentCode"
            status            = "COMPLETED"
          }
        }
        End = true
      }
      CancelSale = {
        Type     = "Task"
        Resource = aws_lambda_function.service["cancel_sale"].arn
        End      = true
      }
    }
  })

  operations_dashboard_body = jsonencode({
    widgets = [
      {
        type   = "metric"
        x      = 0
        y      = 0
        width  = 12
        height = 6
        properties = {
          title   = "Lambda Errors"
          view    = "timeSeries"
          stacked = false
          metrics = [
            ["AWS/Lambda", "Errors", "FunctionName", aws_lambda_function.service["create_vehicle"].function_name],
            ["AWS/Lambda", "Errors", "FunctionName", aws_lambda_function.service["update_vehicle"].function_name],
            ["AWS/Lambda", "Errors", "FunctionName", aws_lambda_function.service["start_purchase"].function_name]
          ]
          period = 60
          stat   = "Sum"
          region = var.aws_region
        }
      },
      {
        type   = "metric"
        x      = 12
        y      = 0
        width  = 12
        height = 6
        properties = {
          title   = "Step Functions Executions"
          view    = "timeSeries"
          stacked = false
          metrics = [
            ["AWS/States", "ExecutionsStarted", "StateMachineArn", aws_sfn_state_machine.purchase_saga.arn],
            [".", "ExecutionsSucceeded", ".", "."],
            [".", "ExecutionsFailed", ".", "."]
          ]
          period = 60
          stat   = "Sum"
          region = var.aws_region
        }
      }
    ]
  })
}
