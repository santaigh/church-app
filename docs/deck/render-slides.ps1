# Renders docs/church-app-overview.pptx to PNGs in docs/slides.
#
# There is no LibreOffice on this machine, so PowerPoint is driven over COM. Run after
# `npm run build`, from any directory:
#
#     powershell -ExecutionPolicy Bypass -File render-slides.ps1
#
# Two things that cost time the first time round:
#
#   * COM takes strings, not PowerShell path objects. Resolve-Path returns a PathInfo,
#     which Open rejects with an error that names Quit rather than Open.
#   * The boolean arguments are msoTrue/msoFalse, which are -1 and 0. $true happens to
#     work; -1 is what the API actually documents.
#
# PowerPoint should be closed before running: COM attaches to a running instance, and a
# copy of this deck already open read-only makes the export fail unhelpfully.

$ErrorActionPreference = "Stop"

$deck = (Join-Path $PSScriptRoot "..\church-app-overview.pptx" | Resolve-Path).Path
$outDir = (Join-Path $PSScriptRoot "..\slides" | Resolve-Path).Path

$powerpoint = New-Object -ComObject PowerPoint.Application
$powerpoint.Visible = 1

# PowerPoint answers the COM call before its object model is usable. Without this wait,
# Presentations comes back null, or Open succeeds and reports a presentation with zero
# slides -- neither of which says anything about what is actually wrong.
for ($attempt = 1; $attempt -le 15; $attempt++) {
    try { $null = $powerpoint.Presentations.Count; break } catch { Start-Sleep -Seconds 2 }
}

try {
    # WithWindow = msoTrue. Opening windowless is what Protected View trips over on a
    # file that Windows has marked as coming from elsewhere.
    $presentation = $powerpoint.Presentations.Open($deck, -1, 0, -1)

    # Only clear the old renders once the deck has opened, so a failure here does not
    # leave the folder empty with nothing to replace it.
    Get-ChildItem -Path $outDir -Filter "slide-*.png" | Remove-Item -Force

    for ($i = 1; $i -le $presentation.Slides.Count; $i++) {
        $name = "slide-{0:D2}.png" -f $i
        # 1400x788 keeps the 16:9 ratio the earlier renders used.
        $presentation.Slides.Item($i).Export((Join-Path $outDir $name), "PNG", 1400, 788)
        Write-Output "rendered $name"
    }

    $presentation.Close()
}
finally {
    # Quit can be rejected while PowerPoint is still finishing an export; it is not worth
    # failing the render over.
    try { $powerpoint.Quit() } catch { }
}
