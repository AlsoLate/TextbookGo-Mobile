#!/usr/bin/env python3
"""生成 TextbookGo《坠书一瞬》logo（莫兰迪色板 + 墨色方形线条，与 assets/logo.svg 同几何）。
概念：一本书冻结在落向书架的前一瞬——下载的等待感成为画面本身。
输出 logo.png（README 用）+ logo.jpg（README 白底）+ assets/app.ico（Windows 图标）。
零外部素材依赖。"""
from __future__ import annotations
from pathlib import Path
from PIL import Image, ImageDraw

INK = (28, 27, 26, 255)      # #1C1B1A 晨光主墨
SAGE = (167, 176, 160, 255)   # #A7B0A0 鼠尾草
LINEN = (232, 228, 217, 255)  # #E8E4D9 漂白麻
OATMEAL = (212, 201, 176, 255)  # #D4C9B0 淡麦金
PAPER = (249, 248, 245, 255)  # #F9F8F5 README 白底
SIZE = 256


def draw_logo(size: int = SIZE) -> Image.Image:
    k = size / 256

    # 坠落之书：独立图层画好再旋转（倾 8°，与 SVG rotate(8) 同向）
    book = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    bd = ImageDraw.Draw(book)
    # Cubist 封面拼贴
    bd.rectangle([106 * k, 56 * k, 172 * k, 132 * k], fill=SAGE)
    bd.rectangle([106 * k, 132 * k, 172 * k, 176 * k], fill=LINEN)
    bd.rectangle([84 * k, 132 * k, 106 * k, 176 * k], fill=OATMEAL)
    # 墨色骨架（覆盖在色块上）
    bd.rectangle([84 * k, 56 * k, 172 * k, 176 * k], outline=INK, width=round(12 * k))
    bd.line([106 * k, 56 * k, 106 * k, 176 * k], fill=INK, width=round(12 * k))
    # PIL 正角度=逆时针，SVG 正角度=顺时针 → 取 -8
    book = book.rotate(-8, resample=Image.BICUBIC, center=(128 * k, 116 * k))

    img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    img.alpha_composite(book)
    d = ImageDraw.Draw(img)
    # 落点方点 + 书架 hairline
    d.rectangle([123 * k, 190 * k, 133 * k, 200 * k], fill=INK)
    d.line([52 * k, 204 * k, 204 * k, 204 * k], fill=INK, width=round(10 * k))
    return img


def main() -> int:
    repo = Path(__file__).resolve().parent.parent
    logo_png = repo / "logo.png"
    logo_jpg = repo / "logo.jpg"  # README 引用
    ico = repo / "assets" / "app.ico"
    ico.parent.mkdir(parents=True, exist_ok=True)

    img = draw_logo(SIZE)
    img.save(logo_png, "PNG")
    # 白底 JPG 给 README（避免透明背景在某些渲染器发黑）
    bg = Image.new("RGBA", (SIZE, SIZE), PAPER)
    bg.paste(img, (0, 0), img)
    bg.convert("RGB").save(logo_jpg, "JPEG", quality=95)
    # ICO 多尺寸
    img.save(ico, "ICO", sizes=[(256, 256), (128, 128), (64, 64), (48, 48), (32, 32), (16, 16)])
    print(f"  logo.png  -> {logo_png}")
    print(f"  logo.jpg -> {logo_jpg}")
    print(f"  app.ico  -> {ico}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())