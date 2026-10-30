"""Builds the lock-screen wallpapers (Pillow + numpy).

Usage: python3 tools/make_wallpapers.py <folder with the source photos> [<preview folder>]

For each photo: crops it to 16:9, finds the skyline, places the clock so the peak (or the towers) rises in front of
its lower half, bakes the scrims in, and cuts out the part of the scene that is drawn over the clock. Writes
app/assets/wallpapers/<id>.jpg (1280x720), <id>-front.png and index.txt. All photos are CC0 or public domain
(Wikimedia Commons); the credits are in SPEC and in index.txt.
"""
import os
import sys

import numpy as np
from PIL import Image, ImageDraw, ImageFilter, ImageFont

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, "app/assets/wallpapers")
FONT = os.path.join(ROOT, "app/assets/fonts/InterVariable.ttf")
W, H = 1920, 1080          # design space (the app's layout units)
OW, OH = 1280, 720         # stored size (the Mi TV's screen)
CLOCK_SIZE = 300           # Inter display, weight 600 (Ui.displaySemibold)
CAP = 0.727 * CLOCK_SIZE   # Inter's cap height
HALF_SPAN = 440            # the widest clock ("10:08") reaches about this far from its center

# id, source file, caption, credit, and per-photo framing: zoom (>= 1), vertical crop (0 top .. 1 bottom), clock center.
SPEC = [
    ("aoraki", "60.jpg", "Aoraki / Mount Cook, New Zealand", "Bernard Spragg", dict(oy=0.15)),
    ("noville", "7.jpg", "Noville, Switzerland", "Romain Dupraz", dict()),
    ("alpenglow", "0.jpg", "Above the clouds", "Christopher Burns", dict(tolerance=4)),
    ("orange-sky", "9.jpg", "Orange sky over the Alps", "Pietro De Grandi", dict()),
    ("swiss-alps", "18.jpg", "Swiss Alps", "Mike Dennler", dict()),
    ("silhouette", "66.jpg", "Mountain silhouette at dusk", "Pacific Austin", dict()),
    ("dolomites", "115.jpg", "Dolomites, Italy", "Paul Morris", dict()),
    ("blue-ridges", "1.jpg", "Blue mountain silhouettes", "bady qb", dict()),
    ("london", "188.jpg", "London", "Pierre Blaché", dict()),
    ("vienna", "192.jpg", "Vienna, Austria", "Pierre Blaché", dict()),
    ("dallas", "186.jpg", "Downtown Dallas at dusk", "Carol M. Highsmith", dict()),
    ("toronto", "191.jpg", "Toronto skyline at dusk", "Mitul Shah", dict()),
]


def cover(img, zoom, ox, oy):
    """Scales the photo to cover the screen (times zoom) and crops it at (ox, oy)."""
    s = max(W / img.width, H / img.height) * zoom
    img = img.resize((round(img.width * s), round(img.height * s)), Image.LANCZOS)
    x, y = round((img.width - W) * ox), round((img.height - H) * oy)
    return img.crop((x, y, x + W, y + H))


