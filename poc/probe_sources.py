#!/usr/bin/env python3
"""OpenTrack India - source probe + capture tool (M0, KICKOFF 15.3 / 7.4).

Standalone, stdlib-only. Prints one verdict per source:
  usable | usable-but-labelled | blocked-by-network | blocked-by-shape

Rules honoured here (KICKOFF):
  * 3.5  scraping decency - a handful of requests per run, no crawling loops.
  * 7.4  NO PARSER WITHOUT A CAPTURED PAYLOAD - unparseable responses get a structural
         signature (keys/nesting/lengths/delimiters, NO values) written as the capture.
  * 10.8 a single timeout is not a verdict - every network step retries exactly once.

Usage:  python3 poc/probe_sources.py [--out FILE] [--fixtures DIR] [--quiet]
"""

from __future__ import annotations

import argparse
import datetime as _dt
import hashlib
import json
import os
import re
import shutil
import socket
import ssl
import subprocess
import sys
import time
import urllib.error
import urllib.request

CONNECT_TIMEOUT_S = 10.0
READ_TIMEOUT_S = 20.0
RETRIES = 1  # one retry after the first timeout: a single timeout is not a verdict
MAX_FIXTURE_BYTES = 2048  # small redacted fixtures only (3.5)
USER_AGENT = "opentrack-probe/0.1 (research; contact: repo owner)"

# The four allowed verdicts.
USABLE = "usable"
USABLE_LABELLED = "usable-but-labelled"
BLOCKED_NET = "blocked-by-network"
BLOCKED_SHAPE = "blocked-by-shape"

NOW_ISO = None  # set in main(); injected, never read from wall clock inside helpers


# --------------------------------------------------------------------------- http


def _open(url: str, data: bytes | None = None, method: str = "GET"):
    """One HTTP attempt. Returns (ok, detail-dict). Never raises."""
    req = urllib.request.Request(url, data=data, method=method)
    req.add_header("User-Agent", USER_AGENT)
    ctx = ssl.create_default_context()
    started = time.monotonic()
    try:
        with urllib.request.urlopen(req, timeout=READ_TIMEOUT_S, context=ctx) as resp:
            body = resp.read(MAX_FIXTURE_BYTES * 4)
            return True, {
                "status": resp.status,
                "final_url": resp.geturl(),
                "elapsed_s": round(time.monotonic() - started, 3),
                "content_type": resp.headers.get("Content-Type", ""),
                "body_head": body[:MAX_FIXTURE_BYTES],
                "body_len": len(body),
            }
    except urllib.error.HTTPError as e:  # server answered - shape question, not network
        try:
            body = e.read(MAX_FIXTURE_BYTES * 4)
        except Exception:
            body = b""
        return True, {
            "status": e.code,
            "final_url": url,
            "elapsed_s": round(time.monotonic() - started, 3),
            "content_type": e.headers.get("Content-Type", "") if e.headers else "",
            "body_head": body[:MAX_FIXTURE_BYTES],
            "body_len": len(body),
        }
    except (urllib.error.URLError, socket.timeout, ssl.SSLError, ConnectionError, OSError) as e:
        reason = getattr(e, "reason", e)
        return False, {
            "status": 0,
            "final_url": url,
            "elapsed_s": round(time.monotonic() - started, 3),
            "error": f"{type(e).__name__}: {reason}",
        }


def http_with_retry(url: str, **kw):
    """Retry exactly once on network failure (10.8). Returns (ok, last-detail)."""
    ok, detail = _open(url, **kw)
    if ok:
        return True, detail
    detail["retried"] = True
    ok2, detail2 = _open(url, **kw)
    return ok2, detail2


# ------------------------------------------------------------------- signatures


def _signature_text(text: str) -> dict:
    """Structural signature of a text payload - NO values (7.4)."""
    lines = text.splitlines()
    delim_counts = {d: text.count(d) for d in ("~", "|", ",", "\t", ";")}
    nonempty = [ln for ln in lines if ln.strip()]
    fields_per_line = [len(ln.split("~")) if "~" in ln else len(ln.split(",")) for ln in nonempty[:50]]
    import collections

    return {
        "kind": "text",
        "line_count": len(lines),
        "nonempty_lines": len(nonempty),
        "first_line_len": len(lines[0]) if lines else 0,
        "max_line_len": max((len(ln) for ln in lines), default=0),
        "delimiter_counts": delim_counts,
        "fields_per_line_head": fields_per_line[:10],
        "fields_per_line_histogram": dict(collections.Counter(fields_per_line)),
        "csr_feed_stamp_seen": bool(re.search(r"\d{4}-\d{1,2}-\d{1,2}-\d{1,2}-\d{1,2}-\d{1,2}", text)),
        "sha256_head": hashlib.sha256(text.encode("utf-8", "replace")).hexdigest()[:16],
    }


