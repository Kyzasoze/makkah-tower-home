# Puts the share edition on GitHub: the code as a public repository, and the three apps as a release your
# followers can download. Needs a free GitHub account. It installs Git and the GitHub tool (gh) if they are
# missing, and opens the browser once so you can sign in.
# Usage: powershell -ExecutionPolicy Bypass -File publish-github.ps1 [-Repo makkah-tower-home]
param([string]$Repo = 'makkah-tower-home', [string]$Version = 'v1.0')
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location $root
function Have($cmd) { [bool](Get-Command $cmd -ErrorAction SilentlyContinue) }
function Refresh-Path { $env:Path = [Environment]::GetEnvironmentVariable('Path', 'Machine') + ';' + [Environment]::GetEnvironmentVariable('Path', 'User') }
if (-not (Have git)) { Write-Host 'Installing Git...'; winget install --id Git.Git -e --accept-package-agreements --accept-source-agreements; Refresh-Path }
if (-not (Have gh)) { Write-Host 'Installing the GitHub tool...'; winget install --id GitHub.cli -e --accept-package-agreements --accept-source-agreements; Refresh-Path }
if (-not (Have git) -or -not (Have gh)) { throw 'Git or the GitHub tool could not be installed. Close this window, open a new one and run the script again.' }

$ErrorActionPreference = 'Continue'
gh auth status 2>&1 | Out-Null
if ($LASTEXITCODE -ne 0) {
    Write-Host 'Sign in to GitHub in the browser that opens (choose GitHub.com, HTTPS, and "Login with a web browser").' -ForegroundColor Yellow
    gh auth login --hostname github.com --git-protocol https --web
}
$ErrorActionPreference = 'Stop'
$user = (gh api user --jq .login).Trim()
Write-Host "Signed in as $user" -ForegroundColor Green

if (-not (Test-Path "$root\release\MakkahTowerHome.apk")) { & "$root\build-release.ps1" }

if (-not (Test-Path "$root\.git")) {
    git init -b main | Out-Null
    git config user.name $user
    git config user.email "$user@users.noreply.github.com"
}
git add -A
git commit -m "Makkah Tower Home $Version" 2>&1 | Out-Null
$ErrorActionPreference = 'Continue'
gh repo view "$user/$Repo" 2>&1 | Out-Null
$exists = $LASTEXITCODE -eq 0
$ErrorActionPreference = 'Stop'
if (-not $exists) {
    gh repo create $Repo --public --description 'A custom Android TV / Google TV home screen with prayer times, a live Makkah clock tower, weather, match day and 300 free wallpapers.' --source . --remote origin --push
} else {
    git push -u origin main
}
$ErrorActionPreference = 'Continue'
gh release view $Version --repo "$user/$Repo" 2>&1 | Out-Null
$hasRelease = $LASTEXITCODE -eq 0
$ErrorActionPreference = 'Stop'
$apks = @("$root\release\MakkahTowerHome.apk", "$root\release\SalahTimes.apk", "$root\release\Weather.apk")
if ($hasRelease) { gh release upload $Version @apks --repo "$user/$Repo" --clobber }
else { gh release create $Version @apks --repo "$user/$Repo" --title "Makkah Tower Home $Version" --notes-file "$root\RELEASE_NOTES.md" }

$page = "https://github.com/$user/$Repo"
$latest = "https://github.com/$user/$Repo/releases/latest"
@"
Your project page:      $page
Downloads (story link): $latest
Direct app links (for Downloader codes at aftvnews.com/downloader):
  $latest/download/SalahTimes.apk
  $latest/download/Weather.apk
  $latest/download/MakkahTowerHome.apk
"@ | Tee-Object -FilePath "$root\SHARE-LINKS.txt"
Write-Host 'Published. The links are saved in SHARE-LINKS.txt' -ForegroundColor Green
