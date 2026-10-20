# Publie sur GitHub les releases d'Origami qui manquent, depuis le PC (gh connecté au compte du dépôt).
# Usage : powershell -ExecutionPolicy Bypass -File publish-releases.ps1 -Work <dossier de travail>
#
# Où sont les APK : la branche temporaire apk-transfer (plusieurs APK) et les branches apk-<version>
# (une par version). La préversion en cours (branche preview) n'est jamais publiée en release.
# Notes : docs/releases/<version>.md de la branche -Notes (main par défaut).
# Une release déjà publiée est sautée. À la fin, sans échec, les branches temporaires sont supprimées.
param(
  [Parameter(Mandatory=$true)][string]$Work,
  [string]$Repo = "orikami-dev/origami",
  [string]$Notes = "main",
  [switch]$KeepBranches
)
$ErrorActionPreference = "Stop"
New-Item -ItemType Directory -Force -Path $Work | Out-Null
Set-Location $Work

# Notes de version et liste des branches temporaires.
if (Test-Path "notes") { Remove-Item -Recurse -Force "notes" }
git clone --quiet --depth 1 --branch $Notes "https://github.com/$Repo" notes
$branches = git ls-remote --heads "https://github.com/$Repo" | ForEach-Object { ($_ -split "refs/heads/")[1] }
$temp = @($branches | Where-Object { $_ -eq "apk-transfer" -or $_ -match '^apk-\d' })

# Version de la préversion en cours : jamais publiée ici.
$preview = $null
try { $preview = (Invoke-RestMethod "https://raw.githubusercontent.com/$Repo/preview/preview.json").version } catch { }

# Récupère les APK des branches temporaires (dépôt public : lien direct, sans clone).
New-Item -ItemType Directory -Force -Path "apk" | Out-Null
foreach ($b in $temp) {
  Write-Host "Lecture de $b"
  $tree = gh api "repos/$Repo/git/trees/$b" --jq '.tree[].path'
  foreach ($f in $tree) {
    if ($f -notlike "*.apk") { continue }
    $dest = Join-Path "apk" $f
    if (-not (Test-Path $dest)) {
      Write-Host "  téléchargement $f"
      Invoke-WebRequest "https://raw.githubusercontent.com/$Repo/$b/$f" -OutFile $dest -UseBasicParsing
    }
  }
}

$existing = gh release list --repo $Repo --limit 500 --json tagName --jq ".[].tagName"
$apks = @(Get-ChildItem "apk\*.apk" | ForEach-Object {
  $v = ($_.Name -replace '^(foldplay|origami)-', '') -replace '-debug\.apk$', ''
  if ($v -match '^\d+(\.\d+){1,3}$') { [pscustomobject]@{ Version = $v; Path = $_.FullName; Key = [version]$v } }
} | Where-Object { $_ -and $_.Version -ne $preview } | Sort-Object Key)
$todo = @($apks | Where-Object { $existing -notcontains "v$($_.Version)" })
if ($todo.Count -eq 0) { Write-Host "Rien à publier." }
$latest = ($apks | Select-Object -Last 1).Version
$i = 0
$failed = @()
foreach ($a in $todo) {
  $i++
  $tag = "v$($a.Version)"
  Write-Progress -Activity "Releases Origami" -Status "$tag ($i/$($todo.Count))" -PercentComplete (100 * $i / $todo.Count)
  $nf = "notes\docs\releases\$($a.Version).md"
  if (-not (Test-Path $nf)) { $nf = Join-Path $Work "notes-$($a.Version).md"; "Origami $($a.Version)" | Set-Content -Encoding UTF8 $nf }
  $ga = @($tag, $a.Path, "--repo", $Repo, "--title", "Origami $($a.Version)", "--notes-file", $nf, "--target", $Notes)
  if ($a.Version -eq $latest) { $ga += "--latest" } else { $ga += "--latest=false" }
  gh release create @ga
  if ($LASTEXITCODE -ne 0) { $failed += $tag; Write-Host "$tag ÉCHEC" } else { Write-Host "$tag publiée" }
}
Write-Progress -Activity "Releases Origami" -Completed

if ($failed.Count -eq 0) {
  if (-not $KeepBranches) {
    foreach ($b in $temp) { git -C notes push --quiet origin --delete $b; Write-Host "Branche $b supprimée" }
  }
  Write-Host "Terminé : $($todo.Count) releases publiées, dernière $latest"
} else {
  Write-Host "Échecs : $($failed -join ', ') (branches temporaires gardées, relancer le script)"
  exit 1
}
