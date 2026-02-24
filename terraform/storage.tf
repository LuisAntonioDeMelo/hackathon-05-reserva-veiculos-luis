resource "aws_dynamodb_table" "vehicles" {
  name         = "${var.project_name}-vehicles"
  billing_mode = "PAY_PER_REQUEST"
  hash_key     = "vehicleId"

  attribute {
    name = "vehicleId"
    type = "S"
  }

  attribute {
    name = "status"
    type = "S"
  }

  attribute {
    name = "price"
    type = "N"
  }

  global_secondary_index {
    name            = "status-price-index"
    hash_key        = "status"
    range_key       = "price"
    projection_type = "ALL"
  }

  point_in_time_recovery {
    enabled = true
  }

  server_side_encryption {
    enabled = true
  }
}

resource "aws_dynamodb_table" "clients" {
  name         = "${var.project_name}-clients"
  billing_mode = "PAY_PER_REQUEST"
  hash_key     = "clientId"

  attribute {
    name = "clientId"
    type = "S"
  }

  point_in_time_recovery {
    enabled = true
  }

  server_side_encryption {
    enabled = true
  }
}

resource "aws_dynamodb_table" "sales" {
  name         = "${var.project_name}-sales"
  billing_mode = "PAY_PER_REQUEST"
  hash_key     = "saleId"

  attribute {
    name = "saleId"
    type = "S"
  }

  point_in_time_recovery {
    enabled = true
  }

  server_side_encryption {
    enabled = true
  }
}

resource "aws_dynamodb_table" "reservations" {
  name         = "${var.project_name}-reservations"
  billing_mode = "PAY_PER_REQUEST"
  hash_key     = "reservationId"

  attribute {
    name = "reservationId"
    type = "S"
  }

  attribute {
    name = "status"
    type = "S"
  }

  attribute {
    name = "reservedAt"
    type = "S"
  }

  global_secondary_index {
    name            = "status-reservedAt-index"
    hash_key        = "status"
    range_key       = "reservedAt"
    projection_type = "ALL"
  }

  point_in_time_recovery {
    enabled = true
  }

  server_side_encryption {
    enabled = true
  }
}

resource "aws_sqs_queue" "sales_notifications" {
  name                       = "${var.project_name}-sales-notifications"
  visibility_timeout_seconds = 60
}
