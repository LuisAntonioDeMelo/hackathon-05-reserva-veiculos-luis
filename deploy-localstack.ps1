param(
    [string]$StackName = "vehicle-platform",
    [string]$Region = "us-east-1",
    [string]$Endpoint = "http://localhost:4566",
    [string]$ArtifactBucket = "sam-artifacts-local",
    [string]$ComposeFile = ".\localstack\docker-compose.yaml"
)

$ErrorActionPreference = "Stop"
$PackagedTemplate = "$PSScriptRoot\template.packaged.yaml"
$ClientsPath = "$PSScriptRoot\Clients"
$FunctionArtifact = "$ClientsPath\target\function.jar"

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

Write-Host "1/8 - Starting LocalStack"
$healthUrl = "$Endpoint/_localstack/health"
if (-not (Test-LocalStackHealthy -HealthUrl $healthUrl)) {
  docker compose -f $ComposeFile up -d --no-recreate
  if ($LASTEXITCODE -ne 0 -and -not (Test-LocalStackHealthy -HealthUrl $healthUrl)) {
    throw "Could not start LocalStack."
  }
} else {
  Write-Host "LocalStack already healthy, continuing."
}

Write-Host "2/8 - Waiting LocalStack health endpoint"
$attempt = 0
while ($attempt -lt 45) {
  $attempt++
  try {
    $health = Invoke-RestMethod -Uri $healthUrl -Method Get -TimeoutSec 3
    if ($health -and $health.services) {
      break
    }
  } catch {
    Start-Sleep -Seconds 2
  }
}

if ($attempt -ge 45) {
  throw "LocalStack did not become healthy in time."
}

Write-Host "3/8 - Building Java artifacts"
if (Get-Command mvn -ErrorAction SilentlyContinue) {
  Push-Location $ClientsPath
  Invoke-External -Command { mvn clean package } -ErrorMessage "Maven build failed."
  Pop-Location
} else {
  Write-Host "Maven not found, using Docker Maven image"
  Invoke-External -Command {
    docker run --rm `
      -v "${ClientsPath}:/app" `
      -v "${env:USERPROFILE}\.m2:/root/.m2" `
      -w /app `
      maven:3.9.9-eclipse-temurin-17 `
      mvn clean package
  } -ErrorMessage "Docker Maven build failed."
}

Write-Host "4/8 - Ensuring LocalStack artifact bucket"
aws --endpoint-url=$Endpoint --region $Region s3 mb "s3://$ArtifactBucket" 2>$null
if ($LASTEXITCODE -ne 0 -and $LASTEXITCODE -ne 1) {
  throw "Could not ensure artifact bucket."
}
if ($LASTEXITCODE -eq 1) {
  Write-Host "Bucket already exists, continuing."
}

Write-Host "5/8 - Uploading Lambda artifact and generating packaged template"
Invoke-External -Command {
  aws --endpoint-url=$Endpoint --region $Region s3 cp $FunctionArtifact "s3://$ArtifactBucket/function.jar"
} -ErrorMessage "Could not upload Lambda artifact to LocalStack S3."

$rawTemplate = Get-Content -Path "$PSScriptRoot\template.yaml" -Raw
$codeUriYaml = @"
CodeUri:
      Bucket: $ArtifactBucket
      Key: function.jar
"@
$packaged = $rawTemplate.Replace("CodeUri: Clients/target/function.jar", $codeUriYaml.TrimEnd())
Set-Content -Path $PackagedTemplate -Value $packaged -Encoding UTF8

Write-Host "6/8 - Deploying stack to LocalStack"
Invoke-External -Command {
  aws --endpoint-url=$Endpoint --region $Region cloudformation deploy `
    --template-file $PackagedTemplate `
    --stack-name $StackName `
    --capabilities CAPABILITY_IAM `
    --parameter-overrides AwsEndpointOverride=http://localhost.localstack.cloud:4566
} -ErrorMessage "CloudFormation deploy failed."

Write-Host "7/8 - Reading stack outputs"
Invoke-External -Command {
  aws --endpoint-url=$Endpoint cloudformation describe-stacks `
    --stack-name $StackName `
    --region $Region `
    --query "Stacks[0].Outputs"
} -ErrorMessage "Could not read CloudFormation outputs."

Write-Host "8/8 - Deployment complete"
