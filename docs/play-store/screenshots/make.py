"""Builds the framed Play Store screenshots: a phone-shaped frame around a real screenshot, with a caption.
Run from this folder (needs Pillow), then render each shot-N.html at 1080x1920 (see the README note in the repo history)."""
import html

from PIL import Image

# (screenshot, caption, name, rows to crop off the top). Screenshots are 1080 px wide.
# The raw captures include the status bar (clock, battery), which the frame's rounded corners would clip, so it is
# cropped off: 100 px on the Pixel 7 Pro captures (scaled to 1080 x 2340), 142 px on the Pixel 10 emulator (1080 x 2424).
SHOTS = [
    ("headlines.png", "Headlines from the other side of the world", "headlines", 100),
    ("ocean.png", "Most of the planet is water, so start at sea", "ocean", 100),
    ("headlines-dark.png", "Easy on the eyes at night", "dark", 100),
    ("about-privacy.png", "Private by default", "private", 142),
]
# A little padding above the cropped screen. It takes the colours of the screenshot's first row after the crop, so the
# flag wash behind the header carries on to the top of the frame instead of stopping at a flat strip.
PAD_TOP = 34
TEMPLATE = """<!DOCTYPE html>
<html lang="en"><head><meta charset="utf-8">
<link rel="stylesheet" href="https://fonts.googleapis.com/css2?family=Roboto:wght@500;700&display=swap">
<style>
  html, body {{ margin: 0; width: 1080px; height: 1920px; overflow: hidden; }}
  body {{ font-family: Roboto, "Segoe UI", system-ui, sans-serif; color: #eaf0ff; position: relative;
         background: linear-gradient(160deg, #161a40 0%, #1b1f4b 50%, #2b3190 130%); }}
  h1 {{ position: absolute; left: 80px; right: 80px; top: 120px; margin: 0; font-size: 84px; line-height: 1.08; font-weight: 700; letter-spacing: -1px; }}
  .bar {{ position: absolute; left: 80px; top: 60px; width: 96px; height: 8px; border-radius: 4px; background: #ffb703; }}
  .phone {{ position: absolute; left: 160px; top: 470px; width: 760px; box-sizing: border-box; border: 16px solid #0b0d27;
            border-radius: 88px; overflow: hidden; background: {bg}; padding-top: {pad}px; box-shadow: 0 30px 80px rgba(0,0,0,.45); }}
  .phone .clip {{ overflow: hidden; }}
  .phone img {{ display: block; width: 100%; margin-top: -{crop}px; }}
</style></head>
<body><div class="bar"></div><h1>{caption}</h1>
<div class="phone"><div class="clip"><img src="../../screenshots/{image}" alt=""></div></div></body></html>
"""

def top_row_gradient(path: str, crop: int) -> str:
    """The screenshot's colours along the first row below the crop, as a left-to-right CSS gradient."""
    with Image.open(f"../../screenshots/{path}") as im:
        im = im.convert("RGB")
        stops = []
        for i in range(9):
            x = round(i * (im.width - 1) / 8)
            r, g, b = im.getpixel((x, crop))
            stops.append(f"rgb({r},{g},{b}) {i * 12.5:g}%")
    return "linear-gradient(to right, " + ", ".join(stops) + ")"


for n, (image, caption, _name, crop_top) in enumerate(SHOTS, 1):
    # The image is shown 728 px wide (760 less the 16 px border each side), so 1080 raw px = 728 px.
    crop = round(crop_top * 728 / 1080)
    with open(f"shot-{n}.html", "w", encoding="utf-8", newline="\n") as f:
        f.write(TEMPLATE.format(caption=html.escape(caption), image=image, bg=top_row_gradient(image, crop_top), pad=PAD_TOP, crop=crop))
print(len(SHOTS), "pages")
