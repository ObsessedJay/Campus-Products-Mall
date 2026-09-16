from __future__ import annotations

import math
import random
from pathlib import Path

from PIL import Image, ImageDraw, ImageEnhance, ImageFilter, ImageFont


ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / ".asset-production"
PRODUCTS = ROOT / "src" / "assets" / "products"
TEXTURES = ROOT / "src" / "assets" / "textures"
W, H = 1200, 900

INK = (24, 24, 22)
PAPER = (241, 237, 225)
CORAL = (234, 85, 64)
GREEN = (28, 126, 91)
GOLD = (195, 164, 91)


def font(size: int, bold: bool = False) -> ImageFont.FreeTypeFont:
    names = [
        "C:/Windows/Fonts/arialbd.ttf" if bold else "C:/Windows/Fonts/arial.ttf",
        "C:/Windows/Fonts/segoeuib.ttf" if bold else "C:/Windows/Fonts/segoeui.ttf",
    ]
    for name in names:
        if Path(name).exists():
            return ImageFont.truetype(name, size)
    return ImageFont.load_default()


def cover_crop(image: Image.Image, size=(W, H), center=(0.5, 0.5)) -> Image.Image:
    image = image.convert("RGB")
    target_ratio = size[0] / size[1]
    source_ratio = image.width / image.height
    if source_ratio > target_ratio:
        crop_h = image.height
        crop_w = int(crop_h * target_ratio)
    else:
        crop_w = image.width
        crop_h = int(crop_w / target_ratio)
    left = int((image.width - crop_w) * center[0])
    top = int((image.height - crop_h) * center[1])
    left = max(0, min(left, image.width - crop_w))
    top = max(0, min(top, image.height - crop_h))
    return image.crop((left, top, left + crop_w, top + crop_h)).resize(size, Image.Resampling.LANCZOS)


def soft_grade(image: Image.Image, color=0.86, contrast=1.05, brightness=0.96) -> Image.Image:
    image = ImageEnhance.Color(image).enhance(color)
    image = ImageEnhance.Contrast(image).enhance(contrast)
    return ImageEnhance.Brightness(image).enhance(brightness)


