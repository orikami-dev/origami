# Publie les releases privées d'Origami avec gh (connecté au compte du dépôt).
# Usage : powershell -ExecutionPolicy Bypass -File publish-releases.ps1 -Work <dossier de travail>
# Lit les APK de la branche temporaire apk-transfer et les notes de docs/releases (branche main).
# Une release déjà publiée est sautée. À la fin, la branche apk-transfer est supprimée.
param([Parameter(Mandatory=$true)][string]$Work, [string]$Repo = "orikami-dev/origami")
$ErrorActionPreference = "Stop"
New-Item -ItemType Directory -Force -Path $Work | Out-Null
Set-Location $Work
if (-not (Test-Path "main")) { git clone --depth 1 "https://github.com/$Repo" main }
if (-not (Test-Path "apk")) { git clone --depth 1 --branch apk-transfer "https://github.com/$Repo" apk }
$existing = gh release list --repo $Repo --limit 500 --json tagName --jq ".[].tagName"
$apks = Get-ChildItem "apk\*.apk" | ForEach-Object {
  $v = ($_.Name -replace '^(foldplay|origami)-', '') -replace '-debug\.apk$', ''
  [pscustomobject]@{ Version = $v; Path = $_.FullName; Key = [version]$v }
} | Sort-Object Key
$latest = ($apks | Select-Object -Last 1).Version
$i = 0
$failed = @()
foreach ($a in $apks) {
  $i++
  $tag = "v$($a.Version)"
  Write-Progress -Activity "Releases Origami" -Status "$tag ($i/$($apks.Count))" -PercentComplete (100 * $i / $apks.Count)
  if ($existing -contains $tag) { Write-Host "$tag déjà publiée"; continue }
  $notes = "main\docs\releases\$($a.Version).md"
  $ga = @($tag, $a.Path, "--repo", $Repo, "--title", "Origami $($a.Version)", "--notes-file", $notes, "--target", "main")
  if ($a.Version -eq $latest) { $ga += "--latest" } else { $ga += "--latest=false" }
  gh release create @ga
  if ($LASTEXITCODE -ne 0) { $failed += $tag; Write-Host "$tag ÉCHEC" } else { Write-Host "$tag publiée" }
}
gh repo edit $Repo --description "Origami par KAMI-WORKS : émulation et jeux réunis pour téléphones pliants. Déplie. Joue." --enable-wiki=false
if ($failed.Count -eq 0) {
  git -C main push origin --delete apk-transfer
  Write-Host "Terminé : $($apks.Count) versions, dernière $latest"
} else {
  Write-Host "Échecs : $($failed -join ', ') (branche apk-transfer gardée, relancer le script)"
  exit 1
}
