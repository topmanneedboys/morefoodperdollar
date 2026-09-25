$ErrorActionPreference = 'Stop'

$Package = 'com.valuepilot.app'
$ExpectedVersionCode = '10107'
$Query = 'leche'
$UiDumpPath = '/sdcard/valuepilot-live-search-trace.xml'
$StayAwakeChanged = $false
$Serial = $null

function Invoke-Adb {
    param([Parameter(Mandatory = $true)][string[]]$Arguments)
    $output = & adb @Arguments 2>&1
    if ($LASTEXITCODE -ne 0) {
        throw "ADB_FAILED_$($Arguments[0])"
    }
    return $output
}

function Get-UiXml {
    Invoke-Adb @('-s', $Serial, 'shell', 'uiautomator', 'dump', $UiDumpPath) | Out-Null
    $bytes = & adb -s $Serial exec-out cat $UiDumpPath 2>$null
    if ($LASTEXITCODE -ne 0) {
        throw 'UI_DUMP_READ_FAILED'
    }
    return [xml]($bytes -join "`n")
}

function Get-BoundsCenter {
    param([Parameter(Mandatory = $true)][System.Xml.XmlElement]$Node)
    $match = [regex]::Match($Node.bounds, '^\[(\d+),(\d+)\]\[(\d+),(\d+)\]$')
    if (-not $match.Success) {
        throw 'UI_BOUNDS_INVALID'
    }
    $x = ([int]$match.Groups[1].Value + [int]$match.Groups[3].Value) / 2
    $y = ([int]$match.Groups[2].Value + [int]$match.Groups[4].Value) / 2
    return @([int]$x, [int]$y)
}

function Tap-Resource {
    param([Parameter(Mandatory = $true)][System.Xml.XmlDocument]$Ui, [Parameter(Mandatory = $true)][string]$ResourceId)
    $node = $Ui.SelectSingleNode("//*[@resource-id='$ResourceId']")
    if ($null -eq $node) {
        throw "UI_RESOURCE_MISSING_$ResourceId"
    }
    $center = Get-BoundsCenter $node
    Invoke-Adb @('-s', $Serial, 'shell', 'input', 'tap', $center[0], $center[1]) | Out-Null
}

try {
    $deviceRows = @(adb devices | Select-String '\sdevice$')
    if ($deviceRows.Count -ne 1) {
        throw 'EXPECTED_ONE_UNLOCKED_DEVICE'
    }
    $Serial = ($deviceRows[0].ToString() -split '\s+')[0]

    $packageDump = (Invoke-Adb @('-s', $Serial, 'shell', 'dumpsys', 'package', $Package)) -join "`n"
    if ($packageDump -notmatch "versionCode=$ExpectedVersionCode") {
        throw "EXPECTED_VERSION_${ExpectedVersionCode}_NOT_INSTALLED"
    }
    $keyguard = (Invoke-Adb @('-s', $Serial, 'shell', 'dumpsys', 'window')) -join "`n"
    if ($keyguard -match 'mDreamingLockscreen=true|isStatusBarKeyguard=true|mInputRestricted=true') {
        throw 'DEVICE_LOCKED_UNLOCK_REQUIRED'
    }

    Invoke-Adb @('-s', $Serial, 'shell', 'svc', 'power', 'stayon', 'usb') | Out-Null
    $StayAwakeChanged = $true
    Invoke-Adb @('-s', $Serial, 'logcat', '-c') | Out-Null
    Invoke-Adb @('-s', $Serial, 'shell', 'am', 'force-stop', $Package) | Out-Null
    Invoke-Adb @('-s', $Serial, 'shell', 'am', 'start', '-n', "$Package/.MainActivity") | Out-Null
    Start-Sleep -Seconds 2

    $ui = Get-UiXml
    Tap-Resource $ui "$Package:id/navSearch"
    Start-Sleep -Milliseconds 500
    $ui = Get-UiXml
    $inputNode = $ui.SelectSingleNode("//*[@resource-id='$Package:id/searchInput']")
    if ($null -eq $inputNode) {
        throw 'UI_SEARCH_INPUT_MISSING'
    }
    $inputCenter = Get-BoundsCenter $inputNode
    Invoke-Adb @('-s', $Serial, 'shell', 'input', 'tap', $inputCenter[0], $inputCenter[1]) | Out-Null
    Invoke-Adb @('-s', $Serial, 'shell', 'input', 'text', $Query) | Out-Null
    Start-Sleep -Milliseconds 250
    $ui = Get-UiXml
    Tap-Resource $ui "$Package:id/searchButton"
    Start-Sleep -Seconds 18

    $ui = Get-UiXml
    $status = $ui.SelectSingleNode("//*[@resource-id='$Package:id/searchStatus']")
    $heading = $ui.SelectSingleNode("//*[@resource-id='$Package:id/searchResultsHeading']")
    $cards = @($ui.SelectNodes("//*[@resource-id='$Package:id/searchResultsContainer']//*[contains(@class,'MaterialCardView') or @clickable='true']"))
    Write-Output 'DEVICE_TRACE_COMPLETE'
    Write-Output "SERIAL=$Serial"
    Write-Output "VERSION_CODE=$ExpectedVersionCode"
    Write-Output "SEARCH_STATUS=$($status.text)"
    Write-Output "RESULT_HEADING=$($heading.text)"
    Write-Output "RESULT_NODE_COUNT=$($cards.Count)"
    Write-Output 'SAFE_LOGCAT_BEGIN'
    Invoke-Adb @('-s', $Serial, 'logcat', '-d', '-v', 'brief', '-s', 'ValuePilotLiveSearch:W', 'AndroidRuntime:E') |
        ForEach-Object { $_.ToString() -replace '(?i)(token|authorization|cookie|password|secret)[=:]\S+', '$1=<REDACTED>' }
    Write-Output 'SAFE_LOGCAT_END'
}
finally {
    if ($null -ne $Serial) {
        & adb -s $Serial shell rm -f $UiDumpPath 2>$null | Out-Null
        if ($StayAwakeChanged) {
            & adb -s $Serial shell svc power stayon false 2>$null | Out-Null
        }
    }
}
