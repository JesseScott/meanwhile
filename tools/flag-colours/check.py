"""Check the flag stripe table (core/.../Flags.kt) against reference flag images.

For every country in the table this downloads the flag from flagcdn.com (once, into --cache) and reports any
table colour that is not one of the solid colours in that image. With --sheets it also writes contact sheets of
each reference flag beside a hard-edged drawing of the table entry, to compare arrangements by eye.

Territories that deliberately use their state's flag (see ALIASES) have their colours compared with the state's
image; the contact sheets still show the territory's own reference image beside it.

Needs Pillow. Run from the repo root:
    python tools/flag-colours/check.py --cache /tmp/flags [--sheets /tmp/flag-sheets]
Exits 1 if any colour is not in its reference image.
"""
import argparse
import os
import re
import sys
import urllib.request

from PIL import Image, ImageDraw

TABLE = "core/src/main/kotlin/tt/co/jesses/meanwhile/core/Flags.kt"
ALIASES = {"FRANCE": "FR", "NORWAY": "NO", "NETHERLANDS": "NL", "AUSTRALIA": "AU", "UNITED_STATES": "US"}
MIN_AREA = 0.02  # a solid colour covers at least this much of the image; anything smaller is an edge or an emblem


def parse(path):
    src = open(path, encoding="utf-8").read()
    consts = {m.group(1): int(m.group(2), 16) for m in re.finditer(r"private const val (\w+) = (0x[0-9A-Fa-f]+)", src)}

    def colours(args):
        return [consts[a] if a in consts else int(a, 16) for a in (t.strip() for t in args.split(",")) if a]

    named = {m.group(1): (m.group(2), colours(m.group(3))) for m in re.finditer(r"private val (\w+) = (\w+)\(([^)]*)\)", src)}
    flags = {}
    for m in re.finditer(r'"([A-Z]{2})" to (\w+)(?:\(([^)]*)\))?,', src):
        if m.group(3) is None:
            flags[m.group(1)] = named[m.group(2)] + (ALIASES[m.group(2)],)
        else:
            flags[m.group(1)] = (m.group(2), colours(m.group(3)), m.group(1))
    return flags


def reference(iso, cache):
    path = os.path.join(cache, iso.lower() + ".png")
    if not os.path.exists(path):
        urllib.request.urlretrieve("https://flagcdn.com/w160/%s.png" % iso.lower(), path)
    image = Image.open(path).convert("RGBA")
    return Image.alpha_composite(Image.new("RGBA", image.size, (255, 255, 255, 255)), image).convert("RGB")


def rgb(colour):
    return (colour >> 16) & 255, (colour >> 8) & 255, colour & 255


def draw(layout, colours, w, h):
    image = Image.new("RGB", (w, h))
    d = ImageDraw.Draw(image)
    c = [rgb(x) for x in colours]

    def bands(cs, top_to_bottom):
        n = len(cs)
        for i, colour in enumerate(cs):
            d.rectangle([0, i * h // n, w, (i + 1) * h // n] if top_to_bottom else [i * w // n, 0, (i + 1) * w // n, h], fill=colour)

    if layout == "h":
        bands(c, True)
    elif layout == "v":
        bands(c, False)
    elif layout in ("nordic", "cross"):
        d.rectangle([0, 0, w, h], fill=c[0])
        cx = w * 0.36 if layout == "nordic" else w / 2
        for colour, t in zip(c[1:], (h * 0.3, h * 0.13)):
            d.rectangle([0, h / 2 - t / 2, w, h / 2 + t / 2], fill=colour)
            d.rectangle([cx - t / 2, 0, cx + t / 2, h], fill=colour)
    elif layout == "disc":
        d.rectangle([0, 0, w, h], fill=c[0])
        for colour, r in zip(c[1:], (h * 0.33, h * 0.18)):
            d.ellipse([w / 2 - r, h / 2 - r, w / 2 + r, h / 2 + r], fill=colour)
    elif layout == "hoist":
        bands(c[1:], True)
        d.polygon([(0, 0), (w * 0.4, h / 2), (0, h)], fill=c[0])
    elif layout == "canton":
        bands(c[1:], True)
        d.rectangle([0, 0, w * 0.4, h * 0.5], fill=c[0])
    return image


def main():
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--cache", required=True, help="folder for the downloaded reference images")
    parser.add_argument("--sheets", help="folder to write side-by-side contact sheets into")
    args = parser.parse_args()
    os.makedirs(args.cache, exist_ok=True)

    flags = parse(TABLE)
    wrong = 0
    pairs = []
    for iso in sorted(flags):
        layout, colours, source = flags[iso]
        image = reference(source, args.cache)
        area = image.size[0] * image.size[1]
        solid = {c for n, c in image.getcolors(area) if n >= area * MIN_AREA}
        missing = ["#%06X" % c for c in dict.fromkeys(colours) if rgb(c) not in solid]
        if missing:
            wrong += 1
            print("%s: %s not in the reference image (it has %s)" % (
                iso, ", ".join(missing), ", ".join("#%02X%02X%02X" % c for c in sorted(solid))))
        if args.sheets:
            pairs.append((iso, reference(iso, args.cache).resize((150, 100)), draw(layout, colours, 150, 100)))

    if args.sheets:
        os.makedirs(args.sheets, exist_ok=True)
        per = 52
        for start in range(0, len(pairs), per):
            sheet = Image.new("RGB", (4 * 350, 13 * 112), (128, 128, 128))
            d = ImageDraw.Draw(sheet)
            for i, (iso, ref, mine) in enumerate(pairs[start:start + per]):
                x, y = (i % 4) * 350, (i // 4) * 112
                d.text((x + 4, y + 45), iso, fill=(255, 255, 255))
                sheet.paste(ref, (x + 30, y + 6))
                sheet.paste(mine, (x + 188, y + 6))
            sheet.save(os.path.join(args.sheets, "sheet%d.png" % (start // per)))

    print("%d flags checked, %d with a colour that is not in its reference image" % (len(flags), wrong))
    return 1 if wrong else 0


if __name__ == "__main__":
    sys.exit(main())