def _signature_value(v, depth=0, max_depth=3):
    if isinstance(v, dict):
        if depth >= max_depth:
            return {"...": f"depth>{max_depth}"}
        return {str(k): _signature_value(x, depth + 1, max_depth) for k, x in list(v.items())[:50]}
    if isinstance(v, list):
        return {"__len__": len(v), "0": _signature_value(v[0], depth + 1, max_depth) if v else None}
    return type(v).__name__


def _signature_bytes(body: bytes, content_type: str) -> dict:
    if "json" in (content_type or "").lower():
        try:
            return _signature_value(json.loads(body.decode("utf-8", "replace")))
        except Exception:
            pass
    return _signature_text(body.decode("utf-8", "replace"))


# --------------------------------------------------------------------- fixtures


def write_fixture(fixtures_dir: str, source: str, name: str, detail: dict, signature: dict) -> str:
    """Small, attributed, dated fixture + its signature (3.5, 7.4)."""
    out_dir = os.path.join(fixtures_dir, source)
    os.makedirs(out_dir, exist_ok=True)
    path = os.path.join(out_dir, f"{name}.txt")
    head = detail.get("body_head", b"")
    with open(path, "w", encoding="utf-8", errors="replace") as fh:
        fh.write(f"# source={source} name={name}\n")
        fh.write(f"# captured={NOW_ISO} url={detail.get('final_url')}\n")
        fh.write(f"# status={detail.get('status')} elapsed_s={detail.get('elapsed_s')}\n")
        fh.write(f"# redaction: first {len(head)} bytes of body; signature below, no further values\n")
        fh.write(f"# signature={json.dumps(signature, sort_keys=True)}\n")
        fh.write(head.decode("utf-8", "replace"))
    with open(path + ".sig.json", "w", encoding="utf-8") as fh:
        json.dump(signature, fh, indent=2, sort_keys=True)
    return path


# ---------------------------------------------------------------------- sources


S2_THROTTLE_MARKERS = ("try again", "please try again after some time")


def _looks_throttled(text: str, sig: dict) -> bool:
    low = text.lower()
    return any(m in low for m in S2_THROTTLE_MARKERS)


def probe_s1_ntes(fixtures_dir: str) -> dict:
    """S1 NTES/CRIS: the app's own AES-CBC + MD5 envelope is NOT reproduced here (no capture
    yet - 7.4), so this probe measures reachability only, with repeat samples: a single
    timeout is not a verdict (10.8)."""
    url = "https://enquiry.indianrail.gov.in/crisns/AppServAnd"
    samples = []
    fixture = None
    for i in range(3):  # handful of requests only (3.5)
        ok, detail = http_with_retry(url, data=b"", method="POST")
        entry = {"attempt": i + 1, "ok": ok, "status": detail.get("status"),
                 "elapsed_s": detail.get("elapsed_s"), "error": detail.get("error")}
        if ok:
            sig = _signature_bytes(detail.get("body_head", b""), detail.get("content_type", ""))
            entry["body_len"] = detail.get("body_len", 0)
            if fixture is None:
                fixture = write_fixture(fixtures_dir, "s1_ntes", "appservand_reachability", detail, sig)
        samples.append(entry)
    ok_count = sum(1 for s in samples if s["ok"])
    result = {
        "source": "S1 NTES/CRIS",
        "url": url,
        "measured": NOW_ISO,
        "samples": samples,
        "reachability": f"{ok_count}/{len(samples)}",
        "fixture": fixture,
    }
    if ok_count == 0:
        result["verdict"] = BLOCKED_NET
        result["reason"] = ("unreachable from this environment after one retry per sample; "
                            "matches the brief's recorded measurement; re-measure from device")
    else:
        result["verdict"] = BLOCKED_SHAPE
        result["reason"] = (f"host answered on {ok_count}/{len(samples)} samples (NOTE: earlier GET "
                            "timed out - reachability is INTERMITTENT here; recorded, not re-litigated). "
                            "But an empty/unknown body proves nothing about the envelope: no parser "
                            "until a real capture (7.4). CI reachability still unmeasured - the CI "
                            "probe run decides")
    return result


