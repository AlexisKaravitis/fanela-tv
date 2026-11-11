"""Draws the application icon (mipmap ic_app): the Fanela shirt in ivory with a grey trim on the cards' dark
(#1B2024), in the original icon's shape and placement. Android TV's add-row prompt shows this icon
(PackageManager.getApplicationIcon), so it is the neutral Fanela mark rather than one club's (or Greece's) colour."""
import sys
from PIL import Image
res = sys.argv[1] if len(sys.argv) > 1 else "app/src/main/res"  # run from the repo root
base = Image.open(res + '/mipmap-xxxhdpi/ic_launcher.png').convert('RGBA')
shirt = Image.open(res + '/drawable-nodpi/shirt.png').convert('RGBA')
BG, FILL, TRIM = (0x1B, 0x20, 0x24), (0xED, 0xEA, 0xE3), (0x8A, 0x90, 0x99)
def mix(a, b, t): return tuple(round(a[i] + (b[i] - a[i]) * t) for i in range(3))
sp = shirt.load(); w, h = shirt.size
rec = Image.new('RGBA', shirt.size)
rp = rec.load()
for y in range(h):
    for x in range(w):
        r, g, b, al = sp[x, y]
        if al == 0: continue
        t = max(0.0, min(1.0, (r - 0x0D) / (255 - 0x0D)))
        rp[x, y] = mix(FILL, TRIM, t) + (al,)
big = Image.new('RGBA', (192, 192), BG + (255,))
sc = 118 / 314
sh = rec.resize((round(360 * sc), round(360 * sc)), Image.LANCZOS)
big.alpha_composite(sh, (round(37 - 23 * sc), round(50 - 52 * sc)))
big.putalpha(base.getchannel('A'))
for d, n in (('mdpi', 48), ('hdpi', 72), ('xhdpi', 96), ('xxhdpi', 144), ('xxxhdpi', 192)):
    big.resize((n, n), Image.LANCZOS).save('%s/mipmap-%s/ic_app.png' % (res, d), optimize=True)
print('ok')
