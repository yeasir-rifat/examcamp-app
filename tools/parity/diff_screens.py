#!/usr/bin/env python3
"""Step B: pair web/app screenshots by name, write side-by-side + diff images and a score table.

Usage: python3 tools/parity/diff_screens.py --web shots/web --app shots/app --out shots/diff
The app image has status/navigation bars; --crop-top/--crop-bottom (px) cut them from the app image.
Send the three images (web, app, diff) for a screen and the fix can be derived from them.
"""
import argparse, os
import numpy as np
from PIL import Image, ImageChops

def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--web", default="shots/web"); ap.add_argument("--app", default="shots/app")
    ap.add_argument("--out", default="shots/diff")
    ap.add_argument("--crop-top", type=int, default=0); ap.add_argument("--crop-bottom", type=int, default=0)
    ap.add_argument("--threshold", type=int, default=24, help="per-channel difference that counts as a changed pixel")
    a = ap.parse_args()
    os.makedirs(a.out, exist_ok=True)
    rows = []
    for name in sorted(os.listdir(a.web)):
        if not name.endswith(".png") or not os.path.exists(os.path.join(a.app, name)): continue
        w = Image.open(os.path.join(a.web, name)).convert("RGB")
        p = Image.open(os.path.join(a.app, name)).convert("RGB")
        if a.crop_top or a.crop_bottom:
            p = p.crop((0, a.crop_top, p.width, p.height - a.crop_bottom))
        if p.size != w.size:
            p = p.resize(w.size, Image.LANCZOS)
        d = np.abs(np.asarray(w, dtype=int) - np.asarray(p, dtype=int)).max(axis=2)
        mask = d > a.threshold
        pct = 100.0 * mask.mean()
        heat = np.asarray(w).copy() // 3 + 170
        heat[mask] = (238, 29, 54)
        diff = Image.fromarray(heat.astype("uint8"))
        sheet = Image.new("RGB", (w.width * 3 + 40, w.height), (128, 128, 128))
        for i, im in enumerate((w, p, diff)):
            sheet.paste(im, (i * (w.width + 20), 0))
        sheet.save(os.path.join(a.out, name.replace(".png", "__web-app-diff.png")))
        rows.append((pct, name))
    print(f"{'diff %':>8}  screen")
    for pct, name in sorted(rows, reverse=True):
        print(f"{pct:8.2f}  {name}")
    if not rows: print("no matching file names between", a.web, "and", a.app)

if __name__ == "__main__":
    main()
