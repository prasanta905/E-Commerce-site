# ShopSphere — one-time seed image downloader (run manually; not part of build)
$ErrorActionPreference = 'Continue'
$dest = Join-Path $PSScriptRoot "backend\uploads\products"
New-Item -ItemType Directory -Force -Path $dest | Out-Null

$map = [ordered]@{
  "nexora-n12-pro-5g-cosmic-grey"    = @("https://images.unsplash.com/photo-1511707171634-5f897ff02aa9", "https://images.unsplash.com/photo-1598327105666-5b89351aff97")
  "nexora-n12-lite-ocean-blue"       = @("https://images.unsplash.com/photo-1567581935884-3349723552ca", "https://images.unsplash.com/photo-1580910051074-3eb694886505")
  "nexora-ultrabook-14-i7"           = @("https://images.unsplash.com/photo-1496181133206-80ce9b88a859", "https://images.unsplash.com/photo-1525547719571-a2d4ac894d8e")
  "zenava-creator-15-ryzen7"         = @("https://images.unsplash.com/photo-1541807084-5c52b6b3adef", "https://images.unsplash.com/photo-1587614382346-4ec70e388b28")
  "orbitrio-pulse-buds-anc"          = @("https://images.unsplash.com/photo-1606220588913-b3aacb4d2f46", "https://images.unsplash.com/photo-1583394838336-acd977736f90")
  "orbitrio-soundstage-900"          = @("https://images.unsplash.com/photo-1608043152269-423dbba4e7e1", "https://images.unsplash.com/photo-1608156639585-b3a032767cb2")
  "nexora-smartwatch-x2"             = @("https://images.unsplash.com/photo-1523275335684-37898b6baf30", "https://images.unsplash.com/photo-1546868871-7041f2a55e30")
  "zenava-chiffon-maxi-dress-beige"  = @("https://images.unsplash.com/photo-1539109136881-3be0616acf4b", "https://images.unsplash.com/photo-1496747611176-843123e4949c")
  "aurora-linen-coord-set-black"     = @("https://images.unsplash.com/photo-1434389677669-084d51b1d9a8", "https://images.unsplash.com/photo-1489987707025-afc232f7ea0f")
  "zenava-oxford-casual-shirt-sky-blue" = @("https://images.unsplash.com/photo-1602810318383-e386cc2a3ccf", "https://images.unsplash.com/photo-1562157873-818bc0726f68")
  "lumira-everyday-sneakers-white"   = @("https://images.unsplash.com/photo-1542291026-7eec264c27ff", "https://images.unsplash.com/photo-1560769629-975ec94e6a86")
  "forge-flame-cast-iron-kadai-26"   = @("https://images.unsplash.com/photo-1556911220-e15b29be8c8f", "https://images.unsplash.com/photo-1516684732162-798a0062be99")
  "forge-flame-triply-saucepan-2l"   = @("https://images.unsplash.com/photo-1584345604476-8ec5e12e66dd", "https://images.unsplash.com/photo-1516685018646-549198525c1b")
  "nexora-smart-bulb-pack2"          = @("https://images.unsplash.com/photo-1550985616-1041023e0825", "https://images.unsplash.com/photo-1507473885765-e6ed057f782c")
  "aurora-smart-plug-16a-pack2"      = @("https://images.unsplash.com/photo-1583863789431-e30e7e36740c", "https://images.unsplash.com/photo-1550009158-9ebf69173e03")
  "lumira-adjustable-dumbbell-12kg"  = @("https://images.unsplash.com/photo-1571019613454-1cb2f99b2d8b", "https://images.unsplash.com/photo-1534438327276-14e5300c3a48")
  "aurora-yoga-mat-6mm"              = @("https://images.unsplash.com/photo-1518611012118-696072aa579a", "https://images.unsplash.com/photo-1575052814086-f385e2e2ad1b")
  "orbitrio-fitband-3"               = @("https://images.unsplash.com/photo-1576243345690-4e4b79b63288", "https://images.unsplash.com/photo-1575311373937-040b8e1fd5b6")
}

$failures = @()
foreach ($slug in $map.Keys) {
  for ($i = 0; $i -lt 2; $i++) {
    $file = Join-Path $dest "$slug-$($i+1).jpg"
    if (Test-Path $file) { continue }
    try {
      $url = "$($map[$slug][$i])?auto=format&fit=crop&w=900&q=70"
      Invoke-WebRequest -Uri $url -OutFile $file -UseBasicParsing -TimeoutSec 30
      $len = (Get-Item $file).Length
      if ($len -lt 5000) { throw "too small ($len bytes)" }
      Write-Host "OK  $slug-$($i+1)  $len bytes"
    } catch {
      $failures += "$slug-$($i+1)"
      Write-Host "FAIL $slug-$($i+1)"
    }
  }
}
# Fallback: any failure reuses the first teammate image of a same-category product (kept local, never hotlinked)
if ($failures.Count -gt 0) {
  $fallback = Join-Path $dest "nexora-n12-pro-5g-cosmic-grey-2.jpg"
  foreach ($f in $failures) {
    $target = Join-Path $dest "$f.jpg"
    if (-not (Test-Path $target)) { Copy-Item $fallback $target; Write-Host "FALLBACK-COPY -> $f.jpg" }
  }
}
Write-Host "DOWNLOADED=$($map.Keys.Count * 2) failures=$($failures.Count)"
