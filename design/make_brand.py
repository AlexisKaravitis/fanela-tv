# Draws Fanela's brand assets: app icon, TV banner, header wordmark and the Play Store graphics.
# Font: Outfit (SIL Open Font License, design/fonts/OFL.txt). Everything else is drawn here; no third-party artwork.
from PIL import Image, ImageDraw, ImageFilter, ImageFont
import math, os

HERE = os.path.dirname(os.path.abspath(__file__))
RES = os.path.join(HERE, "..", "app", "src", "main", "res")
STORE = os.path.join(HERE, "..", "store", "graphics")
os.makedirs(STORE, exist_ok=True)
FONT = os.path.join(HERE, "fonts", "Outfit.ttf")

BG = (17, 19, 24)
BG2 = (12, 34, 68)
BLUE = (13, 94, 175)      # Greece blue
BLUE_L = (40, 120, 205)
INK = (17, 19, 24)
WHITE = (255, 255, 255)
SUB = (166, 172, 184)


def font(size, weight=b"Bold"):
    f = ImageFont.truetype(FONT, size)
    f.set_variation_by_name(weight)
    return f


def _poly_mask(size, pts, width=None):
    S = size * 4
    m = Image.new("L", (S, S), 0)
    d = ImageDraw.Draw(m)
    k = S / 1000.0
    q = [(x * k, y * k) for x, y in pts]
    if width: d.line(q, fill=255, width=int(width * k), joint="curve")
    else: d.polygon(q, fill=255)
    return m


def jersey_mask(size):
    """A football shirt silhouette with a V-neck (white on black), drawn at 4x and reduced for smooth edges."""
    S = size * 4
    body = [(372, 150), (232, 196), (70, 340), (168, 470), (262, 410), (262, 900),
            (738, 900), (738, 410), (832, 470), (930, 340), (768, 196), (628, 150), (500, 262)]
    m = _poly_mask(size, body)
    m = m.filter(ImageFilter.GaussianBlur(S / 260)).point(lambda v: 255 if v > 128 else 0)
    return m.resize((size, size), Image.LANCZOS)


def contrast(a, b):
    """WCAG contrast ratio between two colours."""
    la, lb = lum(a), lum(b)
    return (max(la, lb) + 0.05) / (min(la, lb) + 0.05)


def dark_on_bg(c):
    """A trim colour that doesn't stand out from the dark background (black, navy): it is set in from the edge."""
    return contrast(c, BG) < 3


def faint_on_bg(c):
    """A shirt colour that nearly disappears on the dark background (black, navy, maroon): it gets an edge."""
    return contrast(c, BG) < 2


# The trim, in the 1000-unit space of jersey_mask. Cuffs are bands straight across each sleeve end (the end runs from
# (70,340) to (168,470)); the left one is given and the right one mirrored. A light trim sits at the very end of the
# sleeve and the neckline; a dark trim is set in by a lip of shirt colour, so it never reads as a bite out of the outline.
CUFF = [(62, 346), (160, 476), (195, 453), (94, 319)]
CUFF_INSET = [(77, 333), (176, 465), (203, 447), (101, 312)]
COLLAR = ([(372, 150), (500, 262), (628, 150)], 64)
COLLAR_INSET = ([(372, 186), (500, 298), (628, 186)], 32)


def jersey(size, color, trim=WHITE, inset=None):
    """Coloured shirt with a V-neck collar and sleeve cuffs in the trim colour. inset: None decides from the trim."""
    if inset is None: inset = dark_on_bg(trim)
    mask = jersey_mask(size)
    im = Image.new("RGBA", (size, size), color + (255,))
    im.putalpha(mask)
    S = size * 4
    k = S / 1000.0
    pts, w = COLLAR_INSET if inset else COLLAR
    lines = _poly_mask(size, pts, w)
    d = ImageDraw.Draw(lines)
    cuff = CUFF_INSET if inset else CUFF
    for poly in (cuff, [(1000 - x, y) for x, y in cuff]):
        d.polygon([(x * k, y * k) for x, y in poly], fill=255)
    lines = lines.resize((size, size), Image.LANCZOS)
    inside = Image.composite(lines, Image.new("L", (size, size), 0), mask)
    im.paste(Image.new("RGBA", (size, size), trim + (255,)), (0, 0), inside)
    return im


