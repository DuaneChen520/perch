# Generates the legacy launcher icons and the TV banner from the Perch mark.
# Windows-only and optional; generated assets are committed.
Add-Type -AssemblyName System.Drawing
$ErrorActionPreference = 'Stop'

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$root = Split-Path -Parent $scriptDir

$bgColor = [System.Drawing.Color]::FromArgb(255, 27, 26, 23)
$iconBgColor = [System.Drawing.Color]::FromArgb(255, 42, 40, 35)
$ivory = [System.Drawing.Color]::FromArgb(255, 243, 238, 229)
$sage = [System.Drawing.Color]::FromArgb(255, 166, 185, 141)

function New-RoundedPath([float]$x, [float]$y, [float]$w, [float]$h, [float]$r) {
    $path = New-Object System.Drawing.Drawing2D.GraphicsPath
    $d = $r * 2
    $path.AddArc($x, $y, $d, $d, 180, 90)
    $path.AddArc($x + $w - $d, $y, $d, $d, 270, 90)
    $path.AddArc($x + $w - $d, $y + $h - $d, $d, $d, 0, 90)
    $path.AddArc($x, $y + $h - $d, $d, $d, 90, 90)
    $path.CloseFigure()
    return $path
}

$icons = @(
    @{ Dir = 'mipmap-mdpi'; Size = 48 },
    @{ Dir = 'mipmap-hdpi'; Size = 72 },
    @{ Dir = 'mipmap-xhdpi'; Size = 96 },
    @{ Dir = 'mipmap-xxhdpi'; Size = 144 },
    @{ Dir = 'mipmap-xxxhdpi'; Size = 192 }
)

foreach ($icon in $icons) {
    $size = [int]$icon.Size
    $bitmap = New-Object System.Drawing.Bitmap(
        $size, $size, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
    $graphics = [System.Drawing.Graphics]::FromImage($bitmap)
    $graphics.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
    $graphics.Clear([System.Drawing.Color]::Transparent)

    $inset = [float]($size * 0.04)
    $side = [float]($size - ($inset * 2))
    $path = New-RoundedPath $inset $inset $side $side ([float]($size * 0.2))
    $bgBrush = New-Object System.Drawing.SolidBrush($iconBgColor)
    $graphics.FillPath($bgBrush, $path)
    $path.Dispose()

    $linePen = New-Object System.Drawing.Pen($ivory, [float]($size * 0.075))
    $linePen.StartCap = [System.Drawing.Drawing2D.LineCap]::Round
    $linePen.EndCap = [System.Drawing.Drawing2D.LineCap]::Round
    $graphics.DrawLine(
        $linePen,
        [float]($size * 0.24), [float]($size * 0.63),
        [float]($size * 0.76), [float]($size * 0.63))

    $dotBrush = New-Object System.Drawing.SolidBrush($sage)
    $radius = [float]($size * 0.085)
    $graphics.FillEllipse(
        $dotBrush,
        [float](($size * 0.64) - $radius),
        [float](($size * 0.42) - $radius),
        [float]($radius * 2), [float]($radius * 2))

    $outDir = Join-Path $root ("res\" + $icon.Dir)
    New-Item -ItemType Directory -Force -Path $outDir | Out-Null
    $outPath = Join-Path $outDir 'ic_launcher.png'
    $bitmap.Save($outPath, [System.Drawing.Imaging.ImageFormat]::Png)

    $linePen.Dispose()
    $bgBrush.Dispose()
    $dotBrush.Dispose()
    $graphics.Dispose()
    $bitmap.Dispose()
    Write-Output "wrote $outPath"
}

$banner = New-Object System.Drawing.Bitmap(
    320, 180, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
$bannerGraphics = [System.Drawing.Graphics]::FromImage($banner)
$bannerGraphics.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
$bannerGraphics.TextRenderingHint = [System.Drawing.Text.TextRenderingHint]::AntiAliasGridFit
$bgBrush = New-Object System.Drawing.SolidBrush($bgColor)
$bannerGraphics.FillRectangle($bgBrush, 0, 0, 320, 180)

$bannerPen = New-Object System.Drawing.Pen($ivory, 5)
$bannerPen.StartCap = [System.Drawing.Drawing2D.LineCap]::Round
$bannerPen.EndCap = [System.Drawing.Drawing2D.LineCap]::Round
$bannerGraphics.DrawLine($bannerPen, 34, 100, 104, 100)

$bannerDot = New-Object System.Drawing.SolidBrush($sage)
$bannerGraphics.FillEllipse($bannerDot, 70, 68, 18, 18)

$cnFont = New-Object System.Drawing.Font(
    'Microsoft YaHei', 42, [System.Drawing.FontStyle]::Regular,
    [System.Drawing.GraphicsUnit]::Pixel)
$enFont = New-Object System.Drawing.Font(
    'Segoe UI', 22, [System.Drawing.FontStyle]::Regular,
    [System.Drawing.GraphicsUnit]::Pixel)
$ivoryBrush = New-Object System.Drawing.SolidBrush($ivory)
$sageBrush = New-Object System.Drawing.SolidBrush($sage)
$bannerGraphics.DrawString([string][char]0x6816, $cnFont, $ivoryBrush, 120, 38)
$bannerGraphics.DrawString('Perch', $enFont, $sageBrush, 122, 104)

$bannerPath = Join-Path $root 'res\drawable-xhdpi\tv_banner.png'
$banner.Save($bannerPath, [System.Drawing.Imaging.ImageFormat]::Png)

$bgBrush.Dispose()
$bannerPen.Dispose()
$bannerDot.Dispose()
$cnFont.Dispose()
$enFont.Dispose()
$ivoryBrush.Dispose()
$sageBrush.Dispose()
$bannerGraphics.Dispose()
$banner.Dispose()
Write-Output "wrote $bannerPath"