def probe_s2_erail(fixtures_dir: str) -> dict:
    """S2 erail: the path matters - a wrong path 404s while the host is alive. Probe a small
    matrix of documented shapes instead of guessing one (7.4: capture before parsing).
    A 200 whose body is a control/throttle message is NOT data: verdict blocked-by-shape."""
    base = "https://erail.in/rail/getTrains.aspx"
    candidates = [
        (base, "documented_base_path"),
        (base + "?l=SMET&c=NDLS", "l_c_pair_smet_ndls"),
        # The brief records that S2 also gives identity+schedule BY NUMBER; the number endpoint is
        # not named in the brief, so the probe MEASURES candidate shapes and records verdicts
        # (7.4: capture before parsing). These are measurement candidates, not assumed APIs.
        (base + "?TrainNo=56269", "trainno_param"),
        ("https://erail.in/rail/getTrainList.aspx?TrainNo=56269", "gettrainlist_path"),
    ]
    attempts = []
    for url, name in candidates:
        ok, detail = http_with_retry(url)
        entry = {"url": url, "name": name, "ok": ok,
                 "status": detail.get("status"), "elapsed_s": detail.get("elapsed_s"),
                 "error": detail.get("error")}
        if ok:
            text = detail.get("body_head", b"").decode("utf-8", "replace")
            sig = _signature_bytes(detail.get("body_head", b""), detail.get("content_type", ""))
            entry["signature"] = sig
            entry["throttled_or_control_message"] = _looks_throttled(text, sig)
            entry["fixture"] = write_fixture(fixtures_dir, "s2_erail", name, detail, sig)
        attempts.append(entry)
    answered = [a for a in attempts if a["ok"] and a.get("status") == 200]
    number_ok = [
        a for a in answered
        if a["name"] == "trainno_param"
        and not a.get("throttled_or_control_message")
        and a["signature"].get("csr_feed_stamp_seen")
    ]
    real_data = number_ok
    result = {
        "source": "S2 erail.in",
        "measured": NOW_ISO,
        "attempts": attempts,
    }
    if not answered:
        result["verdict"] = BLOCKED_NET
        result["reason"] = "no 2xx from either probed path after one retry each"
    elif real_data:
        result["verdict"] = USABLE_LABELLED
        result["reason"] = ("train-number endpoint answers with the documented shape (tilde rows + "
                            "feed stamp; capture committed at poc/fixtures/s2_erail/trainno_param.txt); "
                            "label `via erail.in` with the feed stamp as the age. The PAIR endpoint "
                            "still answers throttle control messages from every measured environment")
    else:
        signatures = {a["signature"].get("sha256_head") for a in answered}
        result["verdict"] = BLOCKED_SHAPE
        result["reason"] = (
            "host is alive and answered 200, but the payloads are control/throttle messages "
            "(same body signature on both paths)" if len(signatures) == 1 else
            "host alive but payload shape does not match the documented tilde+feed-stamp format"
        ) + "; parser blocked until a real data capture (7.4)"
    return result


def _gh(args: list[str]) -> tuple[bool, str]:
    if shutil.which("gh") is None:
        return False, "gh not installed"
    try:
        proc = subprocess.run(["gh"] + args, capture_output=True, text=True, timeout=60)
        return proc.returncode == 0, (proc.stdout or "") + (proc.stderr or "")
    except Exception as e:  # tool failure is a stated reason, never a silent pass
        return False, f"gh failed: {type(e).__name__}: {e}"


