#!/usr/bin/env python3
"""Deep probe for ConfirmTkt and RailYatri focusing on live running status by train number.
Try more realistic paths + headers; capture structural sigs."""
from __future__ import annotations
import argparse, datetime as _dt, hashlib, json, os, re, sys, time, urllib.error, urllib.parse as uparse, urllib.request as ureq, socket, ssl

MAX_FIXTURE_BYTES = 4096
NOW_ISO = _dt.datetime.now(_dt.timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")
UAS = [
  "Dalvik/2.1.0 (Linux; Android 11)",
  "Mozilla/5.0 (Linux; Android 13; Mobile Safari/537.36",
  "okhttp/4.9.3",
]
def retry(url, **kw):
    try:
        req=ureq.Request(url,**{k:v for k,v in kw.items() if k not in ('data','method')})
        if 'method' in kw: 
            pass
    except Exception: pass
    # simpler
    pass

# quick inline
pass