def skyline(img, tolerance=10):
    """Row of the sky/ground boundary for every column. The sky is flooded from the top edge through smooth pixels
    (clouds included); the boundary is the lowest sky pixel of each column, snapped to the strongest edge nearby."""
    q = 2
    small = np.asarray(img.resize((W // q, H // q), Image.BOX).filter(ImageFilter.GaussianBlur(1)), dtype=np.int32)
    h, w = small.shape[:2]
    down = np.abs(small[1:] - small[:-1]).sum(axis=2) < tolerance      # pixel (y+1) is like pixel y
    right = np.abs(small[:, 1:] - small[:, :-1]).sum(axis=2) < tolerance
    sky = np.zeros((h, w), bool)
    sky[0] = True
    while True:
        grow = sky.copy()
        grow[1:] |= sky[:-1] & down
        grow[:-1] |= sky[1:] & down
        grow[:, 1:] |= sky[:, :-1] & right
        grow[:, :-1] |= sky[:, 1:] & right
        if (grow == sky).all():
            break
        sky = grow
    rows = np.where(sky, np.arange(h)[:, None], -1).max(axis=0) + 1
    full = np.asarray(img, dtype=np.float32)
    gf = np.zeros(full.shape[:2], np.float32)
    gf[1:-1] = np.abs(full[2:] - full[:-2]).sum(axis=2)
    rough_y = np.repeat(rows * q, q)[:W]
    out = np.zeros(W, np.float32)
    for x in range(W):
        y0 = int(rough_y[x])
        lo, hi = max(1, y0 - 3), min(H - 2, y0 + 3)
        out[x] = lo + int(np.argmax(gf[lo:hi + 1, x]))
    return out


def place(sky, cx):
    """Clock baseline so the highest point under the clock covers about its lower 40%."""
    span = sky[max(0, cx - HALF_SPAN + 60):min(W, cx + HALF_SPAN - 60)]
    peak = float(span.min())
    return int(np.clip(peak + 0.42 * CAP, 340, 600))   # the date above stays clear of the overscan


def scrims(img):
    a = np.asarray(img, dtype=np.float32)
    y = np.arange(H, dtype=np.float32)[:, None, None] / H
    top = 0.30 * np.clip(1 - y / 0.3, 0, 1) ** 1.5
    bottom = 0.62 * np.clip((y - 0.5) / 0.5, 0, 1) ** 1.6
    a = a * (1 - top) * (1 - bottom)
    return Image.fromarray(np.clip(a, 0, 255).astype(np.uint8))


def font(size, weight, opsz):
    f = ImageFont.truetype(FONT, size)
    f.set_variation_by_axes([opsz, weight])
    return f


def main():
    src = sys.argv[1]
    preview = sys.argv[2] if len(sys.argv) > 2 else None
    os.makedirs(OUT, exist_ok=True)
    index = ["# id|clock center x|clock baseline (layout units, 1920x1080)|ink|front left|front top (pixels of the "
             "1280x720 photo)|caption|credit"]
    shots = []
    for id_, name, caption, credit, opt in SPEC:
        img = Image.open(os.path.join(src, name)).convert("RGB")
        img = cover(img, opt.get("zoom", 1.0), opt.get("ox", 0.5), opt.get("oy", 0.5))
        sky = skyline(img, opt.get("tolerance", 10))
        cx = opt.get("cx", W // 2)
        base = opt.get("base") or place(sky, cx)
        top = int(base - CAP - 24)
        # Ink: dark on a bright sky behind the clock, white otherwise.
        lum = np.asarray(img.convert("L"), dtype=np.float32)[max(0, top):base, cx - 300:cx + 300]
        rows = np.arange(max(0, top), base)[:, None]
        skypix = lum[rows < sky[cx - 300:cx + 300][None, :]]
        ink = 0xFF1C2226 if skypix.size and skypix.mean() > 175 else 0xFFF7F5F0
        final = scrims(img)
        # The cut-out: the scene below the skyline, around the clock only (1 px soft edge).
        yy = np.arange(H, dtype=np.float32)[:, None]
        mask = np.clip(yy - sky[None, :] + 0.5, 0, 1)
        x0, x1 = max(0, cx - HALF_SPAN - 40), min(W, cx + HALF_SPAN + 40)
        y0 = max(0, min(int(sky[x0:x1].min()) - 2, base)),
        y0 = y0[0]
        y1 = min(H, base + 70)
        front = np.dstack([np.asarray(final), (mask * 255).astype(np.uint8)])[y0:y1, x0:x1]
        fimg = Image.fromarray(front, "RGBA")
        s = OW / W
        fl, ft = round(x0 * s), round(y0 * s)
        fr, fb = round(x1 * s), round(y1 * s)
        fimg = fimg.resize((fr - fl, fb - ft), Image.LANCZOS)
        final.resize((OW, OH), Image.LANCZOS).save(os.path.join(OUT, id_ + ".jpg"), quality=86, optimize=True,
                                                   progressive=False)
        fimg.save(os.path.join(OUT, id_ + "-front.png"), optimize=True)
        index.append("|".join([id_, str(cx), str(base), "%08X" % ink, str(fl), str(ft), caption, credit]))
        if preview:
            shots.append(render(final, fimg, (fl, ft), cx, base, ink, caption, id_, sky))
    with open(os.path.join(OUT, "index.txt"), "w", encoding="utf-8", newline="\n") as f:
        f.write("\n".join(index) + "\n")
    if preview:
        os.makedirs(preview, exist_ok=True)
        for id_, shot, debug in shots:
            shot.save(os.path.join(preview, id_ + ".jpg"), quality=88)
            debug.save(os.path.join(preview, id_ + "-sky.jpg"), quality=80)
        cols, tw, th = 4, 480, 270
        sheet = Image.new("RGB", (cols * tw, ((len(shots) + cols - 1) // cols) * th))
        for i, (_, shot, _) in enumerate(shots):
            sheet.paste(shot.resize((tw, th), Image.LANCZOS), ((i % cols) * tw, (i // cols) * th))
        sheet.save(os.path.join(preview, "sheet.jpg"), quality=88)
        for i, (_, _, debug) in enumerate(shots):
            sheet.paste(debug.resize((tw, th), Image.LANCZOS), ((i % cols) * tw, (i // cols) * th))
        sheet.save(os.path.join(preview, "sky.jpg"), quality=88)


def render(final, front, at, cx, base, ink, caption, id_, sky):
    """What the TV shows: photo, date, clock, the cut-out over it, the dock (1280x720)."""
    s = OW / W
    img = final.resize((OW, OH), Image.LANCZOS).convert("RGBA")
    d = ImageDraw.Draw(img)
    rgb = ((ink >> 16) & 255, (ink >> 8) & 255, ink & 255)
    clock = font(round(CLOCK_SIZE * s), 600, 32)
    d.text((cx * s, base * s), "10:08", font=clock, fill=rgb, anchor="ms")
    date = font(round(36 * s), 500, 14)
    d.text((cx * s, (base - CAP - 40) * s), "Thursday, October 30", font=date, fill=rgb, anchor="ms")
    img.alpha_composite(front, at)
    small = font(round(22 * s), 500, 14)
    d = ImageDraw.Draw(img)
    for i in range(4):
        x = (960 - 2 * 270 - 1.5 * 48 + i * (270 + 48)) * s
        d.rounded_rectangle((x, 854 * s, x + 270 * s, 980 * s), radius=22 * s, fill=(12, 16, 18, 150))
    d.text((80 * s, 1040 * s), caption, font=small, fill=(255, 255, 255, 170), anchor="ls")
    debug = final.resize((OW, OH)).convert("RGB")
    dd = ImageDraw.Draw(debug)
    dd.line([(x * s, sky[x] * s) for x in range(0, W, 2)], fill=(255, 0, 255), width=2)
    return id_, img.convert("RGB"), debug


if __name__ == "__main__":
    main()
