<#
.SYNOPSIS
  Updates shared/ (code reused from Echo Music) from an Echo Music checkout and re-applies
  the desktop's innertube fixes.

.EXAMPLE
  git clone https://github.com/EchoMusicApp/Echo-Music.git C:\temp\Echo-Music
  .\scripts\update-shared.ps1 -EchoMusic C:\temp\Echo-Music
  .\gradlew.bat test
#>
param(
    [Parameter(Mandatory = $true)][string]$EchoMusic
)
$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot -Parent

if (-not (Test-Path (Join-Path $EchoMusic 'innertube\src\main\kotlin'))) {
    throw "No parece una copia de Echo Music: $EchoMusic"
}

# Pure-Kotlin modules of the app
$modules = 'innertube', 'lrclib', 'metadata', 'kugou', 'betterlyrics', 'simpmusic',
           'youlyplus', 'paxsenixlyrics', 'shazamkit', 'canvas', 'applecanvas'
foreach ($m in $modules) {
    $dst = Join-Path $root "shared\$m"
    if (Test-Path $dst) { Remove-Item -Recurse -Force $dst }
    Copy-Item -Recurse (Join-Path $EchoMusic "$m\src\main\kotlin") $dst
}

# Single files from the app (source in Echo Music -> path under shared\echo-app)
$extracts = [ordered]@{
    'lyrics\src\main\kotlin\echo\music\iad1tya\api\OpenRouterService.kt'          = 'echo\music\iad1tya\api\OpenRouterService.kt'
    'lyrics\src\main\kotlin\echo\music\iad1tya\api\DeepLService.kt'               = 'echo\music\iad1tya\api\DeepLService.kt'
    'lyrics\src\main\kotlin\echo\music\iad1tya\api\MistralService.kt'             = 'echo\music\iad1tya\api\MistralService.kt'
    'app\src\main\kotlin\com\music\echo\utils\lastfm\LastFM.kt'                    = 'echo\music\iad1tya\utils\lastfm\LastFM.kt'
    'app\src\main\kotlin\com\music\echo\ui\screens\settings\ListenBrainzManager.kt' = 'echo\music\iad1tya\ui\screens\settings\ListenBrainzManager.kt'
    'core\src\main\kotlin\echo\music\iad1tya\models\lastfm\Authentication.kt'      = 'echo\music\iad1tya\models\lastfm\Authentication.kt'
    'app\src\main\kotlin\com\music\echo\recognition\ShazamSignatureGenerator.kt'   = 'echo\music\iad1tya\recognition\ShazamSignatureGenerator.kt'
    'app\src\main\kotlin\com\music\echo\recognition\VibraSignature.kt'             = 'echo\music\iad1tya\recognition\VibraSignature.kt'
}
foreach ($src in $extracts.Keys) {
    $dst = Join-Path $root ("shared\echo-app\" + $extracts[$src])
    New-Item -ItemType Directory -Force (Split-Path $dst -Parent) | Out-Null
    Copy-Item (Join-Path $EchoMusic $src) $dst -Force
}
Copy-Item (Join-Path $EchoMusic 'lyrics\src\main\kotlin\echo\music\iad1tya\lyrics\LyricsUtils.kt') `
    (Join-Path $root 'shared\echo-app-romanization\LyricsUtils.kt') -Force

# Spotify client (the Android-only parts, session storage and queue, are rewritten in src/main)
$spotifySrc = Join-Path $EchoMusic 'app\src\main\kotlin\com\music\echo\spotify'
$spotifyDst = Join-Path $root 'shared\spotify\echo\music\iad1tya\spotify'
if (Test-Path (Join-Path $root 'shared\spotify')) { Remove-Item -Recurse -Force (Join-Path $root 'shared\spotify') }
New-Item -ItemType Directory -Force (Join-Path $spotifyDst 'models') | Out-Null
foreach ($f in 'Spotify.kt', 'SpotifyAuth.kt', 'SpotifyHashProvider.kt', 'SpotifyMapper.kt', 'SpotifyPlaybackResolver.kt') {
    Copy-Item (Join-Path $spotifySrc $f) (Join-Path $spotifyDst $f) -Force
}
Copy-Item (Join-Path $spotifySrc 'models\*.kt') (Join-Path $spotifyDst 'models') -Force
# Kotlin 2.1 does not allow "return" inside an expression body (Echo Music uses 2.3)
Push-Location $root
try {
    git apply scripts/spotify-desktop.patch
    if ($LASTEXITCODE -ne 0) { Write-Warning "El parche de Spotify no aplicó limpio: revisa scripts\spotify-desktop.patch" }
} finally {
    Pop-Location
}

# Re-apply the desktop's fixes to innertube (paths in the patch are innertube/src/main/kotlin/...)
Push-Location $root
try {
    git apply -p5 --directory=shared/innertube scripts/innertube-desktop.patch
    if ($LASTEXITCODE -ne 0) {
        Write-Warning "El parche de innertube no aplicó limpio: revisa scripts\innertube-desktop.patch (puede que Echo Music ya incluya el arreglo)."
    } else {
        Write-Host "shared/ actualizado y arreglos de innertube aplicados. Ejecuta .\gradlew.bat test"
    }
} finally {
    Pop-Location
}
