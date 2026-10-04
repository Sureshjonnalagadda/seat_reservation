param(
    [string]$TestFilter = "",
    [switch]$SkipIntegrationTests,
    [switch]$UseDocker
)

$projectRoot = Split-Path -Parent $PSScriptRoot
Set-Location $projectRoot

function Test-DockerRunning {
    try {
        docker info 2>$null | Out-Null
        return $LASTEXITCODE -eq 0
    } catch {
        return $false
    }
}

$mvnArgs = @("test")
if ($TestFilter) {
    $mvnArgs += "-Dtest=$TestFilter"
} elseif ($SkipIntegrationTests) {
    $mvnArgs += '-Dtest=!ReservationConcurrencyIT'
}

if ($UseDocker -or (-not (Test-Path "$projectRoot\mvnw.cmd") -and (Test-DockerRunning))) {
    if (-not (Test-DockerRunning)) {
        Write-Error "Docker is not running. Start Docker Desktop for containerized Maven, or use .\scripts\mvn.ps1 test"
        exit 1
    }
    $dockerArgs = @(
        "run", "--rm",
        "-v", "${projectRoot}:/app",
        "-w", "/app",
        "-v", "//var/run/docker.sock:/var/run/docker.sock",
        "-e", "TESTCONTAINERS_HOST_OVERRIDE=host.docker.internal",
        "-e", "TESTCONTAINERS_RYUK_DISABLED=true",
        "maven:3.9.9-eclipse-temurin-17",
        "mvn", "-q"
    ) + $mvnArgs
    Write-Host "Running tests via Docker Maven (Testcontainers needs Docker)..."
    & docker @dockerArgs
    exit $LASTEXITCODE
}

Write-Host "Running tests via Maven Wrapper (.\mvnw.cmd)..."
& "$PSScriptRoot\mvn.ps1" @mvnArgs
exit $LASTEXITCODE
