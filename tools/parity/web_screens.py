#!/usr/bin/env python3
"""Step B: screenshot the website in the same sizes/themes/languages as the app.

Usage:
  python3 tools/parity/web_screens.py --site ../examcamp-main --out shots/web
  python3 tools/parity/web_screens.py --site https://your-site.example --out shots/web --sizes 390x844

Output names: <screen>__<WxH>__<light|dark>__<en|bn>.png (the app script uses the same names).
Needs: pip install playwright && playwright install chromium
"""
import argparse, functools, http.server, os, socketserver, threading
from playwright.sync_api import sync_playwright

# screen name -> view name passed to the site's showView(). Views that need data/auth
# (history, statistics, practice-admin ...) render their empty/guest state.
SCREENS = {
    "home": "home",
    "history": "history",
    "practice": "practice",
    "practice-subjects": "practice-subjects",
    "practice-mode": "practice-mode",
    "live": "live-exam",
    "statistics": "statistics",
    "generating": "generating",
    "exam": "exam",
}

def serve(directory):
    handler = functools.partial(http.server.SimpleHTTPRequestHandler, directory=directory)
    class Quiet(handler.func):
        def log_message(self, *a): pass
    srv = socketserver.TCPServer(("127.0.0.1", 0), functools.partial(Quiet, directory=directory))
    threading.Thread(target=srv.serve_forever, daemon=True).start()
    return srv, f"http://127.0.0.1:{srv.server_address[1]}/index.html"

def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--site", required=True, help="folder with index.html, or an http(s) URL")
    ap.add_argument("--out", default="shots/web")
    ap.add_argument("--sizes", default="390x844,360x800,412x915")
    ap.add_argument("--dpr", type=float, default=3)
    ap.add_argument("--themes", default="light,dark")
    ap.add_argument("--langs", default="en,bn")
    ap.add_argument("--screens", default=",".join(SCREENS))
    a = ap.parse_args()
    os.makedirs(a.out, exist_ok=True)
    srv = None
    url = a.site
    if not url.startswith("http"):
        srv, url = serve(os.path.abspath(a.site))
    with sync_playwright() as p:
        b = p.chromium.launch()
        for size in a.sizes.split(","):
            w, h = map(int, size.split("x"))
            for theme in a.themes.split(","):
                for lang in a.langs.split(","):
                    ctx = b.new_context(viewport={"width": w, "height": h}, device_scale_factor=a.dpr,
                                        is_mobile=True, has_touch=True, reduced_motion="reduce",
                                        color_scheme=theme)
                    ctx.add_init_script(
                        f"localStorage.setItem('mcq-exam-theme','{theme}');localStorage.setItem('mcq-exam-lang','{lang}');")
                    page = ctx.new_page()
                    page.on("pageerror", lambda e: print("  page error:", str(e)[:160]))
                    page.goto(url, wait_until="networkidle")
                    page.add_style_tag(content="*{animation:none!important;transition:none!important;caret-color:transparent!important}")
                    for name in a.screens.split(","):
                        view = SCREENS[name]
                        try:
                            page.evaluate(f"typeof showView==='function' && showView('{view}')")
                        except Exception as e:
                            print("skip", name, e)
                        page.wait_for_timeout(250)
                        f = f"{a.out}/{name}__{size}__{theme}__{lang}.png"
                        page.screenshot(path=f, full_page=False)
                        print("saved", f)
                    ctx.close()
        b.close()
    if srv: srv.shutdown()

if __name__ == "__main__":
    main()
