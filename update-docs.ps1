# Publishes the updated README and release notes only: no apps are rebuilt, nothing is sent to any TV.
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location $root
$env:Path = [Environment]::GetEnvironmentVariable('Path', 'Machine') + ';' + [Environment]::GetEnvironmentVariable('Path', 'User')
git add README.md RELEASE_NOTES.md update-docs.ps1
git commit -m "New Downloader code 8076512 and new GitHub name Kyzasoze" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`nClaude-Session: https://claude.ai/code/session_012VaCAWduNpjvvdaCHhkD1L"
git push
gh release edit v1.0 --repo Kyzasoze/makkah-tower-home --notes-file "$root\RELEASE_NOTES.md"
Write-Host 'README and release notes updated on GitHub. The apps and the download links are unchanged.' -ForegroundColor Green
Write-Host 'https://github.com/Kyzasoze/makkah-tower-home'
