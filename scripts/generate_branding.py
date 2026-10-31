
#!/usr/bin/env python3
"""Build Android brand assets from branding/logo/logo.png."""
from pathlib import Path
from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
SRC = ROOT / "branding" / "logo" / "logo.png"

def silhouette(im: Image.Image, size: int) -> Image.Image:
    im = im.resize((size, size), Image.Resampling.LANCZOS)
    px = im.load()
    out = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    op = out.load()
    for y in range(size):
        for x in range(size):
            r, g, b, a = px[x, y]
            if a < 40:
                continue
            lum = 0.2126 * r + 0.7152 * g + 0.0722 * b
            if lum > 70:
                op[x, y] = (255, 255, 255, min(255, a))
    return out

def main() -> None:
    if not SRC.exists():
        raise SystemExit(f"Missing {SRC}")
    src = Image.open(SRC).convert("RGBA")
    src.resize((512, 512), Image.Resampling.LANCZOS).save(ROOT / "branding" / "logo" / "play_store_512.png")
    nodpi = ROOT / "app" / "src" / "main" / "res" / "drawable-nodpi"
    nodpi.mkdir(parents=True, exist_ok=True)
    src.resize((512, 512), Image.Resampling.LANCZOS).save(nodpi / "brand_logo.png")
    for d, s in {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}.items():
        folder = ROOT / "app" / "src" / "main" / "res" / f"mipmap-{d}"
        folder.mkdir(parents=True, exist_ok=True)
        src.resize((s, s), Image.Resampling.LANCZOS).save(folder / "ic_launcher.png")
        src.resize((s, s), Image.Resampling.LANCZOS).save(folder / "ic_launcher_round.png")
        fs = int(s * 108 / 48)
        src.resize((fs, fs), Image.Resampling.LANCZOS).save(folder / "ic_launcher_foreground.png")
    for d, s in {"mdpi": 24, "hdpi": 36, "xhdpi": 48, "xxhdpi": 72, "xxxhdpi": 96}.items():
        folder = ROOT / "app" / "src" / "main" / "res" / f"drawable-{d}"
        folder.mkdir(parents=True, exist_ok=True)
        silhouette(src, s).save(folder / "ic_stat_notify.png")
        silhouette(src, s * 2).save(folder / "ic_launcher_monochrome.png")
    print("Branding generated from", SRC)

if __name__ == "__main__":
    main()
