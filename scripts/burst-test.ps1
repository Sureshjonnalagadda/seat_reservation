param(
    [string]$BaseUrl = "http://localhost:8080",
    [int]$Requests = 100,
    [string]$Seat = "A12"
)

$BaseUrl = $BaseUrl.TrimEnd("/")
$username = "burst_user_{0}" -f [DateTimeOffset]::UtcNow.ToUnixTimeSeconds()
$password = "password123"

function Register-And-Login {
    try {
        Invoke-RestMethod -Method Post -Uri "$BaseUrl/auth/register" -ContentType "application/json" `
            -Body (@{ username = $username; password = $password } | ConvertTo-Json) | Out-Null
    } catch { }
    $login = Invoke-RestMethod -Method Post -Uri "$BaseUrl/auth/login" -ContentType "application/json" `
        -Body (@{ username = $username; password = $password } | ConvertTo-Json)
    return $login.access_token
}

$adminLogin = Invoke-RestMethod -Method Post -Uri "$BaseUrl/auth/login" -ContentType "application/json" `
    -Body '{"username":"admin","password":"adminrole"}'
$adminHeaders = @{ Authorization = "Bearer $($adminLogin.access_token)" }
$createBody = @{
    name = "Burst Show"
    seats = @($Seat)
    price_paise = 10000
    per_user_limit = 4
} | ConvertTo-Json
$show = Invoke-RestMethod -Method Post -Uri "$BaseUrl/shows" -Headers $adminHeaders -ContentType "application/json" -Body $createBody
$showId = $show.id
$userToken = Register-And-Login

Write-Host "Running $Requests concurrent reservations (show $showId, seat $Seat)..."

$jobs = 1..$Requests | ForEach-Object {
    $id = $_
    Start-Job -ScriptBlock {
        param($BaseUrl, $ShowId, $Seat, $Token, $Key)
        try {
            $headers = @{
                Authorization = "Bearer $Token"
                "Content-Type"  = "application/json"
                "Idempotency-Key" = $Key
            }
            $body = (@{ seats = @($Seat) } | ConvertTo-Json -Compress)
            $resp = Invoke-WebRequest -Method Post -Uri "$BaseUrl/shows/$ShowId/reserve" `
                -Headers $headers -Body $body -UseBasicParsing
            return [int]$resp.StatusCode
        } catch {
            $response = $_.Exception.Response
            if ($response) {
                return [int]$response.StatusCode.value__
            }
            return 0
        }
    } -ArgumentList $BaseUrl, $showId, $Seat, $userToken, "burst-$id"
}

$codes = @($jobs | Wait-Job | Receive-Job)
$jobs | Remove-Job -Force

$created = @($codes | Where-Object { $_ -eq 201 }).Count
$replay = @($codes | Where-Object { $_ -eq 200 }).Count
$conflict = @($codes | Where-Object { $_ -eq 409 }).Count
$serverErrors = @($codes | Where-Object { $_ -ge 500 }).Count
$accounted = $created + $replay + $conflict + $serverErrors
$other = $Requests - $accounted

$showState = Invoke-RestMethod -Method Get -Uri "$BaseUrl/shows/$showId"
$finalStatus = ($showState.seats | Where-Object { $_.seat_number -eq $Seat }).status

Write-Host "========================================"
Write-Host " Seat Reservation Concurrency Test"
Write-Host "========================================"
Write-Host ""
Write-Host "Requests:       $Requests"
Write-Host "Seat:           $Seat"
Write-Host ""
Write-Host "201 Created:    $created"
Write-Host "200 Replay:     $replay"
Write-Host "409 Conflict:   $conflict"
Write-Host "5xx Errors:     $serverErrors"
Write-Host "Other (401…):   $other"
Write-Host ""
Write-Host "Final Seat State:"
Write-Host "$Seat = $finalStatus"
Write-Host ""

$pass = (
    $created -eq 1 -and
    $serverErrors -eq 0 -and
    $other -eq 0 -and
    ($created + $replay + $conflict) -eq $Requests -and
    $finalStatus -eq "CONFIRMED"
)
if ($pass) {
    Write-Host "Result:"
    Write-Host "PASS"
    Write-Host "========================================"
    exit 0
}
Write-Host "Result:"
Write-Host "FAIL"
Write-Host "========================================"
exit 1