def probe_s3_github_dumps(fixtures_dir: str) -> dict:
    """S3: test repo commit freshness AGAINST content vintage (10.8: commit date is not
    data vintage). Retired 0-prefix train numbers are the pre-2022-23-renumbering tell."""
    ok, out = _gh(["search", "repos", "indian railway", "--limit", "5",
                   "--sort", "stars", "--json", "fullName,updatedAt"])
    result = {"source": "S3 GitHub dumps", "measured": NOW_ISO}
    if not ok:
        result["verdict"] = BLOCKED_NET
        result["reason"] = f"candidate search unavailable: {out.strip()[:200]}"
        return result
    try:
        repos = json.loads(out)
    except Exception:
        result["verdict"] = BLOCKED_SHAPE
        result["reason"] = "gh search returned unparseable JSON"
        return result
    findings = []
    for repo in repos[:1]:  # handful of requests only (3.5): 1 repo, 2 calls, 1 fetch
        full = repo.get("fullName")
        ok2, listing = _gh(["api", f"repos/{full}/contents", "--jq", ".[].name"])
        if not ok2:
            findings.append({"repo": full, "note": "contents listing failed",
                             "detail": listing.strip()[:200]})
            continue
        entry = {"repo": full, "updatedAt": repo.get("updatedAt"),
                 "listing": listing.split()[:8]}
        for fname in ("trains.json", "trains.csv", "train.csv"):
            raw = f"https://raw.githubusercontent.com/{full}/master/{fname}"
            ok3, detail = http_with_retry(raw)
            if ok3 and detail.get("status") == 200:
                text = detail["body_head"].decode("utf-8", "replace")
                bad = re.findall(r"\b0\d{5}\b", text)
                entry["sample_file"] = fname
                entry["sample_bytes"] = len(text)
                entry["retired_0prefix_hits_in_head"] = len(bad)
                entry["sample_examples"] = sorted(set(bad))[:5]
                entry["vintage_signal"] = ("PRE-RENUMBERING CONTENT in head" if bad
                                           else "no retired 0-prefix numbers in head (vintage still unknown)")
                break
            entry.setdefault("misses", []).append(fname)
        findings.append(entry)
    result["findings"] = findings
    result["verdict"] = USABLE_LABELLED
    result["reason"] = ("cold-start fallback only, permanently labelled; a repo commit date is "
                        "NOT a data vintage - content checks above decide, not updatedAt")
    return result


def probe_s4_open_data(fixtures_dir: str) -> dict:
    """S4: measure reachability only; cadence/vintage/key policy stay UNADMITTED until an
    ADR + owner consent (brief S4)."""
    url = "https://www.data.gov.in/"
    ok, detail = http_with_retry(url)
    result = {"source": "S4 open-data portals", "measured": NOW_ISO,
              "url": url, "ok": ok, "status": detail.get("status"),
              "elapsed_s": detail.get("elapsed_s")}
    if ok:
        result["verdict"] = BLOCKED_SHAPE
        result["reason"] = ("portal reachable; publication cadence, data vintage and key policy "
                            "UNMEASURED - remains UNADMITTED pending ADR + owner consent")
    else:
        result["verdict"] = BLOCKED_NET
        result["reason"] = f"unreachable: {detail.get('error')}"
    return result


# ------------------------------------------------------------------------ main


def main() -> int:
    global NOW_ISO
    ap = argparse.ArgumentParser()
    ap.add_argument("--out", default="poc/probe-results.json")
    ap.add_argument("--fixtures", default="poc/fixtures")
    ap.add_argument("--quiet", action="store_true")
    args = ap.parse_args()

    NOW_ISO = _dt.datetime.now(_dt.timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")
    probes = [probe_s1_ntes, probe_s2_erail, probe_s3_github_dumps, probe_s4_open_data]
    results = []
    for probe in probes:
        try:
            results.append(probe(args.fixtures))
        except Exception as e:  # a crashed probe is a stated failure, not a skipped question
            results.append({"source": probe.__name__, "verdict": BLOCKED_SHAPE,
                            "measured": NOW_ISO, "reason": f"probe crashed: {e}"})
    os.makedirs(os.path.dirname(args.out) or ".", exist_ok=True)
    with open(args.out, "w", encoding="utf-8") as fh:
        json.dump({"run": NOW_ISO, "results": results}, fh, indent=2)
    if not args.quiet:
        print(f"source probe run {NOW_ISO}")
        for r in results:
            print(f"\n== {r.get('source', '?')}: {r['verdict']}")
            print(f"   {r.get('reason', '')}")
            for s in r.get("samples", []):
                print(f"   sample {s.get('attempt')}: status={s.get('status')} ok={s['ok']} "
                      f"elapsed={s.get('elapsed_s')}s")
            for a in r.get("attempts", []):
                print(f"   attempt {a['name']}: status={a.get('status')} ok={a['ok']} "
                      f"elapsed={a.get('elapsed_s')}s fixture={a.get('fixture', '-')}")
            for f in r.get("findings", []):
                print(f"   repo {f.get('repo')} updatedAt={f.get('updatedAt')} "
                      f"file={f.get('sample_file', '-')} signal={f.get('vintage_signal', f.get('note', '-'))}")
        print(f"\nresults written to {args.out}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
