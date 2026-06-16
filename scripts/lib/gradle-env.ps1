# Shared Java, Android SDK, and Gradle wrapper setup for local build/CI scripts.
# Dot-source from repo scripts: . (Join-Path $PSScriptRoot "lib/gradle-env.ps1")

function Initialize-JottyGradleEnv {
    param(
        [Parameter(Mandatory = $true)]
        [string]$RepoRoot
    )

    Set-Location $RepoRoot

    $wrapperJar = Join-Path $RepoRoot "gradle\wrapper\gradle-wrapper.jar"
    $wrapperProps = Join-Path $RepoRoot "gradle\wrapper\gradle-wrapper.properties"
    $gradleTagVer = "9.1.0"
    if (Test-Path $wrapperProps) {
        $line = Get-Content $wrapperProps | Where-Object { $_ -match '^distributionUrl=' } | Select-Object -First 1
        if ($line -match 'gradle-([0-9][0-9.]+)-') {
            $gradleTagVer = $Matches[1]
        }
    }
    $wrapperUrl = "https://raw.githubusercontent.com/gradle/gradle/v$gradleTagVer/gradle/wrapper/gradle-wrapper.jar"

    function Test-WrapperJarUsable {
        if (-not (Test-Path $wrapperJar)) { return $false }
        try {
            Add-Type -AssemblyName System.IO.Compression.FileSystem
            $zip = [System.IO.Compression.ZipFile]::OpenRead((Resolve-Path $wrapperJar))
            $entry = $zip.Entries | Where-Object { $_.FullName -eq "META-INF/MANIFEST.MF" }
            if (-not $entry) { $zip.Dispose(); return $false }
            $sr = New-Object System.IO.StreamReader($entry.Open())
            $manifest = $sr.ReadToEnd()
            $sr.Close()
            $zip.Dispose()
            return $manifest -match "(?m)^Main-Class:"
        } catch {
            return $false
        }
    }

    if (-not (Test-WrapperJarUsable)) {
        Write-Host "Gradle wrapper JAR missing or invalid (re-downloading for Gradle $gradleTagVer)..." -ForegroundColor Yellow
        try {
            $ProgressPreference = "SilentlyContinue"
            Invoke-WebRequest -Uri $wrapperUrl -OutFile $wrapperJar -UseBasicParsing
            if (-not (Test-WrapperJarUsable)) {
                throw "Downloaded wrapper JAR still invalid. Delete $wrapperJar and retry, or run: gradle wrapper"
            }
            Write-Host "Wrapper JAR installed." -ForegroundColor Green
        } catch {
            Write-Host $_.Exception.Message -ForegroundColor Red
            Write-Host "Install Gradle (e.g. winget install Gradle.Gradle) and run: gradle wrapper" -ForegroundColor Red
            exit 1
        }
    }

    function Get-JavaVersion {
        param([string]$JavaHome)
        $env:JAVA_HOME = $JavaHome
        $out = (& "$JavaHome\bin\java.exe" -version 2>&1) | Out-String
        if ($out -match '"(\d+)(?:\.(\d+))?') {
            $major = [int]$Matches[1]
            $minor = if ($Matches[2]) { [int]$Matches[2] } else { 0 }
            if ($major -eq 1) { return $minor }
            return $major
        }
        return 0
    }

    $minJava = 11
    $needJava = $true
    if ($env:JAVA_HOME -and (Test-Path "$env:JAVA_HOME\bin\java.exe")) {
        $v = Get-JavaVersion -JavaHome $env:JAVA_HOME
        if ($v -ge $minJava) { $needJava = $false }
    }

    if ($needJava) {
        $candidates = @(
            "${env:LOCALAPPDATA}\Programs\Android Studio\jbr",
            "C:\Program Files\Android\Android Studio\jbr",
            "${env:ProgramFiles}\Android\Android Studio\jbr",
            "${env:ProgramFiles(x86)}\Android\Android Studio\jbr",
            "${env:LOCALAPPDATA}\Microsoft\jbr-17",
            "C:\Program Files\Microsoft\jdk-17*",
            "C:\Program Files\Eclipse Adoptium\jdk-17*",
            "C:\Program Files\Java\jdk-17*",
            "C:\Program Files\Java\jdk-21*",
            "C:\Program Files\Java\jdk-11*"
        )
        foreach ($c in $candidates) {
            $dir = $null
            if ($c -match '\*') { $dir = Get-Item $c -ErrorAction SilentlyContinue | Select-Object -First 1 }
            else { $dir = Get-Item $c -ErrorAction SilentlyContinue }
            if ($dir -and $dir.PSIsContainer -and (Test-Path "$($dir.FullName)\bin\java.exe")) {
                $v = Get-JavaVersion -JavaHome $dir.FullName
                if ($v -ge $minJava) {
                    $env:JAVA_HOME = $dir.FullName
                    Write-Host "Using Java $v from: $($dir.FullName)" -ForegroundColor Green
                    break
                }
            }
        }
        if (-not $env:JAVA_HOME -or (Get-JavaVersion -JavaHome $env:JAVA_HOME) -lt $minJava) {
            Write-Host "This build requires Java $minJava or newer." -ForegroundColor Red
            exit 1
        }
    }

    $props = Join-Path $RepoRoot "local.properties"
    $sdk = $null
    if (Test-Path $props) {
        $line = Get-Content $props | Where-Object { $_ -match '^\s*sdk\.dir\s*=' } | Select-Object -First 1
        if ($line -match 'sdk\.dir\s*=\s*(.+)') {
            $sdk = $Matches[1].Trim()
        }
    }
    if ($sdk -and (Test-Path $sdk)) {
        if (-not $env:ANDROID_HOME) { $env:ANDROID_HOME = $sdk }
        if (-not $env:ANDROID_SDK_ROOT) { $env:ANDROID_SDK_ROOT = $sdk }
        return
    }

    $sdkCandidates = @(
        $env:ANDROID_HOME
        $env:ANDROID_SDK_ROOT
        (Join-Path $env:LOCALAPPDATA "Android\Sdk")
        (Join-Path $env:USERPROFILE "Android\Sdk")
    ) | Where-Object { $_ -and (Test-Path $_) }

    foreach ($c in $sdkCandidates) {
        if ((Test-Path (Join-Path $c "platform-tools")) -or (Test-Path (Join-Path $c "build-tools"))) {
            $sdk = $c
            break
        }
    }

    if ($sdk) {
        $env:ANDROID_HOME = $sdk
        $env:ANDROID_SDK_ROOT = $sdk
        $sdkProp = ($sdk -replace '\\', '/')
        if (-not (Test-Path $props) -or -not ((Get-Content $props -ErrorAction SilentlyContinue | Where-Object { $_ -match '^\s*sdk\.dir\s*=' }))) {
            Add-Content -Path $props -Value "sdk.dir=$sdkProp"
            Write-Host "Wrote Android SDK path to local.properties: $sdk" -ForegroundColor Green
        }
        return
    }

    Write-Host "Android SDK not found. Install Android Studio or set ANDROID_HOME / sdk.dir in local.properties." -ForegroundColor Red
    exit 1
}

