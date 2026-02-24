param(
    [string]$Region = "us-east-1",
    [string]$Endpoint = "http://localhost:4566",
    [string]$ComposeFile = ".\localstack\docker-compose.yaml",
    [string]$TerraformPath = ".\terraform",
    [switch]$Destroy
)

$ErrorActionPreference = "Stop"
$ProjectPath = Join-Path $PSScriptRoot "hackaton-projeto-5"

function Invoke-External {
    param(
        [Parameter(Mandatory = $true)]
        [scriptblock]$Command,
        [Parameter(Mandatory = $true)]
        [string]$ErrorMessage
    )

    & $Command
    if ($LASTEXITCODE -ne 0) {
        throw $ErrorMessage
    }
}

function Test-LocalStackHealthy {
    param([string]$HealthUrl)
    try {
        $health = Invoke-RestMethod -Uri $HealthUrl -Method Get -TimeoutSec 3
        return [bool]($health -and $health.services)
    } catch {
        return $false
    }
}

if (-not $env:AWS_ACCESS_KEY_ID) { $env:AWS_ACCESS_KEY_ID = "test" }
if (-not $env:AWS_SECRET_ACCESS_KEY) { $env:AWS_SECRET_ACCESS_KEY = "test" }
if (-not $env:AWS_DEFAULT_REGION) { $env:AWS_DEFAULT_REGION = $Region }
if (-not $env:AWS_REGION) { $env:AWS_REGION = $Region }

$healthUrl = "$Endpoint/_localstack/health"
Write-Host "1/6 - Starting LocalStack"
if (-not (Test-LocalStackHealthy -HealthUrl $healthUrl)) {
    docker compose -f $ComposeFile up -d --no-recreate
    if ($LASTEXITCODE -ne 0 -and -not (Test-LocalStackHealthy -HealthUrl $healthUrl)) {
        throw "Could not start LocalStack."
    }
}

Write-Host "2/6 - Waiting LocalStack health endpoint"
$attempt = 0
while ($attempt -lt 45) {
    $attempt++
    if (Test-LocalStackHealthy -HealthUrl $healthUrl) {
        break
    }
    Start-Sleep -Seconds 2
}
if ($attempt -ge 45) {
    throw "LocalStack did not become healthy in time."
}

Write-Host "3/6 - Building Java artifacts"
if (Get-Command mvn -ErrorAction SilentlyContinue) {
    Push-Location $ProjectPath
    Invoke-External -Command { mvn clean package } -ErrorMessage "Maven build failed."
    Pop-Location
} else {
    Invoke-External -Command {
        docker run --rm `
            -v "${ProjectPath}:/app" `
            -v "${env:USERPROFILE}\.m2:/root/.m2" `
            -w /app `
            maven:3.9.9-eclipse-temurin-17 `
            mvn clean package
    } -ErrorMessage "Docker Maven build failed."
}

Write-Host "4/6 - Initializing Terraform"
Push-Location (Join-Path $PSScriptRoot $TerraformPath)
Invoke-External -Command { terraform init -input=false } -ErrorMessage "Terraform init failed."

Write-Host "5/6 - Running Terraform"
if ($Destroy) {
    Invoke-External -Command { terraform destroy -auto-approve -input=false } -ErrorMessage "Terraform destroy failed."
} else {
    Invoke-External -Command { terraform apply -auto-approve -input=false } -ErrorMessage "Terraform apply failed."
}

Write-Host "6/6 - Terraform outputs"
Invoke-External -Command { terraform output } -ErrorMessage "Could not read Terraform outputs."
Pop-Location
