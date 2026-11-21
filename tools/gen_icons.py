"""Draws the Longplay launcher icon and TV banner (vinyl record, film-reel label).

Usage: python gen_icons.py <app/src/main/res>
Colors follow ui/theme/Theme.kt: background 0E0E11, primary E3B35C, text ECE9E4.
"""
import math
import sys
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

BG = (14, 14, 17, 255)
DISC = (34, 34, 40, 255)
GROOVE = (58, 58, 68, 255)
AMBER = (227, 179, 92, 255)
AMBER_DARK = (150, 112, 48, 255)
TEXT = (236, 233, 228, 255)
SS = 4  # supersampling factor for smooth edges


def record(draw, cx, cy, r):
    """Vinyl disc with grooves and an amber film-reel label."""
    draw.ellipse([cx - r, cy - r, cx + r, cy + r], fill=DISC)
    for frac in (0.92, 0.84, 0.76, 0.68, 0.60, 0.52):
        rr = r * frac
        draw.ellipse([cx - rr, cy - rr, cx + rr, cy + rr], outline=GROOVE, width=max(1, int(r * 0.012)))
    # Light sheen across the grooves.
    draw.arc([cx - r * 0.88, cy - r * 0.88, cx + r * 0.88, cy + r * 0.88], 200, 250, fill=(70, 70, 80, 255),
             width=max(1, int(r * 0.03)))
    # Label = film reel: amber disc, five round cut-outs, hub.
    lr = r * 0.40
    draw.ellipse([cx - lr, cy - lr, cx + lr, cy + lr], fill=AMBER)
    hole = lr * 0.24
    for k in range(5):
        a = math.radians(-90 + k * 72)
        hx, hy = cx + math.cos(a) * lr * 0.56, cy + math.sin(a) * lr * 0.56
        draw.ellipse([hx - hole, hy - hole, hx + hole, hy + hole], fill=AMBER_DARK)
    hub = lr * 0.16
    draw.ellipse([cx - hub, cy - hub, cx + hub, cy + hub], fill=DISC)


def icon(size):
    s = size * SS
    img = Image.new("RGBA", (s, s), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    pad = s * 0.04
    d.rounded_rectangle([pad, pad, s - pad, s - pad], radius=s * 0.22, fill=BG)
    record(d, s / 2, s / 2, s * 0.38)
    return img.resize((size, size), Image.LANCZOS)


def banner(w, h):
    W, H = w * SS, h * SS
    img = Image.new("RGBA", (W, H), BG)
    d = ImageDraw.Draw(img)
    r = H * 0.36
    cx, cy = H * 0.50, H / 2
    record(d, cx, cy, r)
    tx = cx + r + H * 0.12
    room = W - tx - H * 0.12
    size = int(H * 0.26)
    while True:  # largest Georgia Bold size that fits beside the record
        font = ImageFont.truetype("C:/Windows/Fonts/georgiab.ttf", size)
        bbox = d.textbbox((0, 0), "Longplay", font=font)
        if bbox[2] - bbox[0] <= room or size < 10:
            break
        size -= 2
    ty = cy - (bbox[3] - bbox[1]) / 2 - bbox[1] - H * 0.05
    d.text((tx, ty), "Longplay", font=font, fill=TEXT)
    # Groove underline: three thin amber lines of decreasing length.
    text_w = bbox[2] - bbox[0]
    base = ty + bbox[3] + H * 0.06
    for i, frac in enumerate((1.0, 0.8, 0.6)):
        y = base + i * H * 0.035
        d.line([tx, y, tx + text_w * frac, y], fill=AMBER, width=max(2, int(H * 0.012)))
    return img.resize((w, h), Image.LANCZOS)


def main(res: Path):
    densities = {"mdpi": 1, "hdpi": 1.5, "xhdpi": 2, "xxhdpi": 3, "xxxhdpi": 4}
    for name, scale in densities.items():
        out = res / f"mipmap-{name}"
        out.mkdir(parents=True, exist_ok=True)
        icon(int(48 * scale)).save(out / "ic_launcher.png")
    # TV banner: 320 x 180 dp; xhdpi is the density Android TV uses.
    out = res / "drawable-xhdpi"
    out.mkdir(parents=True, exist_ok=True)
    banner(640, 360).save(out / "banner.png")
    print("written to", res)


if __name__ == "__main__":
    main(Path(sys.argv[1]))
