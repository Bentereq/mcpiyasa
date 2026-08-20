param(
    [string]$Java16 = "java",
    [string]$Java17 = "java",
    [string]$Java21 = "java",
    [string[]]$Versions = @("1.16.5", "1.18.2", "1.20.4", "1.21.4")
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$ScriptRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$RepoRoot = (Resolve-Path (Join-Path $ScriptRoot "..")).Path
$CacheRoot = Join-Path $ScriptRoot "cache"
$RunRoot = Join-Path $ScriptRoot "run"
$JdkRoot = Join-Path $env:USERPROFILE ".jdks"
$TimeoutSeconds = 120
$DownloadHeaders = @{
    "User-Agent" = "MCPiyasa-Boot-Matrix/0.1 (https://github.com/Bentereq/mcpiyasa)"
}

$VersionJava = @{
    "1.16.5" = 16
    "1.18.2" = 17
    "1.20.4" = 17
    "1.21.4" = 21
}
$VersionPort = @{
    "1.16.5" = 25901
    "1.18.2" = 25902
    "1.20.4" = 25903
    "1.21.4" = 25904
}
$ConfiguredJava = @{
    16 = $Java16
    17 = $Java17
    21 = $Java21
}

New-Item -ItemType Directory -Force -Path $CacheRoot, $RunRoot, $JdkRoot | Out-Null

function Write-Phase {
    param([string]$Message)
    Write-Host "`n==> $Message" -ForegroundColor Cyan
}

function Invoke-CurlDownload {
    param(
        [Parameter(Mandatory = $true)][string]$Uri,
        [Parameter(Mandatory = $true)][string]$PartialPath
    )

    $curl = Get-Command "curl.exe" -ErrorAction SilentlyContinue
    if ($null -eq $curl) {
        throw "curl.exe bulunamadi; yarim Invoke-WebRequest indirmesi surdurulemiyor."
    }

    if ((Test-Path -LiteralPath $PartialPath) -and (Get-Item -LiteralPath $PartialPath).Length -gt 0) {
        Write-Warning "Yarim indirme curl ile kaldigi yerden surduruluyor: $PartialPath"
        & $curl.Source -L --fail --retry 3 --retry-delay 3 --connect-timeout 30 --max-time 900 `
            -C - --output $PartialPath $Uri
        if ($LASTEXITCODE -eq 0) {
            return
        }
        Write-Warning "HTTP Range devam istegi basarisiz; curl ile bastan bir kez indirilecek."
        Remove-Item -LiteralPath $PartialPath -Force -ErrorAction SilentlyContinue
    }

    & $curl.Source -L --fail --retry 3 --retry-delay 3 --connect-timeout 30 --max-time 900 `
        --output $PartialPath $Uri
    if ($LASTEXITCODE -ne 0) {
        throw "curl indirmesi cikis kodu $LASTEXITCODE ile basarisiz oldu."
    }
}

function Invoke-CachedDownload {
    param(
        [Parameter(Mandatory = $true)][string]$Uri,
        [Parameter(Mandatory = $true)][string]$Destination,
        [long]$MinimumBytes = 1
    )

    if (Test-Path -LiteralPath $Destination) {
        $cached = Get-Item -LiteralPath $Destination
        if ($cached.Length -ge $MinimumBytes) {
            Write-Host "Cache kullaniliyor: $Destination"
            return
        }
        Remove-Item -LiteralPath $Destination -Force
    }

    $partial = "$Destination.partial"
    if ((Test-Path -LiteralPath $partial) -and (Get-Item -LiteralPath $partial).Length -gt 0) {
        Invoke-CurlDownload -Uri $Uri -PartialPath $partial
        $resumed = Get-Item -LiteralPath $partial
        if ($resumed.Length -lt $MinimumBytes) {
            throw "Surdurulen dosya beklenenden kucuk ($($resumed.Length) bayt)."
        }
        Move-Item -LiteralPath $partial -Destination $Destination -Force
        return
    } elseif (Test-Path -LiteralPath $partial) {
        Remove-Item -LiteralPath $partial -Force
    }

    Write-Host "Indiriliyor: $Uri"
    try {
        Invoke-WebRequest -UseBasicParsing -TimeoutSec 300 -Headers $DownloadHeaders -Uri $Uri -OutFile $partial
        $downloaded = Get-Item -LiteralPath $partial
        if ($downloaded.Length -lt $MinimumBytes) {
            throw "Indirilen dosya beklenenden kucuk ($($downloaded.Length) bayt)."
        }
        Move-Item -LiteralPath $partial -Destination $Destination -Force
    } catch {
        $webError = $_.Exception.Message
        if ((Test-Path -LiteralPath $partial) -and (Get-Item -LiteralPath $partial).Length -gt 0) {
            Write-Warning "Invoke-WebRequest tamamlanamadi ($webError); yarim dosya surdurulecek."
            Invoke-CurlDownload -Uri $Uri -PartialPath $partial
            $downloaded = Get-Item -LiteralPath $partial
            if ($downloaded.Length -lt $MinimumBytes) {
                throw "Fallback dosyasi beklenenden kucuk ($($downloaded.Length) bayt)."
            }
            Move-Item -LiteralPath $partial -Destination $Destination -Force
            return
        }
        throw
    }
}

function Get-JavaMajor {
    param([Parameter(Mandatory = $true)][string]$JavaExecutable)

    $originalErrorPreference = $ErrorActionPreference
    try {
        # java -version writes normal version information to stderr. Windows
        # PowerShell 5 turns redirected native stderr into ErrorRecord objects;
        # collect those records without letting the script-wide Stop policy
        # mistake a healthy JDK for a failed command.
        $ErrorActionPreference = "Continue"
        $versionOutput = & $JavaExecutable -version 2>&1
        $javaExitCode = $LASTEXITCODE
        if ($javaExitCode -ne 0) {
            return $null
        }
        $versionText = ($versionOutput | Out-String)
        if ($versionText -match 'version\s+"1\.(\d+)') {
            return [int]$Matches[1]
        }
        if ($versionText -match 'version\s+"(\d+)') {
            return [int]$Matches[1]
        }
    } catch {
        return $null
    } finally {
        $ErrorActionPreference = $originalErrorPreference
    }
    return $null
}

function Find-CachedJava {
    param([Parameter(Mandatory = $true)][int]$Major)

    $candidates = Get-ChildItem -LiteralPath $JdkRoot -Filter "java.exe" -File -Recurse -ErrorAction SilentlyContinue |
        Where-Object { $_.FullName -match '[\\/]bin[\\/]java\.exe$' } |
        Sort-Object FullName
    foreach ($candidate in $candidates) {
        if ((Get-JavaMajor -JavaExecutable $candidate.FullName) -eq $Major) {
            return $candidate.FullName
        }
    }
    return $null
}

function Install-Temurin {
    param([Parameter(Mandatory = $true)][int]$Major)

    $downloadUri = "https://api.adoptium.net/v3/binary/latest/$Major/ga/windows/x64/jdk/hotspot/normal/eclipse"
    $archive = Join-Path $JdkRoot "temurin-$Major-windows-x64.zip"
    $extractRoot = Join-Path $JdkRoot ".mcpiyasa-temurin-$Major-extract"
    $installRoot = Join-Path $JdkRoot "temurin-$Major-mcpiyasa"

    for ($attempt = 1; $attempt -le 2; $attempt++) {
        try {
            if ($attempt -eq 2 -and (Test-Path -LiteralPath $archive)) {
                Write-Warning "JDK $Major arsivi acilamadi; cache silinip bir kez daha indiriliyor."
                Remove-Item -LiteralPath $archive -Force
            }
            Invoke-CachedDownload -Uri $downloadUri -Destination $archive -MinimumBytes 1000000

            if (Test-Path -LiteralPath $extractRoot) {
                Remove-Item -LiteralPath $extractRoot -Recurse -Force
            }
            New-Item -ItemType Directory -Force -Path $extractRoot | Out-Null
            Expand-Archive -LiteralPath $archive -DestinationPath $extractRoot -Force

            $java = Get-ChildItem -LiteralPath $extractRoot -Filter "java.exe" -File -Recurse |
                Where-Object { $_.FullName -match '[\\/]bin[\\/]java\.exe$' } |
                Select-Object -First 1
            if ($null -eq $java) {
                throw "JDK $Major arsivinde bin\java.exe bulunamadi."
            }
            if ((Get-JavaMajor -JavaExecutable $java.FullName) -ne $Major) {
                throw "Indirilen Java, beklenen $Major ana surumu degil."
            }

            $jdkHome = Split-Path -Parent (Split-Path -Parent $java.FullName)
            if (Test-Path -LiteralPath $installRoot) {
                Remove-Item -LiteralPath $installRoot -Recurse -Force
            }
            Move-Item -LiteralPath $jdkHome -Destination $installRoot
            Remove-Item -LiteralPath $extractRoot -Recurse -Force -ErrorAction SilentlyContinue
            return (Join-Path $installRoot "bin\java.exe")
        } catch {
            Remove-Item -LiteralPath $extractRoot -Recurse -Force -ErrorAction SilentlyContinue
            if ($attempt -eq 2) {
                throw
            }
        }
    }
}

function Resolve-Java {
    param(
        [Parameter(Mandatory = $true)][int]$Major,
        [Parameter(Mandatory = $true)][string]$Configured
    )

    if ($Configured -ne "java") {
        $candidate = $Configured
        if (Test-Path -LiteralPath $candidate -PathType Container) {
            $candidate = Join-Path $candidate "bin\java.exe"
        }
        if (-not (Test-Path -LiteralPath $candidate -PathType Leaf)) {
            $command = Get-Command $candidate -ErrorAction SilentlyContinue
            if ($null -ne $command) {
                $candidate = $command.Source
            }
        }
        if (-not (Test-Path -LiteralPath $candidate -PathType Leaf)) {
            throw "Yapilandirilan Java bulunamadi: $Configured"
        }
        $actualMajor = Get-JavaMajor -JavaExecutable $candidate
        if ($actualMajor -ne $Major) {
            throw "Yapilandirilan Java $Configured surum $actualMajor; Java $Major gerekiyor."
        }
        return (Resolve-Path -LiteralPath $candidate).Path
    }

    $cached = Find-CachedJava -Major $Major
    if ($null -ne $cached) {
        Write-Host "JDK $Major bulundu: $cached"
        return $cached
    }

    $systemJava = Get-Command "java" -ErrorAction SilentlyContinue
    if ($null -ne $systemJava -and (Get-JavaMajor -JavaExecutable $systemJava.Source) -eq $Major) {
        Write-Host "JDK $Major PATH uzerinde bulundu: $($systemJava.Source)"
        return $systemJava.Source
    }

    Write-Host "JDK $Major eksik; Temurin otomatik indirilecek." -ForegroundColor Yellow
    return Install-Temurin -Major $Major
}

function Get-PaperJar {
    param([Parameter(Mandatory = $true)][string]$Version)

    $buildsUri = "https://api.papermc.io/v2/projects/paper/versions/$Version/builds"
    Write-Host "Paper $Version son build bilgisi v2 API'den aliniyor."
    try {
        $response = Invoke-WebRequest -UseBasicParsing -Headers $DownloadHeaders -Uri $buildsUri
        $metadata = $response.Content | ConvertFrom-Json
        $build = $metadata.builds | Sort-Object { [int]$_.build } -Descending | Select-Object -First 1
        if ($null -eq $build) {
            throw "Paper $Version icin v2 build bulunamadi."
        }

        $application = $build.downloads.application
        if ($null -eq $application -or [string]::IsNullOrWhiteSpace($application.name)) {
            throw "Paper $Version build $($build.build) uygulama JAR bilgisi icermiyor."
        }
        $fileName = [string]$application.name
        $paperJar = Join-Path $CacheRoot "paper-$Version-$($build.build).jar"
        $downloadName = [Uri]::EscapeDataString($fileName)
        $downloadUri = "https://api.papermc.io/v2/projects/paper/versions/$Version/builds/$($build.build)/downloads/$downloadName"
        Invoke-CachedDownload -Uri $downloadUri -Destination $paperJar -MinimumBytes 1000000

        $expectedSha256 = [string]$application.sha256
        if ([string]::IsNullOrWhiteSpace($expectedSha256)) {
            throw "Paper $Version v2 build $($build.build), SHA-256 bilgisi icermiyor."
        }
        $actualSha256 = (Get-FileHash -LiteralPath $paperJar -Algorithm SHA256).Hash.ToLowerInvariant()
        if ($actualSha256 -ne $expectedSha256.ToLowerInvariant()) {
            throw "Paper $Version JAR SHA-256 dogrulamasi basarisiz."
        }
        return $paperJar
    } catch {
        Write-Warning "PaperMC v2 kullanilamiyor ($($_.Exception.Message)); resmi v3 downloads API'ye geciliyor."
    }

    $v3BuildsUri = "https://fill.papermc.io/v3/projects/paper/versions/$Version/builds"
    $v3Response = Invoke-WebRequest -UseBasicParsing -Headers $DownloadHeaders -Uri $v3BuildsUri
    $parsedV3Builds = $v3Response.Content | ConvertFrom-Json
    # Windows PowerShell 5 returns a top-level JSON array as one Object[]
    # pipeline value. Enumerate it explicitly before filtering/sorting.
    $v3Builds = @($parsedV3Builds | ForEach-Object { $_ })
    $build = $v3Builds |
        Where-Object { $_.channel -eq "STABLE" } |
        Sort-Object { [int]$_.id } -Descending |
        Select-Object -First 1
    if ($null -eq $build) {
        $build = $v3Builds | Sort-Object { [int]$_.id } -Descending | Select-Object -First 1
    }
    if ($null -eq $build) {
        throw "Paper $Version icin v3 build bulunamadi."
    }

    $downloadProperty = $build.downloads.PSObject.Properties["server:default"]
    if ($null -eq $downloadProperty) {
        throw "Paper $Version v3 build $($build.id), server:default JAR bilgisi icermiyor."
    }
    $download = $downloadProperty.Value
    if ($null -eq $download -or [string]::IsNullOrWhiteSpace([string]$download.url)) {
        throw "Paper $Version v3 build $($build.id), indirme URL'si icermiyor."
    }
    $paperJar = Join-Path $CacheRoot "paper-$Version-$($build.id).jar"
    $downloadUri = [string]$download.url
    Invoke-CachedDownload -Uri $downloadUri -Destination $paperJar -MinimumBytes 1000000

    $expectedSha256 = [string]$download.checksums.sha256
    if (-not [string]::IsNullOrWhiteSpace($expectedSha256)) {
        $actualSha256 = (Get-FileHash -LiteralPath $paperJar -Algorithm SHA256).Hash.ToLowerInvariant()
        if ($actualSha256 -ne $expectedSha256.ToLowerInvariant()) {
            throw "Paper $Version JAR SHA-256 dogrulamasi basarisiz."
        }
    }
    return $paperJar
}

function Get-VaultJar {
    $vaultJar = Join-Path $CacheRoot "Vault-1.7.3.jar"
    Invoke-CachedDownload `
        -Uri "https://github.com/MilkBowl/Vault/releases/download/1.7.3/Vault.jar" `
        -Destination $vaultJar `
        -MinimumBytes 100000
    return $vaultJar
}

function Test-MCPiyasaCrash {
    param([string]$Text)

    if ([string]::IsNullOrEmpty($Text)) {
        return $false
    }
    $stackFrameSeen = $Text -match '(?m)^\s*at com\.mcpiyasa\.'
    $directErrorSeen = $Text -match '(?i)\b(SEVERE|ERROR)\b.*com\.mcpiyasa'
    return $stackFrameSeen -or $directErrorSeen
}

function Read-LogText {
    param([Parameter(Mandatory = $true)][string]$Path)
    if (-not (Test-Path -LiteralPath $Path)) {
        return ""
    }
    $content = Get-Content -LiteralPath $Path -Raw -ErrorAction SilentlyContinue
    if ($null -eq $content) {
        return ""
    }
    return [string]$content
}

function New-Result {
    param(
        [string]$Version,
        [string]$Boot,
        [string]$MCPiyasa,
        [string]$EconomyDiagnostic,
        [string]$ErrorText
    )
    return [PSCustomObject]@{
        surum = $Version
        boot = $Boot
        mcpiyasa = $MCPiyasa
        ekonomiTeshis = $EconomyDiagnostic
        hata = $ErrorText
    }
}

function Invoke-VersionBoot {
    param(
        [Parameter(Mandatory = $true)][string]$Version,
        [Parameter(Mandatory = $true)][int]$Port,
        [Parameter(Mandatory = $true)][string]$JavaExecutable,
        [Parameter(Mandatory = $true)][string]$PluginJar
    )

    Write-Phase "Paper $Version boot testi (127.0.0.1:$Port)"
    $paperJar = Get-PaperJar -Version $Version
    $vaultJar = Get-VaultJar
    $runDir = Join-Path $RunRoot $Version
    if (Test-Path -LiteralPath $runDir) {
        Remove-Item -LiteralPath $runDir -Recurse -Force
    }
    $pluginsDir = Join-Path $runDir "plugins"
    New-Item -ItemType Directory -Force -Path $pluginsDir | Out-Null

    Copy-Item -LiteralPath $paperJar -Destination (Join-Path $runDir "paper.jar")
    Copy-Item -LiteralPath $PluginJar -Destination (Join-Path $pluginsDir "MCPiyasa.jar")
    Copy-Item -LiteralPath $vaultJar -Destination (Join-Path $pluginsDir "Vault.jar")
    Set-Content -LiteralPath (Join-Path $runDir "eula.txt") -Encoding ASCII -Value "eula=true"
    @(
        "server-ip=127.0.0.1"
        "server-port=$Port"
        "online-mode=false"
        "enable-query=false"
        "enable-rcon=false"
        "enable-status=false"
        "max-players=1"
        "view-distance=3"
        "simulation-distance=3"
        "motd=MCPiyasa boot matrix $Version"
    ) | Set-Content -LiteralPath (Join-Path $runDir "server.properties") -Encoding ASCII

    $logPath = Join-Path $runDir "console.log"
    $runnerErrorPath = Join-Path $runDir "runner-error.log"
    $stopSignal = Join-Path $runDir "stop.signal"
    $quotedSignal = $stopSignal.Replace("'", "''")
    $quotedJava = $JavaExecutable.Replace("'", "''")
    $quotedRunDir = $runDir.Replace("'", "''")
    $runnerScript = @"
`$ErrorActionPreference = 'Continue'
`$startInfo = New-Object System.Diagnostics.ProcessStartInfo
`$startInfo.FileName = '$quotedJava'
`$startInfo.Arguments = '-Xms256M -Xmx1024M -jar paper.jar --nogui'
`$startInfo.WorkingDirectory = '$quotedRunDir'
`$startInfo.UseShellExecute = `$false
`$startInfo.CreateNoWindow = `$true
`$startInfo.RedirectStandardInput = `$true
`$startInfo.RedirectStandardOutput = `$false
`$startInfo.RedirectStandardError = `$false
`$javaProcess = New-Object System.Diagnostics.Process
`$javaProcess.StartInfo = `$startInfo
try {
    if (-not `$javaProcess.Start()) {
        throw 'Java process baslatilamadi.'
    }
    `$javaProcess.StandardInput.AutoFlush = `$true
    while (-not `$javaProcess.HasExited -and -not (Test-Path -LiteralPath '$quotedSignal')) {
        Start-Sleep -Milliseconds 200
    }
    if (-not `$javaProcess.HasExited) {
        `$javaProcess.StandardInput.WriteLine('stop')
    }
    `$javaProcess.WaitForExit()
    exit `$javaProcess.ExitCode
} catch {
    [Console]::Error.WriteLine('RUNNER-ERROR: ' + `$_.Exception.Message)
    if (`$null -ne `$javaProcess -and -not `$javaProcess.HasExited) {
        `$javaProcess.Kill()
    }
    exit 1
}
"@
    $encodedCommand = [Convert]::ToBase64String([Text.Encoding]::Unicode.GetBytes($runnerScript))
    $powershellExe = Join-Path $PSHOME "powershell.exe"

    Write-Host "Java: $JavaExecutable"
    Write-Host "Log:  $logPath"
    $process = Start-Process `
        -FilePath $powershellExe `
        -ArgumentList @("-NoLogo", "-NoProfile", "-NonInteractive", "-ExecutionPolicy", "Bypass", "-EncodedCommand", $encodedCommand) `
        -WorkingDirectory $runDir `
        -RedirectStandardOutput $logPath `
        -RedirectStandardError $runnerErrorPath `
        -WindowStyle Hidden `
        -PassThru

    $stopwatch = [Diagnostics.Stopwatch]::StartNew()
    $readyAt = $null
    $timedOut = $false
    while ($stopwatch.Elapsed.TotalSeconds -lt $TimeoutSeconds) {
        Start-Sleep -Milliseconds 500
        $process.Refresh()
        $text = Read-LogText -Path $logPath
        $bootSeen = $text -match '(?i)Done \('
        $pluginSeen = $text -match 'MCPiyasa etkin\.'
        $economyDiagnosticSeen = $text -match '(?im)^.*\bWARN\b.*\[MCPiyasa\]\s+economy:.*\bVault\b.*$'

        if ($bootSeen -and $pluginSeen -and $economyDiagnosticSeen) {
            if ($null -eq $readyAt) {
                $readyAt = $stopwatch.Elapsed.TotalSeconds
            } elseif (($stopwatch.Elapsed.TotalSeconds - $readyAt) -ge 2) {
                break
            }
        }
        if ($process.HasExited) {
            break
        }
    }
    if (-not $process.HasExited -and $stopwatch.Elapsed.TotalSeconds -ge $TimeoutSeconds) {
        $timedOut = $true
    }

    # The runner converts this file signal into the literal `stop` line on Java stdin.
    Set-Content -LiteralPath $stopSignal -Encoding ASCII -Value "stop"
    if (-not $process.WaitForExit(30000)) {
        Write-Warning "[$Version] stop komutu sonrasi 30 saniyede kapanmadi; process tree zorla sonlandiriliyor."
        & taskkill.exe /PID $process.Id /T /F 2>&1 | Out-Null
        $process.WaitForExit(5000) | Out-Null
    }
    $stopwatch.Stop()

    $text = Read-LogText -Path $logPath
    $runnerError = Read-LogText -Path $runnerErrorPath
    $combinedText = $text + "`n" + $runnerError
    $bootPass = $text -match '(?i)Done \('
    $pluginPass = $text -match 'MCPiyasa etkin\.'
    $economyDiagnosticPass = $text -match '(?im)^.*\bWARN\b.*\[MCPiyasa\]\s+economy:.*\bVault\b.*$'
    $ourCrash = Test-MCPiyasaCrash -Text $combinedText
    $runnerFailed = $runnerError -match '(?m)^RUNNER-ERROR:'

    $errors = New-Object System.Collections.Generic.List[string]
    if ($timedOut -and -not $bootPass) { $errors.Add("120s boot timeout") }
    if (-not $bootPass) { $errors.Add("Done bulunamadi") }
    if (-not $pluginPass) { $errors.Add("MCPiyasa etkin bulunamadi") }
    if (-not $economyDiagnosticPass) { $errors.Add("ekonomi saglayicisi teshis uyarisi bulunamadi") }
    if ($ourCrash) { $errors.Add("com.mcpiyasa ERROR/SEVERE veya stack frame") }
    if ($runnerFailed) {
        $firstRunnerError = ($runnerError -split "`r?`n" | Where-Object { $_.Trim().Length -gt 0 } | Select-Object -First 1)
        $errors.Add("runner: $firstRunnerError")
    }

    $passed = $bootPass -and $pluginPass -and $economyDiagnosticPass -and -not $ourCrash -and -not $runnerFailed
    if ($passed) {
        Write-Host "[$Version] PASS" -ForegroundColor Green
        return New-Result -Version $Version -Boot "PASS" -MCPiyasa "PASS" -EconomyDiagnostic "PASS" -ErrorText "YOK"
    }

    Write-Warning "[$Version] FAIL: $($errors -join '; ')"
    return New-Result `
        -Version $Version `
        -Boot $(if ($bootPass) { "PASS" } else { "FAIL" }) `
        -MCPiyasa $(if ($pluginPass) { "PASS" } else { "FAIL" }) `
        -EconomyDiagnostic $(if ($economyDiagnosticPass) { "PASS" } else { "FAIL" }) `
        -ErrorText ("FAIL: " + ($errors -join "; "))
}

$pluginCandidates = @(Get-ChildItem -LiteralPath (Join-Path $RepoRoot "target") -Filter "MCPiyasa-*.jar" -File -ErrorAction SilentlyContinue |
    Where-Object { $_.Name -notmatch '^(original-|.*-(sources|javadoc)\.jar$)' } |
    Sort-Object LastWriteTimeUtc -Descending)
if ($null -eq $pluginCandidates -or $pluginCandidates.Count -eq 0) {
    Write-Error "Guncel MCPiyasa JAR bulunamadi. Once: bash mvnj -q package"
    exit 1
}
$pluginJar = $pluginCandidates[0].FullName
Write-Host "MCPiyasa JAR: $pluginJar"

$javaCache = @{}
$javaErrors = @{}
$requiredMajors = $Versions |
    Where-Object { $VersionJava.ContainsKey($_) } |
    ForEach-Object { [int]$VersionJava[$_] } |
    Sort-Object -Unique
foreach ($major in $requiredMajors) {
    try {
        Write-Phase "JDK $major hazirlaniyor"
        $javaCache[$major] = Resolve-Java -Major $major -Configured ([string]$ConfiguredJava[$major])
    } catch {
        $javaErrors[$major] = $_.Exception.Message
        Write-Warning "JDK $major INDIRME/COZUMLEME BASARISIZ: $($javaErrors[$major])"
    }
}

$results = New-Object System.Collections.Generic.List[object]
foreach ($version in $Versions) {
    if (-not $VersionJava.ContainsKey($version)) {
        Write-Warning "[$version] FAIL: desteklenen Java/port eslemesi yok."
        $results.Add((New-Result -Version $version -Boot "FAIL" -MCPiyasa "FAIL" -EconomyDiagnostic "FAIL" -ErrorText "FAIL: desteklenmeyen surum"))
        continue
    }

    $major = [int]$VersionJava[$version]
    if ($javaErrors.ContainsKey($major)) {
        $skipReason = "SKIP: JDK $major indirilemedi/cozumlenemedi: $($javaErrors[$major])"
        Write-Warning "[$version] $skipReason"
        $results.Add((New-Result -Version $version -Boot "SKIP" -MCPiyasa "SKIP" -EconomyDiagnostic "SKIP" -ErrorText $skipReason))
        continue
    }

    $port = if ($VersionPort.ContainsKey($version)) {
        [int]$VersionPort[$version]
    } else {
        25901 + $results.Count
    }
    try {
        $result = Invoke-VersionBoot `
            -Version $version `
            -Port $port `
            -JavaExecutable ([string]$javaCache[$major]) `
            -PluginJar $pluginJar
        $results.Add($result)
    } catch {
        $message = $_.Exception.Message
        Write-Warning "[$version] FAIL: $message"
        $results.Add((New-Result -Version $version -Boot "FAIL" -MCPiyasa "FAIL" -EconomyDiagnostic "FAIL" -ErrorText "FAIL: $message"))
    }
}

Write-Host "`nRESULTS TABLE"
Write-Host "surum | boot | mcpiyasa | ekonomi-teshis | hata"
foreach ($result in $results) {
    Write-Host ("{0} | {1} | {2} | {3} | {4}" -f `
        $result.surum, $result.boot, $result.mcpiyasa, $result.ekonomiTeshis, $result.hata)
}

$bootedResults = @($results | Where-Object { $_.boot -ne "SKIP" })
if ($bootedResults.Count -eq 0) {
    Write-Host "FAIL: Hicbir surum boot edilmedi; tum surumler SKIP veya matris bos." -ForegroundColor Red
    exit 1
}

$hasFailure = $results | Where-Object {
    $_.boot -ne "PASS" -or $_.mcpiyasa -ne "PASS" -or $_.ekonomiTeshis -ne "PASS" -or $_.hata -ne "YOK"
}
if ($null -ne $hasFailure) {
    exit 1
}
exit 0