def shadowed_paste(canvas: Image.Image, item: Image.Image, xy: tuple[int, int], blur=24, offset=(13, 18), opacity=105):
    alpha = item.getchannel("A")
    shadow = Image.new("RGBA", canvas.size, (0, 0, 0, 0))
    shadow_shape = Image.new("RGBA", item.size, (18, 18, 16, 0))
    shadow_shape.putalpha(alpha.point(lambda p: p * opacity // 255))
    shadow.alpha_composite(shadow_shape, (xy[0] + offset[0], xy[1] + offset[1]))
    shadow = shadow.filter(ImageFilter.GaussianBlur(blur))
    canvas.alpha_composite(shadow)
    canvas.alpha_composite(item, xy)


def paper_texture(size=(W, H), seed=20260820) -> Image.Image:
    rng = random.Random(seed)
    width, height = size
    image = Image.new("RGB", size, PAPER)
    pixels = image.load()
    for y in range(height):
        for x in range(width):
            wave = (
                2.3 * math.sin(2 * math.pi * x / 173)
                + 1.8 * math.cos(2 * math.pi * y / 139)
                + 1.2 * math.sin(2 * math.pi * (x + y) / 67)
            )
            speck = rng.choice((-2, -1, 0, 0, 0, 1, 2))
            delta = int(wave + speck)
            pixels[x, y] = tuple(max(0, min(255, c + delta)) for c in PAPER)
    draw = ImageDraw.Draw(image, "RGBA")
    fiber_colors = [(104, 85, 58, 28), (25, 99, 71, 19), (185, 78, 57, 16), (255, 255, 255, 34)]
    for _ in range(max(220, width * height // 1800)):
        x = rng.randrange(width)
        y = rng.randrange(height)
        length = rng.randrange(4, 18)
        angle = rng.uniform(-0.35, 0.35)
        draw.line((x, y, x + math.cos(angle) * length, y + math.sin(angle) * length), fill=rng.choice(fiber_colors), width=1)
    return image


def make_annual() -> Image.Image:
    art = Image.open(SOURCE / "annual-source.jpg")
    canvas = paper_texture()
    canvas = canvas.convert("RGBA")
    draw = ImageDraw.Draw(canvas, "RGBA")
    draw.rectangle((874, 68, 1128, 99), fill=(234, 85, 64, 170))
    draw.rectangle((78, 90, 318, 101), fill=(28, 126, 91, 155))

    cover = Image.new("RGBA", (650, 700), (0, 0, 0, 0))
    cd = ImageDraw.Draw(cover, "RGBA")
    cd.rounded_rectangle((22, 18, 628, 680), radius=8, fill=(222, 214, 195), outline=(24, 24, 22), width=3)
    cd.rectangle((38, 34, 612, 644), fill=(255, 255, 255))
    art_crop = cover_crop(art, (540, 540), center=(0.5, 0.48))
    cover.alpha_composite(art_crop.convert("RGBA"), (56, 52))
    cd.rectangle((38, 34, 78, 644), fill=CORAL)
    cd.rectangle((92, 612, 612, 644), fill=(239, 235, 221))
    cd.text((106, 617), "ANNUAL ART BOOK / 2026", fill=INK, font=font(20, True))
    cd.ellipse((548, 64, 590, 106), fill=GREEN)
    cd.text((559, 71), "A", fill=(255, 255, 255), font=font(23, True))
    for x in range(98, 606, 12):
        cd.line((x, 656, x + 7, 656), fill=(84, 77, 64, 80), width=1)
    cover = cover.rotate(7, resample=Image.Resampling.BICUBIC, expand=True)
    shadowed_paste(canvas, cover, (260, 128), blur=22, offset=(16, 22), opacity=120)

    draw = ImageDraw.Draw(canvas, "RGBA")
    draw.rectangle((64, 690, 260, 742), fill=(246, 241, 226, 235), outline=(24, 24, 22, 90), width=2)
    draw.text((82, 705), "ART CLUB EDITION", fill=INK, font=font(18, True))
    draw.line((72, 776, 330, 818), fill=(234, 85, 64, 155), width=10)
    return canvas.convert("RGB")


def make_shirt() -> Image.Image:
    source = Image.open(SOURCE / "shirt-source.jpg")
    canvas = soft_grade(cover_crop(source, center=(0.5, 0.43)), color=0.78, contrast=1.04, brightness=0.97).convert("RGBA")
    overlay = Image.new("RGBA", (390, 440), (0, 0, 0, 0))
    draw = ImageDraw.Draw(overlay, "RGBA")
    draw.arc((35, 18, 355, 286), 198, 342, fill=GREEN + (215,), width=17)
    draw.line((82, 150, 306, 150), fill=INK + (185,), width=3)
    draw.text((90, 166), "CLASS OF", fill=INK + (205,), font=font(42, True))
    draw.text((70, 210), "2026", fill=GREEN + (220,), font=font(94, True))
    draw.rectangle((82, 328, 309, 354), fill=CORAL + (220,))
    draw.polygon([(160, 116), (195, 96), (230, 116), (195, 136)], outline=INK + (205,))
    draw.line((230, 116, 239, 150), fill=INK + (205,), width=3)
    draw.ellipse((234, 146, 242, 154), fill=CORAL + (230,))
    overlay = overlay.filter(ImageFilter.GaussianBlur(0.45))
    # The print follows the shirt's central, nearly planar chest area.
    canvas.alpha_composite(overlay, (405, 195))
    grain = Image.effect_noise((W, H), 18).convert("L").point(lambda p: max(0, p - 228))
    ink_grain = Image.new("RGBA", (W, H), (255, 255, 255, 0))
    ink_grain.putalpha(grain.point(lambda p: p // 5))
    canvas.alpha_composite(ink_grain)
    return canvas.convert("RGB")


def make_journal() -> Image.Image:
    source = Image.open(SOURCE / "journal-source.jpg")
    canvas = soft_grade(cover_crop(source, center=(0.51, 0.56)), color=0.72, contrast=0.95, brightness=0.9)
    canvas = canvas.filter(ImageFilter.GaussianBlur(9)).convert("RGBA")
    wash = Image.new("RGBA", canvas.size, (241, 237, 225, 45))
    canvas.alpha_composite(wash)

    book = Image.new("RGBA", (610, 720), (0, 0, 0, 0))
    draw = ImageDraw.Draw(book, "RGBA")
    draw.rounded_rectangle((26, 30, 586, 704), radius=18, fill=(226, 218, 195), outline=(20, 25, 21), width=3)
    for y in range(660, 696, 6):
        draw.line((48, y, 564, y), fill=(114, 103, 82, 65), width=1)
    draw.rounded_rectangle((18, 12, 574, 668), radius=20, fill=(19, 49, 39), outline=(8, 22, 17), width=4)
    cloth_rng = random.Random(4096)
    for _ in range(520):
        x = cloth_rng.randrange(26, 566)
        y = cloth_rng.randrange(18, 660)
        draw.line((x, y, x + cloth_rng.randrange(2, 8), y), fill=(255, 255, 255, 11), width=1)
    draw.rectangle((64, 70, 510, 73), fill=GOLD + (180,))
    draw.text((64, 98), "NIGHT OBSERVATION", fill=(238, 232, 211), font=font(31, True))
    draw.text((64, 142), "FIELD JOURNAL / 2026", fill=GOLD, font=font(20, True))
    cx, cy = 300, 388
    for radius in (76, 126, 176):
        draw.ellipse((cx - radius, cy - radius, cx + radius, cy + radius), outline=(222, 213, 183, 105), width=2)
    draw.line((98, cy, 502, cy), fill=(222, 213, 183, 80), width=2)
    draw.line((cx, 194, cx, 582), fill=(222, 213, 183, 80), width=2)
    stars = [(170, 350), (222, 282), (289, 332), (355, 270), (430, 338), (383, 434), (264, 486), (202, 430)]
    draw.line(stars + [stars[0]], fill=(236, 226, 191, 145), width=2)
    for i, (x, y) in enumerate(stars):
        r = 5 if i in (1, 4) else 3
        draw.ellipse((x - r, y - r, x + r, y + r), fill=(245, 237, 206))
    draw.rectangle((515, 12, 544, 668), fill=CORAL + (230,))
    draw.rounded_rectangle((62, 600, 220, 630), radius=3, fill=(238, 232, 211))
    draw.text((77, 605), "CAMPUS SKY", fill=(19, 49, 39), font=font(16, True))
    book = book.rotate(9, resample=Image.Resampling.BICUBIC, expand=True)
    shadowed_paste(canvas, book, (304, 92), blur=27, offset=(18, 22), opacity=130)
    return canvas.convert("RGB")


def make_backpack() -> Image.Image:
    source = Image.open(SOURCE / "backpack-source.jpg")
    canvas = soft_grade(cover_crop(source, center=(0.44, 0.49)), color=0.72, contrast=1.11, brightness=0.91).convert("RGBA")
    stitch = Image.new("RGBA", canvas.size, (0, 0, 0, 0))
    draw = ImageDraw.Draw(stitch, "RGBA")
    points = [(305, 554), (390, 492), (458, 520), (522, 438), (586, 468), (642, 404)]
    draw.line(points, fill=(15, 45, 35, 210), width=16, joint="curve")
    draw.line(points, fill=CORAL + (235,), width=8, joint="curve")
    # Small bright breaks make the route line read as embroidery thread.
    for a, b in zip(points, points[1:]):
        for t in (0.22, 0.52, 0.82):
            x = int(a[0] + (b[0] - a[0]) * t)
            y = int(a[1] + (b[1] - a[1]) * t)
            draw.ellipse((x - 2, y - 2, x + 2, y + 2), fill=(255, 224, 208, 180))
    for index, (x, y) in enumerate(points):
        draw.ellipse((x - 15, y - 15, x + 15, y + 15), fill=(19, 49, 39, 235), outline=(235, 229, 207, 220), width=3)
        draw.text((x - 5, y - 9), str(index + 1), fill=(245, 241, 228), font=font(16, True))
    draw.rounded_rectangle((405, 670, 685, 730), radius=5, fill=(238, 232, 211, 242), outline=(17, 35, 28, 238), width=3)
    draw.rectangle((405, 670, 440, 730), fill=GREEN + (245,))
    draw.text((458, 687), "CAMPUS ROUTE", fill=(17, 35, 28, 235), font=font(19, True))
    stitch = stitch.filter(ImageFilter.GaussianBlur(0.35))
    canvas.alpha_composite(stitch)
    return canvas.convert("RGB")


def make_tile() -> Image.Image:
    size = 512
    rng = random.Random(20260820)
    image = Image.new("RGB", (size, size), PAPER)
    pixels = image.load()
    for y in range(size):
        for x in range(size):
            # Integer-frequency harmonics repeat at the tile boundary.
            value = (
                2.2 * math.sin(2 * math.pi * x / (size - 1) * 3)
                + 1.7 * math.cos(2 * math.pi * y / (size - 1) * 5)
                + 1.1 * math.sin(2 * math.pi * (x + y) / (size - 1) * 2)
            )
            d = int(value)
            pixels[x, y] = (max(0, min(255, PAPER[0] + d)), max(0, min(255, PAPER[1] + d)), max(0, min(255, PAPER[2] + d)))
    draw = ImageDraw.Draw(image, "RGBA")
    colors = [(112, 91, 63, 30), (32, 99, 73, 21), (188, 84, 62, 17), (255, 255, 255, 36)]
    for _ in range(520):
        x = rng.randrange(size)
        y = rng.randrange(size)
        length = rng.randrange(4, 16)
        angle = rng.uniform(-0.4, 0.4)
        dx, dy = math.cos(angle) * length, math.sin(angle) * length
        color = rng.choice(colors)
        for ox in (-size, 0, size):
            for oy in (-size, 0, size):
                draw.line((x + ox, y + oy, x + dx + ox, y + dy + oy), fill=color, width=1)
    # Enforce byte-identical opposite edges after the wrapped drawing pass.
    for y in range(size):
        pixels[size - 1, y] = pixels[0, y]
    for x in range(size):
        pixels[x, size - 1] = pixels[x, 0]
    return image


def main():
    missing = [name for name in ("annual-source.jpg", "shirt-source.jpg", "journal-source.jpg", "backpack-source.jpg") if not (SOURCE / name).exists()]
    if missing:
        raise SystemExit(f"Missing source files in {SOURCE}: {', '.join(missing)}")
    PRODUCTS.mkdir(parents=True, exist_ok=True)
    TEXTURES.mkdir(parents=True, exist_ok=True)
    outputs = {
        PRODUCTS / "annual-art-booklet.png": make_annual(),
        PRODUCTS / "graduation-shirt.png": make_shirt(),
        PRODUCTS / "astronomy-journal.png": make_journal(),
        PRODUCTS / "campus-route-backpack.png": make_backpack(),
        TEXTURES / "recycled-paper.png": make_tile(),
    }
    for output, image in outputs.items():
        image.save(output, format="PNG", optimize=True)
        print(f"wrote {output.relative_to(ROOT)} {image.width}x{image.height}")


if __name__ == "__main__":
    main()
