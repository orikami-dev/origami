# Marque les mises à jour majeures dans la liste des releases (titre ⭐ et notes à jour).
# Usage : powershell -ExecutionPolicy Bypass -File mark-majors.ps1 -Work <dossier de travail>
param([Parameter(Mandatory=$true)][string]$Work, [string]$Repo = "orikami-dev/origami")
$ErrorActionPreference = "Stop"
Set-Location $Work
if (-not (Test-Path "main")) { git clone --depth 1 "https://github.com/$Repo" main } else { git -C main pull --ff-only }
$majors = @("0.1.0","0.8.0","0.21.0","0.23.0","0.25.0","0.29.0","0.40.0","0.41.0","0.50.0","0.50.1","0.60.0")
foreach ($v in $majors) {
  gh release edit "v$v" --repo $Repo --title "⭐ Origami $v · Mise à jour majeure" --notes-file "main\docs\releases\$v.md"
  if ($LASTEXITCODE -eq 0) { Write-Host "v$v marquée" } else { Write-Host "v$v ÉCHEC" }
}
