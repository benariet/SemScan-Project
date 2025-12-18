# PowerShell script to view logs between specific times
# Usage examples:

# View logs from a specific time
Get-Content "logs\semscan-api.log" | Select-String -Pattern "2025-12-14 11:58"

# View logs between two times (using regex pattern)
Get-Content "logs\semscan-api.log" | Where-Object {
    $_ -match "2025-12-14 11:58:\d{2}" -or 
    $_ -match "2025-12-14 11:59:\d{2}"
}

# View logs from a specific hour
Get-Content "logs\semscan-api.log" | Select-String -Pattern "2025-12-14 11:"

# View logs from a specific date
Get-Content "logs\semscan-api.log" | Select-String -Pattern "2025-12-14"

# View logs between two specific times (more precise)
$startTime = "2025-12-14 11:58:30"
$endTime = "2025-12-14 11:58:35"
Get-Content "logs\semscan-api.log" | Where-Object {
    if ($_ -match "(\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2})") {
        $logTime = $matches[1]
        $logTime -ge $startTime -and $logTime -le $endTime
    }
}

# View error logs only
Get-Content "logs\semscan-api-error.log" | Select-String -Pattern "2025-12-14 11:58"

# View logs and save to file
Get-Content "logs\semscan-api.log" | Select-String -Pattern "2025-12-14 11:58" | Out-File "filtered-logs.txt"

# View last N lines from a specific time
Get-Content "logs\semscan-api.log" | Select-String -Pattern "2025-12-14 11:58" | Select-Object -Last 20