def gradient(w, h, a, b, vertical=False):
    im = Image.new("RGB", (w, h), a)
    d = ImageDraw.Draw(im)
    n = h if vertical else w
    for i in range(n):
        t = i / max(1, n - 1)
        col = tuple(int(a[j] + (b[j] - a[j]) * t) for j in range(3))
        if vertical: d.line([(0, i), (w, i)], fill=col)
        else: d.line([(i, 0), (i, h)], fill=col)
    return im


def rounded(im, r):
    m = Image.new("L", im.size, 0)
    ImageDraw.Draw(m).rounded_rectangle([0, 0, im.size[0] - 1, im.size[1] - 1], radius=r, fill=255)
    out = im.convert("RGBA")
    out.putalpha(m)
    return out


def edged(j, color):
    """A dark shirt gets an edge in its trim colour, so it reads on the dark background. Its width follows the size."""
    a = j.getchannel("A")
    r = max(1, round(j.width * 0.012))
    ring = a.filter(ImageFilter.MaxFilter(2 * r + 1)).point(lambda v: 210 if v > 0 else 0)
    edge = Image.new("RGBA", j.size, color + (0,))
    edge.putalpha(ring)
    edge.alpha_composite(j)
    return edge


def icon(size, shirt=BLUE, trim=WHITE, top=BG2):
    S = size * 2
    bg = gradient(S, S, top, BG, vertical=True)
    j = jersey(int(S * 0.72), shirt, trim)
    if faint_on_bg(shirt): j = edged(j, trim)
    bg = bg.convert("RGBA")
    bg.alpha_composite(j, ((S - j.width) // 2, int(S * 0.15)))
    return bg.resize((size, size), Image.LANCZOS)


def wordmark(height, color=WHITE, shirt=BLUE, trim=WHITE, inset=None):
    """Shirt + 'Fanela' on transparent background."""
    H = height * 3
    f = font(int(H * 0.86))
    tw = int(ImageDraw.Draw(Image.new("L", (1, 1))).textlength("Fanela", font=f))
    j = jersey(int(H * 1.0), shirt, trim, inset)
    if faint_on_bg(shirt): j = edged(j, trim)
    gap = int(H * 0.28)
    im = Image.new("RGBA", (j.width + gap + tw + 8, H), (0, 0, 0, 0))
    im.alpha_composite(j, (0, 0))
    d = ImageDraw.Draw(im)
    d.text((j.width + gap, H * 0.52), "Fanela", font=f, fill=color + (255,), anchor="lm")
    bbox = im.getbbox()
    im = im.crop((0, 0, bbox[2] + 2, H))
    return im.resize((int(im.width / 3), height), Image.LANCZOS)


def banner(w, h, tagline=None, shirt=BLUE, trim=WHITE, top=BG2, glow_color=BLUE):
    S = 3
    W, Hh = w * S, h * S
    bg = gradient(W, Hh, top, BG).convert("RGBA")
    # soft glow behind the shirt
    glow = Image.new("L", (W, Hh), 0)
    ImageDraw.Draw(glow).ellipse([-W * 0.1, -Hh * 0.4, W * 0.55, Hh * 1.4], fill=60)
    glow = glow.filter(ImageFilter.GaussianBlur(W / 10))
    bg.paste(Image.new("RGBA", (W, Hh), glow_color + (255,)), (0, 0), glow.point(lambda v: v // 3))
    wm = wordmark(int(Hh * (0.30 if tagline else 0.36)), WHITE, shirt, trim)
    y = (Hh - wm.height) // 2 - (int(Hh * 0.08) if tagline else 0)
    bg.alpha_composite(wm, ((W - wm.width) // 2, y))
    if tagline:
        d = ImageDraw.Draw(bg)
        d.text((W // 2, y + wm.height + Hh * 0.09), tagline, font=font(int(Hh * 0.075), b"Regular"), fill=SUB + (255,), anchor="mt")
    return bg.convert("RGB").resize((w, h), Image.LANCZOS)


def lum(c):
    """Relative luminance, 0 black to 1 white."""
    def ch(v):
        v /= 255.0
        return v / 12.92 if v <= 0.03928 else ((v + 0.055) / 1.055) ** 2.4
    return 0.2126 * ch(c[0]) + 0.7152 * ch(c[1]) + 0.0722 * ch(c[2])


# Kit colours for the app icon and banner: the TV switches to the one closest to the favourite club's kit
# (app/src/main/java/app/fanela/tv/Brand.java has the same list). Blue is the default: the original assets.
KITS = [  # name, shirt, trim
    ("red", (208, 16, 46), WHITE), ("maroon", (122, 31, 43), WHITE), ("orange", (244, 122, 32), INK),
    ("yellow", (245, 200, 0), INK), ("green", (14, 138, 62), WHITE), ("sky", (108, 171, 221), (28, 44, 91)),
    ("navy", (30, 58, 138), WHITE), ("purple", (107, 62, 153), WHITE), ("black", (17, 17, 17), WHITE),
    ("white", (244, 244, 244), (43, 57, 144)),
]


def kit_background(shirt):
    """The dark top of the gradient, tinted with the kit (a black kit: neutral grey; a white one: the trim)."""
    if lum(shirt) < 0.05: return (40, 42, 50)
    base = shirt if lum(shirt) < 0.8 else (43, 57, 144)
    return tuple(int(BG[i] + (base[i] - BG[i]) * 0.32) for i in range(3))


def save(im, *path):
    p = os.path.join(*path)
    os.makedirs(os.path.dirname(p), exist_ok=True)
    im.save(p, optimize=True)
    print(os.path.relpath(p, os.path.join(HERE, "..")), im.size)


def kits():
    """The icon and banner in each kit colour (the originals stay as they are)."""
    for name, shirt, trim in KITS:
        top = kit_background(shirt)
        glow = shirt if 0.05 <= lum(shirt) < 0.8 else trim if lum(shirt) >= 0.8 else (90, 94, 104)
        for dpi, px in (("mdpi", 48), ("hdpi", 72), ("xhdpi", 96), ("xxhdpi", 144), ("xxxhdpi", 192)):
            save(rounded(icon(px, shirt, trim, top), int(px * 0.18)), RES, "mipmap-" + dpi, "ic_launcher_" + name + ".png")
        save(banner(320, 180, None, shirt, trim, top, glow), RES, "drawable-xhdpi", "banner_" + name + ".png")


if __name__ == "__main__" and "--kits" in __import__("sys").argv:
    kits()
elif __name__ == "__main__":
    # launcher icons (square; the TV launcher itself shows the banner)
    for dpi, px in (("mdpi", 48), ("hdpi", 72), ("xhdpi", 96), ("xxhdpi", 144), ("xxxhdpi", 192)):
        save(rounded(icon(px), int(px * 0.18)), RES, "mipmap-" + dpi, "ic_launcher.png")
    # TV banner: 320x180 at xhdpi (Android TV launcher), with the app name in it
    save(banner(320, 180), RES, "drawable-xhdpi", "banner.png")
    # header wordmark: 30 dp tall = 60 px at xhdpi; drawn at 2x that for 4K panels
    save(wordmark(120), RES, "drawable-nodpi", "wordmark.png")
    # the same, with the trim set in: the app uses it when the favourite club's trim is dark (Logo.java)
    save(wordmark(120, inset=True), RES, "drawable-nodpi", "wordmark_inset.png")
    # the shirt alone (home-screen row logo), both trims; recoloured on the TV
    save(jersey(360, BLUE, WHITE, False), RES, "drawable-nodpi", "shirt.png")
    save(jersey(360, BLUE, WHITE, True), RES, "drawable-nodpi", "shirt_inset.png")
    # Play Store
    save(icon(512), STORE, "icon-512.png")
    save(banner(1280, 720), STORE, "tv-banner-1280x720.png")
    save(banner(1024, 500, "Fixtures, live scores and the channel for every match"), STORE, "feature-graphic-1024x500.png")
