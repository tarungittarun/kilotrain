#!/usr/bin/env python3
"""Candidate source probe for alternatives to NTES (WIMT, ConfirmTkt, ixigo, RailYatri).
Capture-first: structural signatures only, redacted fixtures. Verdicts: usable|usable-but-labelled|blocked-by-network|blocked-by-shape.
"""
from __future__ import annotations
import argparse, datetime as _dt, hashlib, json, os, re, shutil, socket, ssl, sys, time, urllib.error, urllib.parse as uparse, urllib.request as ureq

CONNECT_TIMEOUT_S = 10.0
READ_TIMEOUT_S = 20.0
MAX_FIXTURE_BYTES = 4096
NOW_ISO = _dt.datetime.now(_dt.timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")

USABLE="usable"; USABLE_LABELLED="usable-but-labelled"; BLOCKED_NET="blocked-by-network"; BLOCKED_SHAPE="blocked-by-shape"

UAS = [
  "Dalvik/2.1.0 (Linux; Android 11)",
  "Mozilla/5.0 (Linux; Android 13; SM-G990B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36",
  "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
]

def _open(url, data=None, method="GET", ua=UAS[0], headers=None):
    req = ureq.Request(url, data=data, method=method)
    req.add_header("User-Agent", ua)
    req.add_header("Accept", "*/*")
    req.add_header("Accept-Language", "en-IN,en;q=0.9")
    if headers:
        for k,v in headers.items():
            try: req.add_header(k,v)
            except: pass
    ctx = ssl.create_default_context()
    t0=time.monotonic()
    try:
        with ureq.urlopen(req, timeout=READ_TIMEOUT_S, context=ctx) as r:
            body=r.read(MAX_FIXTURE_BYTES*4)
            return True, {"status":r.status,"final_url":r.geturl(),"elapsed_s":round(time.monotonic()-t0,3),
                          "content_type":r.headers.get("Content-Type",""),"body_head":body[:MAX_FIXTURE_BYTES],"body_len":len(body),"headers":dict(r.headers)}
    except urllib.error.HTTPError as e:
        try: body=e.read(MAX_FIXTURE_BYTES*4)
        except: body=b""
        return True, {"status":e.code,"final_url":url,"elapsed_s":round(time.monotonic()-t0,3),
                      "content_type":e.headers.get("Content-Type","") if e.headers else "","body_head":body[:MAX_FIXTURE_BYTES],"body_len":len(body),"headers":dict(e.headers) if e.headers else {}}
    except (Exception,urllib.error.URLError,socket.timeout,ssl.SSLError,OSError,ConnectionError) as e:
        return False, {"status":0,"final_url":url,"elapsed_s":round(time.monotonic()-t0,3),"error":f"{type(e).__name__}:{getattr(e,'reason',e)}"}

def retry(url, **kw):
    ok,d=_open(url,**kw); 
    if ok: return ok,d
    return _open(url,**kw)

def sig_bytes(b, ct):
    s=b.decode("utf-8","replace")
    lines=s.splitlines()
    delim={d:s.count(d) for d in ("~","|",",","\t",";")}
    nonempty=[ln for ln in lines if ln.strip()]
    fpl=[len(ln.split("~")) if "~" in ln else (len(ln.split("|")) if "|" in ln else len(ln.split(","))) for ln in nonempty[:60]]
    import collections
    h={"kind":"json" if "json" in (ct or "").lower() or s.strip().startswith(("{","[")) else ("html" if "<html" in s.lower() or "<!doctype" in s.lower() else "text"),
       "line_count":len(lines),"nonempty_lines":len(nonempty),"first_line_len":len(lines[0]) if lines else 0,"max_line_len":max((len(x) for x in lines),default=0),
       "delimiter_counts":delim,"fields_per_line_head":fpl[:12],"fields_per_line_histogram":dict(collections.Counter(fpl)),
       "csr_feed_stamp_seen":bool(re.search(r"\d{4}-\d{1,2}-\d{1,2}-\d{1,2}-\d{1,2}-\d{1,2}", s)),
       "has_json_like":bool(re.search(r'"\w+"\s*:', s)),"looks_throttle":any(m in s.lower() for m in ("try again","please try again","too many","rate limit","429","captcha","blocked","access denied")),
       "looks_html": "<html" in s.lower() or "<body" in s.lower(),
       "sha256_head":hashlib.sha256(b[:MAX_FIXTURE_BYTES]).hexdigest()[:16]}
    return h

def write_fixture(src,name,detail,sig,fixtures_dir):
    od=os.path.join(fixtures_dir,src); os.makedirs(od,exist_ok=True)
    p=os.path.join(od,f"{name}.txt")
    head=detail.get("body_head",b"")
    with open(p,"w",encoding="utf-8",errors="replace") as f:
        f.write(f"# source={src} name={name}\n# captured={NOW_ISO} url={detail.get('final_url')}\n# status={detail.get('status')} elapsed_s={detail.get('elapsed_s')} ct={detail.get('content_type')}\n# redacted first {len(head)} bytes; signature only\n")
        f.write(f"# signature={json.dumps(sig,sort_keys=True)}\n")
        f.write(head.decode("utf-8","replace"))
    with open(p+".sig.json","w") as f: json.dump(sig,f,indent=2,sort_keys=True)
    return p

def probe_wimt(fd):
    out={"source":"WIMT (whereismytrain.org.in)","attempts":[],"measured":NOW_ISO}
    base="https://whereismytrain.org.in"
    cands=[
      (base,"home"),
      (base+"/","home_slash"),
      (base+"/train-running-status","tr_running"),
      (base+"/live-train-status","live_tr"),
      (base+"/api/trainStatus","api_trainstatus"),
      (base+"/api/liveTrainStatus","api_livetr"),
      (base+"/api/getTrainStatus","api_gettr"),
      (base+"/api/v1/train/status","api_v1_tr"),
      (base+"/api/v1/live-status","api_v1_live"),
      (base+"/api/v1/train-running-status","api_v1_running"),
    ]
    for url,name in cands:
        for ua in UAS[:2]:
            ok,d=retry(url,ua=ua)
            e={"url":url,"name":name,"ua":ua[:30],"ok":ok,"status":d.get("status"),"elapsed_s":d.get("elapsed_s"),"error":d.get("error"),"ct":d.get("content_type")}
            if ok:
                b=d.get("body_head",b""); s=sig_bytes(b,e["ct"]); e["sig"]=s; e["fixture"]=write_fixture("cands_wimt",name,d,s,fd)
            out["attempts"].append(e); time.sleep(0.05)
    answered=[a for a in out["attempts"] if a.get("ok") and a.get("status")==200]
    jsonish=[a for a in answered if a.get("sig",{}).get("kind")=="json" and a["sig"].get("has_json_like")]
    if jsonish:
        out["verdict"]=USABLE_LABELLED; out["reason"]="JSON-like responses seen on some paths (app-internal style). Need exact XHRs from device to confirm stable keyless API."
    elif any(a.get("sig",{}).get("looks_throttle") for a in answered):
        out["verdict"]=BLOCKED_SHAPE; out["reason"]="200s but throttle/anti-bot markers; likely requires headers/session."
    elif answered:
        out["verdict"]=BLOCKED_SHAPE; out["reason"]="Mostly HTML; no obvious stable public JSON endpoint from web probes."
    else:
        out["verdict"]=BLOCKED_NET; out["reason"]="Few 2xx answers; intermittent/blocked-like or needs specific headers."
    return out

def probe_confirmtkt(fd):
    out={"source":"ConfirmTkt (confirmtkt.com)","attempts":[],"measured":NOW_ISO}
    base="https://www.confirmtkt.com"
    cands=[
      (base+"/train-running-status","running"),
      (base+"/api/train-running-status","api_running"),
      (base+"/api/v1/train/running","api_v1_run"),
      (base+"/api/v2/train/running-status","api_v2_run"),
      (base+"/ajax/trainRunningStatus","ajax_run"),
      (base+"/services/trains/runningStatus","svc_run"),
      (base+"/train-running-status?trainNo=12345","run_q"),
    ]
    for url,name in cands:
        for ua in UAS[:2]:
            ok,d=retry(url,ua=ua,headers={"Referer":base+"/train-running-status","Origin":base})
            e={"url":url,"name":name,"ua":ua[:30],"ok":ok,"status":d.get("status"),"elapsed_s":d.get("elapsed_s"),"error":d.get("error"),"ct":d.get("content_type")}
            if ok:
                b=d.get("body_head",b""); s=sig_bytes(b,e["ct"]); e["sig"]=s; e["fixture"]=write_fixture("cands_confirmtkt",name,d,s,fd)
            out["attempts"].append(e); time.sleep(0.05)
    answered=[a for a in out["attempts"] if a.get("ok") and a.get("status")==200]
    jsonish=[a for a in answered if a.get("sig",{}).get("kind")=="json"]
    if jsonish:
        out["verdict"]=USABLE_LABELLED; out["reason"]="JSON endpoints appear; often require Referer/Origin/cookies and can change shape. Needs captured XHRs from real app/web."
    elif answered:
        out["verdict"]=BLOCKED_SHAPE; out["reason"]="HTML/JS; likely internal XHR calls post-load."
    else:
        out["verdict"]=BLOCKED_NET; out["reason"]="No stable 2xx JSON from naive probes."
    return out

def probe_ixigo(fd):
    out={"source":"ixigo (ixigo.com)","attempts":[],"measured":NOW_ISO}
    base="https://www.ixigo.com"
    cands=[
      (base+"/trains/train-running-status","tr_run"),
      (base+"/api/trains/train-running-status","api_tr_run"),
      (base+"/api/v1/trains/running-status","api_v1_run"),
      (base+"/api/v3/trains/running-status","api_v3_run"),
      (base+"/train-api/v3/trains/running-status","tapi_v3"),
      (base+"/api/rt/train/running","api_rt"),
    ]
    for url,name in cands:
        for ua in UAS[:2]:
            ok,d=retry(url,ua=ua,headers={"Referer":base+"/trains/train-running-status","Origin":base,"X-Requested-With":"XMLHttpRequest"})
            e={"url":url,"name":name,"ua":ua[:30],"ok":ok,"status":d.get("status"),"elapsed_s":d.get("elapsed_s"),"error":d.get("error"),"ct":d.get("content_type")}
            if ok:
                b=d.get("body_head",b""); s=sig_bytes(b,e["ct"]); e["sig"]=s; e["fixture"]=write_fixture("cands_ixigo",name,d,s,fd)
            out["attempts"].append(e); time.sleep(0.05)
    answered=[a for a in out["attempts"] if a.get("ok") and a.get("status")==200]
    jsonish=[a for a in answered if a.get("sig",{}).get("kind")=="json"]
    if jsonish:
        out["verdict"]=USABLE_LABELLED; out["reason"]="Some JSON seen; ixigo often uses versioned/internal APIs + dynamic headers; shape varies."
    elif answered:
        out["verdict"]=BLOCKED_SHAPE; out["reason"]="HTML dominant; XHRs likely loaded dynamically."
    else:
        out["verdict"]=BLOCKED_NET; out["reason"]="No obvious public JSON from probes."
    return out

def probe_railyatri(fd):
    out={"source":"RailYatri (railyatri.in)","attempts":[],"measured":NOW_ISO}
    base="https://www.railyatri.in"
    m="https://www.railyatri.in/m"
    cands=[
      (m+"/live-train-status","m_live"),
      (base+"/m/live-train-status","base_m_live"),
      (base+"/api/live-train-status","api_live"),
      (base+"/api/v1/train/live-status","api_v1_live"),
      (base+"/mobile/v1/train/running-status","mob_v1_run"),
      (base+"/mobile/v2/train/liveStatus","mob_v2_live"),
      (base+"/rest/mobile/train/live/status","rest_mob_live"),
      (base+"/api/mtb/train/runningStatus","api_mtb_run"),
    ]
    for url,name in cands:
        for ua in UAS[:2]:
            ok,d=retry(url,ua=ua,headers={"Referer":m+"/live-train-status","Origin":base,"X-Requested-With":"XMLHttpRequest"})
            e={"url":url,"name":name,"ua":ua[:30],"ok":ok,"status":d.get("status"),"elapsed_s":d.get("elapsed_s"),"error":d.get("error"),"ct":d.get("content_type")}
            if ok:
                b=d.get("body_head",b""); s=sig_bytes(b,e["ct"]); e["sig"]=s; e["fixture"]=write_fixture("cands_railyatri",name,d,s,fd)
            out["attempts"].append(e); time.sleep(0.05)
    answered=[a for a in out["attempts"] if a.get("ok") and a.get("status")==200]
    jsonish=[a for a in answered if a.get("sig",{}).get("kind")=="json"]
    if jsonish:
        out["verdict"]=USABLE_LABELLED; out["reason"]="JSON responses observed on mobile-style paths; RailYatri mixes crowdsourced + operator data. Age/provenance fields vary and endpoints evolve."
    elif answered:
        out["verdict"]=BLOCKED_SHAPE; out["reason"]="HTML-heavy; real calls likely XHR with auth-like headers or dynamic tokens."
    else:
        out["verdict"]=BLOCKED_NET; out["reason"]="No obvious stable JSON from web probes."
    return out

def main():
    ap=argparse.ArgumentParser(); ap.add_argument("--fixtures",default="poc/fixtures"); ap.add_argument("--out",default="poc/candidate_probe_results.json")
    a=ap.parse_args(); fd=a.fixtures; os.makedirs(fd,exist_ok=True)
    res={"measured_at":NOW_ISO,"sources":[]}
    res["sources"].append(probe_wimt(fd)); res["sources"].append(probe_confirmtkt(fd)); res["sources"].append(probe_ixigo(fd)); res["sources"].append(probe_railyatri(fd))
    with open(a.out,"w") as f: json.dump(res,f,indent=2)
    for s in res["sources"]:
        print(f"{s['source']}: {s['verdict']} — {s['reason']}")
    return 0

if __name__=="__main__": sys.exit(main())
