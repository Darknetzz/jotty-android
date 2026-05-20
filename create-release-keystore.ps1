# Create or configure the Jotty Android release keystore (gitignored).
# Usage:
#   .\create-release-keystore.ps1              # create keystore + keystore.properties
#   .\create-release-keystore.ps1 -ConfigureOnly   # keystore exists; only write keystore.properties
#   .\create-release-keystore.ps1 -Force       # replace existing keystore (breaks updates signed with old key)

param(
    [switch]$ConfigureOnly,
    [switch]$Force
)

$ErrorActionPreference = "Stop"
Set-Location $PSScriptRoot

$keystoreFile = "jotty-release.keystore"
$propsFile = "keystore.properties"
$keyAlias = "jotty"
$validityDays = 10000

function Find-Keytool {
    $cmd = Get-Command keytool -ErrorAction SilentlyContinue
    if ($cmd) { return $cmd.Source }
    foreach ($javaHome in @($env:JAVA_HOME, "C:\Program Files\Eclipse Adoptium\jdk-25.0.3.9-hotspot")) {
        if ($javaHome) {
            $candidate = Join-Path $javaHome "bin\keytool.exe"
            if (Test-Path $candidate) { return $candidate }
        }
    }
    throw "keytool not found. Install JDK 17+ and ensure keytool is on PATH."
}

function New-RandomPassword {
    param([int]$Length = 24)
    $chars = "abcdefghijkmnopqrstuvwxyzABCDEFGHJKLMNPQRSTUVWXYZ23456789"
    -join (1..$Length | ForEach-Object { $chars[(Get-Random -Maximum $chars.Length)] })
}

function Write-KeystoreProperties {
    param(
        [string]$StorePass,
        [string]$KeyPass
    )
    @"
storeFile=$keystoreFile
storePassword=$StorePass
keyAlias=$keyAlias
keyPassword=$KeyPass
"@ | Set-Content -Path $propsFile -Encoding UTF8
}

function Test-KeystorePassword {
    param(
        [string]$Keytool,
        [string]$StorePass
    )
    & $Keytool -list -keystore $keystoreFile -storepass $StorePass 2>&1 | Out-Null
    return $LASTEXITCODE -eq 0
}

$keytool = Find-Keytool
Write-Host "Using keytool: $keytool" -ForegroundColor DarkGray

if (Test-Path $keystoreFile) {
    if ($ConfigureOnly -or (-not $Force)) {
        if (-not $ConfigureOnly -and -not $Force) {
            Write-Host "Keystore already exists: $keystoreFile" -ForegroundColor Yellow
            Write-Host "  - Run with -ConfigureOnly to create keystore.properties (you know the password)."
            Write-Host "  - Run with -Force to replace it (only if you never shipped APKs with this key)."
            exit 1
        }
    }
}

if ($ConfigureOnly) {
    if (-not (Test-Path $keystoreFile)) {
        Write-Error "Keystore not found: $keystoreFile. Run without -ConfigureOnly to create one."
    }
    $storePass = Read-Host "Keystore password (storePassword)" -AsSecureString
    $storePlain = [Runtime.InteropServices.Marshal]::PtrToStringAuto(
        [Runtime.InteropServices.Marshal]::SecureStringToBSTR($storePass))
    if (-not (Test-KeystorePassword -Keytool $keytool -StorePass $storePlain)) {
        Write-Error "Password incorrect or keystore unreadable."
    }
    $keyPassSecure = Read-Host "Key password (keyPassword; Enter if same as keystore)" -AsSecureString
    $keyPlain = if ($keyPassSecure.Length -eq 0) {
        $storePlain
    } else {
        [Runtime.InteropServices.Marshal]::PtrToStringAuto(
            [Runtime.InteropServices.Marshal]::SecureStringToBSTR($keyPassSecure))
    }
    Write-KeystoreProperties -StorePass $storePlain -KeyPass $keyPlain
    Write-Host "Wrote $propsFile" -ForegroundColor Green
    exit 0
}

if ((Test-Path $keystoreFile) -and $Force) {
    $answer = Read-Host "Replace $keystoreFile? Type YES to continue"
    if ($answer -ne "YES") { exit 1 }
    Remove-Item $keystoreFile -Force
}

$dname = Read-Host "Certificate DN [CN=Jotty Android, OU=Mobile, O=Jotty, L=Unknown, ST=Unknown, C=US]"
if ([string]::IsNullOrWhiteSpace($dname)) {
    $dname = "CN=Jotty Android, OU=Mobile, O=Jotty, L=Unknown, ST=Unknown, C=US"
}

$useGenerated = Read-Host "Generate random passwords and save to $propsFile? [Y/n]"
if ($useGenerated -eq "" -or $useGenerated -match "^[Yy]") {
    $storePlain = New-RandomPassword
    $keyPlain = $storePlain
    Write-Host ""
    Write-Host "SAVE THESE PASSWORDS NOW (password manager). They are also in $propsFile :" -ForegroundColor Cyan
    Write-Host "  storePassword / keyPassword: $storePlain"
    Write-Host ""
} else {
    $storePass = Read-Host "Keystore password" -AsSecureString
    $storePlain = [Runtime.InteropServices.Marshal]::PtrToStringAuto(
        [Runtime.InteropServices.Marshal]::SecureStringToBSTR($storePass))
    $keyPassSecure = Read-Host "Key password (Enter if same)" -AsSecureString
    $keyPlain = if ($keyPassSecure.Length -eq 0) {
        $storePlain
    } else {
        [Runtime.InteropServices.Marshal]::PtrToStringAuto(
            [Runtime.InteropServices.Marshal]::SecureStringToBSTR($keyPassSecure))
    }
}

& $keytool -genkeypair -v `
    -keystore $keystoreFile `
    -alias $keyAlias `
    -keyalg RSA `
    -keysize 2048 `
    -validity $validityDays `
    -storepass $storePlain `
    -keypass $keyPlain `
    -dname $dname

if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

Write-KeystoreProperties -StorePass $storePlain -KeyPass $keyPlain

Write-Host ""
Write-Host "Created:" -ForegroundColor Green
Write-Host "  $keystoreFile"
Write-Host "  $propsFile"
Write-Host ""
Write-Host "Next — GitHub Actions secrets (repo Settings → Secrets):" -ForegroundColor Cyan
Write-Host "  ANDROID_KEYSTORE_B64      (run below, paste full line)"
Write-Host "  ANDROID_KEYSTORE_PASSWORD $storePlain"
Write-Host "  ANDROID_KEY_ALIAS         $keyAlias"
Write-Host "  ANDROID_KEY_PASSWORD      $keyPlain"
Write-Host ""
Write-Host "Base64 for ANDROID_KEYSTORE_B64:"
$b64 = [Convert]::ToBase64String([IO.File]::ReadAllBytes((Resolve-Path $keystoreFile)))
Write-Host $b64
Write-Host ""
Write-Host "Back up $keystoreFile and passwords offline. Never commit them." -ForegroundColor Yellow
