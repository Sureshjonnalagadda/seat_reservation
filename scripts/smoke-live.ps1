param(
    [Parameter(Mandatory = $true)]
    [string]$BaseUrl
)

$BaseUrl = $BaseUrl.TrimEnd("/")
$script:failed = 0

function Test-Endpoint($path, $expectStatus) {
    $url = "$BaseUrl$path"
    try {
        $resp = Invoke-WebRequest -Uri $url -UseBasicParsing -TimeoutSec 60
        $code = [int]$resp.StatusCode
        if ($code -eq $expectStatus) {
            Write-Host "PASS $path -> $code"
        } else {
            Write-Host "FAIL $path -> $code (expected $expectStatus)"
            $script:failed++
        }
    } catch {
        $code = $null
        if ($_.Exception.Response) {
            $code = [int]$_.Exception.Response.StatusCode.value__
        }
        if ($code -eq $expectStatus) {
            Write-Host "PASS $path -> $code"
        } else {
            Write-Host "FAIL $path -> $code / $($_.Exception.Message)"
            $script:failed++
        }
    }
}

Write-Host "Smoke test: $BaseUrl"
Write-Host "========================================"

Test-Endpoint "/health/live" 200
Test-Endpoint "/health/ready" 200
Test-Endpoint "/actuator/prometheus" 200

Write-Host "========================================"
if ($script:failed -eq 0) {
    Write-Host "Result: PASS"
    exit 0
}
Write-Host "Result: FAIL ($script:failed checks)"
exit 1
