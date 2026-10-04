"""Builds the framed Play Store screenshots: a phone-shaped frame around a real screenshot, with a caption.
Run from this folder, then render each shot-N.html at 1080x1920 (see the README note in the repo history)."""
import html

# (screenshot, caption, name, the app's own background colour, used for the strip above the cropped status bar)
LIGHT, DARK = "#fbfcff", "#1a1b21"
SHOTS = [
    ("headlines.png", "Headlines from the other side of the world", "headlines", LIGHT),
    ("ocean.png", "Most of the planet is water, so start at sea", "ocean", LIGHT),
    ("headlines-dark.png", "Easy on the eyes at night", "dark", DARK),
    ("about-privacy.png", "Private by default", "private", LIGHT),
]
# The raw captures include the emulator's status bar (clock, battery), which the frame's rounded corners would clip.
# Crop it off (CROP_TOP of the 2400 px height) and give the screen a little padding in the app's own colour instead.
CROP_TOP = 96
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

for n, (image, caption, _name, bg) in enumerate(SHOTS, 1):
    # The image is shown 728 px wide (760 less the 16 px border each side), so 1080 raw px = 728 px.
    crop = round(CROP_TOP * 728 / 1080)
    with open(f"shot-{n}.html", "w", encoding="utf-8", newline="\n") as f:
        f.write(TEMPLATE.format(caption=html.escape(caption), image=image, bg=bg, pad=PAD_TOP, crop=crop))
print(len(SHOTS), "pages")
