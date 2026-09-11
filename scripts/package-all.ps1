param(
    [string]$Version = "dev"
)

$ErrorActionPreference = "Stop"

$repoRoot = Split-Path -Parent $PSScriptRoot
$goExe = "C:\Program Files\Go\bin\go.exe"
if (-not (Test-Path $goExe)) {
    $goExe = (Get-Command go | Select-Object -ExpandProperty Source)
}

$distRoot = Join-Path $repoRoot "dist"
$binRoot = Join-Path $repoRoot "bin"

New-Item -ItemType Directory -Force -Path $distRoot | Out-Null
New-Item -ItemType Directory -Force -Path $binRoot | Out-Null

function New-TarGzPackage {
    param(
        [string]$TargetOS,
        [string]$Arch
    )

    $platformName = "TextbookGo-$TargetOS-$Arch"
    $stageRoot = Join-Path $distRoot $platformName
    $archivePath = Join-Path $distRoot "$platformName.tar.gz"
    $binaryName = "TextbookGo"
    $binarySource = Join-Path $binRoot $platformName
    $binaryTarget = Join-Path $stageRoot $binaryName

    Remove-Item -Recurse -Force $stageRoot -ErrorAction SilentlyContinue
    New-Item -ItemType Directory -Force -Path $stageRoot | Out-Null

    $env:GOOS = $TargetOS
    $env:GOARCH = $Arch
    & $goExe build -trimpath -buildvcs=false -ldflags "-s -w -X main.version=$Version" -o $binarySource .\cmd\boooookdown-web
    if ($LASTEXITCODE -ne 0) { throw "go build 失败：$TargetOS-$Arch (exit $LASTEXITCODE)" }

    Copy-Item $binarySource $binaryTarget -Force
    Copy-Item (Join-Path $repoRoot "README.md") (Join-Path $stageRoot "README.md")

    if (Test-Path $archivePath) {
        Remove-Item -Force $archivePath
    }

    # 必须用 Windows 自带 bsdtar：若 PATH 里 MSYS GNU tar 抢先，会把 D:\ 当远程主机名静默失败
    $tarExe = Join-Path $env:SystemRoot "System32\tar.exe"
    & $tarExe -czf $archivePath -C $distRoot $platformName
    if ($LASTEXITCODE -ne 0) { throw "tar 打包失败：$platformName (exit $LASTEXITCODE)" }
}

& (Join-Path $repoRoot "scripts\build-windows.ps1") -Version $Version

# ── Win7 兼容轨：go1.20.14（末代支持 Win7 的 Go）同源编译，产物 Windows 7 SP1 / 8.x ──
$go120Root = "D:\tools\go1.20.14"
$go120 = Join-Path $go120Root "bin\go.exe"
if (Test-Path $go120) {
    $platformName = "TextbookGo-windows7-amd64"
    $stageRoot = Join-Path $distRoot $platformName
    $zipPath = Join-Path $distRoot "$platformName.zip"
    $binSource = Join-Path $binRoot $platformName
    $env:GOOS = "windows"; $env:GOARCH = "amd64"
    $oldRoot = $env:GOROOT
    $env:GOROOT = $go120Root
    & $go120 build -trimpath -buildvcs=false -ldflags "-s -w -X main.version=$Version" -o $binSource .\cmd\boooookdown-web
    if ($LASTEXITCODE -ne 0) { throw "go1.20.14 构建失败 (exit $LASTEXITCODE)" }
    $env:GOROOT = $oldRoot

    Remove-Item -Recurse -Force $stageRoot -ErrorAction SilentlyContinue
    New-Item -ItemType Directory -Force -Path $stageRoot | Out-Null
    Copy-Item $binSource (Join-Path $stageRoot "TextbookGo.exe") -Force
    Copy-Item (Join-Path $repoRoot "README.md") (Join-Path $stageRoot "README.md") -Force
    if (Test-Path $zipPath) { Remove-Item -Force $zipPath }
    Compress-Archive -Path "$stageRoot\*" -DestinationPath $zipPath
    Write-Host "Win7 轨: $zipPath"
} else {
    Write-Warning "未找到 go1.20.14（$go120），跳过 Win7 兼容轨"
}

New-TarGzPackage -TargetOS "darwin" -Arch "amd64"
New-TarGzPackage -TargetOS "darwin" -Arch "arm64"
New-TarGzPackage -TargetOS "linux" -Arch "amd64"

Remove-Item Env:GOOS -ErrorAction SilentlyContinue
Remove-Item Env:GOARCH -ErrorAction SilentlyContinue

Get-ChildItem $distRoot | Select-Object FullName, Length, LastWriteTime
