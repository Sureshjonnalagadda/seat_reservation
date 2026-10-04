# Run Maven for this project: mvnw (no global Maven install required).
param(
    [Parameter(ValueFromRemainingArguments = $true)]
    [string[]]$MavenArgs
)

$projectRoot = Split-Path -Parent $PSScriptRoot
Set-Location $projectRoot

if (-not (Test-Path "$projectRoot\mvnw.cmd")) {
    Write-Error "mvnw.cmd not found. Run from repo root or re-clone the project."
    exit 1
}

$env:JAVA_HOME = $env:JAVA_HOME
if (-not $env:JAVA_HOME) {
    $java = (Get-Command java -ErrorAction SilentlyContinue).Source
    if ($java) {
        $env:JAVA_HOME = Split-Path (Split-Path $java -Parent) -Parent
    }
}

if (-not $env:JAVA_HOME) {
    Write-Error "Java 17 not found. Install Temurin 17 and ensure java is on PATH."
    exit 1
}

& "$projectRoot\mvnw.cmd" @MavenArgs
exit $LASTEXITCODE
