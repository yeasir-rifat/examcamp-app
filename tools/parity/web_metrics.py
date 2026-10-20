# Prints computed CSS metrics (size, padding, radius, colours, font) of core components for light/dark.
# Needs playwright + chromium. Edit the 'directory' path below to your examcamp-main folder.
import functools,http.server,socketserver,threading,json
from playwright.sync_api import sync_playwright
h=functools.partial(http.server.SimpleHTTPRequestHandler,directory='/home/claude/w/examcamp-main')
class Q(http.server.SimpleHTTPRequestHandler):
    def log_message(s,*a):pass
h=functools.partial(Q,directory='/home/claude/w/examcamp-main')
s=socketserver.TCPServer(("127.0.0.1",0),h);threading.Thread(target=s.serve_forever,daemon=True).start()
html='''<div id=t>
<button class="btn btn-primary">Primary</button>
<button class="btn btn-secondary">Secondary</button>
<button class="btn btn-secondary btn-sm">Small</button>
<button class="btn btn-danger">Danger</button>
<button class="btn btn-ghost">Ghost</button>
<button class="btn btn-outline">Outline</button>
<div class="card">Card</div>
<button class="icon-btn">i</button>
<div class="segmented"><button class="segmented__option">A</button></div>
<input class="form-control" value="x"><select class="form-control"><option>a</option></select><textarea class="form-control">t</textarea><label class="form-label">L</label><div class="dashboard-item">D</div>

</div>'''
props=['height','minHeight','paddingTop','paddingRight','paddingBottom','paddingLeft','borderTopWidth','borderTopColor','borderTopLeftRadius','backgroundColor','color','fontSize','fontWeight','lineHeight','letterSpacing','boxShadow','width']
with sync_playwright() as p:
    b=p.chromium.launch();ctx=b.new_context(viewport={'width':390,'height':844});pg=ctx.new_page()
    pg.goto(f"http://127.0.0.1:{s.server_address[1]}/index.html",wait_until="load")
    for theme in ['light','dark']:
        pg.evaluate("t=>document.documentElement.setAttribute('data-theme',t)",theme)
        pg.evaluate("h=>{let d=document.getElementById('t');if(d)d.remove();document.body.insertAdjacentHTML('beforeend',h)}",html)
        res=pg.evaluate("""props=>[...document.querySelectorAll('#t > *')].map(e=>{const c=getComputedStyle(e);const o={tag:e.tagName+'.'+e.className};props.forEach(k=>o[k]=c[k]);return o})""",props)
        print('=====',theme)
        for r in res:
            print(r['tag'],{k:v for k,v in r.items() if k!='tag' and v not in ('0px','none','normal','rgba(0, 0, 0, 0)')})
