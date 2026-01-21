# PowerShell function to view logs between specific times
# Usage: View-LogsByTime -StartTime "11:58:30" -EndTime "11:58:35"

function View-LogsByTime {
    param(
        [Parameter(Mandatory=$false)]
        [string]$LogFile = "logs\semscan-api.log",
        
        [Parameter(Mandatory=$false)]
        [string]$Date = (Get-Date -Format "yyyy-MM-dd"),
        
        [Parameter(Mandatory=$false)]
        [string]$StartTime,
        
        [Parameter(Mandatory=$false)]
        [string]$EndTime,
        
        [Parameter(Mandatory=$false)]
        [int]$LastLines = 0
    )
    
    if (-not (Test-Path $LogFile)) {
        Write-Host "Log file not found: $LogFile" -ForegroundColor Red
        return
    }
    
    $content = Get-Content $LogFile
    
    if ($StartTime -or $EndTime) {
        # Filter by time range
        $filtered = $content | Where-Object {
            if ($_ -match "(\d{4}-\d{2}-\d{2}) (\d{2}:\d{2}:\d{2})") {
                $logDate = $matches[1]
                $logTime = $matches[2]
                
                $dateMatch = ($logDate -eq $Date)
                $timeMatch = $true
                
                if ($StartTime) {
                    $timeMatch = $timeMatch -and ($logTime -ge $StartTime)
                }
                if ($EndTime) {
                    $timeMatch = $timeMatch -and ($logTime -le $EndTime)
                }
                
                return ($dateMatch -and $timeMatch)
            }
            return $false
        }
        
        if ($LastLines -gt 0) {
            $filtered | Select-Object -Last $LastLines
        } else {
            $filtered
        }
    } else {
        # No time filter, just show content
        if ($LastLines -gt 0) {
            $content | Select-Object -Last $LastLines
        } else {
            $content
        }
    }
}

# Examples:
# View-LogsByTime -StartTime "11:58:30" -EndTime "11:58:35"
# View-LogsByTime -StartTime "11:58" -LastLines 50
# View-LogsByTime -Date "2025-12-14" -StartTime "11:58" -EndTime "11:59"

