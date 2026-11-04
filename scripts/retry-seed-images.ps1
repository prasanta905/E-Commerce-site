# Retry the 9 image slots that failed in the first pass with alternate photo IDs.
$ErrorActionPreference = 'Continue'
$dest = Join-Path $PSScriptRoot "..\backend\uploads\products"

$retry = [ordered]@{
  "nexora-ultrabook-14-i7-1.jpg"            = "photo-1517336714731-489689fd1ca8"
  "nexora-ultrabook-14-i7-2.jpg"            = "photo-1498050108023-c5249f4df085"
  "orbitrio-soundstage-900-2.jpg"           = "photo-1545454675-3531b543be5d"
  "nexora-smartwatch-x2-2.jpg"              = "photo-1508685096489-7aacd43bd3b1"
  "zenava-chiffon-maxi-dress-beige-2.jpg"   = "photo-1515372039744-b8f02a3aeeda"
  "aurora-linen-coord-set-black-1.jpg"      = "photo-1445205170230-053b83016050"
  "forge-flame-triply-saucepan-2l-1.jpg"    = "photo-1522708323590-d24dbb6b0267"
  "nexora-smart-bulb-pack2-1.jpg"           = "photo-1565814329452-e1efa11c5b89"
  "aurora-smart-plug-16a-pack2-1.jpg"       = "photo-1558002038-105590734825"
}

$stillFailed = @()
foreach ($name in $retry.Keys) {
  $file = Join-Path $dest $name
  try {
    $url = "https://images.unsplash.com/$($retry[$name])?auto=format&fit=crop&w=900&q=70"
    Invoke-WebRequest -Uri $url -OutFile $file -UseBasicParsing -TimeoutSec 30
    if ((Get-Item $file).Length -lt 5000) { throw "too small" }
    Write-Host "RETRY-OK $name"
  } catch { $stillFailed += $name; Write-Host "RETRY-FAIL $name" }
}
Write-Host "STILL_FAILED=$($stillFailed.Count)"