function Invoke-JottyGradlew {
    param(
        [Parameter(Mandatory = $true)]
        [string[]]$Tasks,
        [hashtable]$Properties = @{}
    )

    $args = @("--no-daemon") + $Tasks
    foreach ($key in $Properties.Keys) {
        $args += "-P$key=$($Properties[$key])"
    }
    & (Join-Path (Get-Location) "gradlew.bat") @args
    if ($LASTEXITCODE -ne 0) {
        throw "Gradle failed: $($Tasks -join ' ')"
    }
}

function Get-GradleProperty {
    param(
        [string]$RepoRoot,
        [string]$Name
    )
    $gradlePath = Join-Path $RepoRoot "gradle.properties"
    $line = Get-Content $gradlePath | Where-Object { $_ -match "^$Name=" } | Select-Object -First 1
    if ($line -match "^$Name=(.+)$") {
        return $Matches[1].Trim()
    }
    throw "$Name not found in gradle.properties"
}

function Test-ReleaseKeystoreConfigured {
    param([string]$RepoRoot)
    $keystoreProps = Join-Path $RepoRoot "keystore.properties"
    if (-not (Test-Path $keystoreProps)) { return $false }
    $content = Get-Content $keystoreProps -Raw
    if ($content -match 'your-keystore-password|your-key-password') { return $false }
    $storeFile = $null
    foreach ($line in (Get-Content $keystoreProps)) {
        if ($line -match '^\s*storeFile\s*=\s*(.+)$') {
            $storeFile = $Matches[1].Trim()
            break
        }
    }
    if (-not $storeFile) { return $false }
    $path = if ([System.IO.Path]::IsPathRooted($storeFile)) { $storeFile } else { Join-Path $RepoRoot $storeFile }
    return Test-Path $path
}
