#!/usr/bin/env python3
"""CDN-SCANNER v2 — async, Termux-optimized, Psiphon/ShirOKhorshid ready"""

import asyncio, ipaddress, ssl, sys, os, time, random, math, io
import socket, threading, subprocess, re, json, signal, urllib.request
from concurrent.futures import ThreadPoolExecutor, as_completed
from datetime import datetime
from http.server import BaseHTTPRequestHandler, HTTPServer
from urllib.parse import urlparse

_STOP_SCAN = threading.Event()

# ── Colors ───────────────────────────────────────────────────────────────────
G = "\033[38;5;82m"; R = "\033[38;5;203m"; Y = "\033[38;5;220m"
O = "\033[38;5;208m"; C = "\033[38;5;117m"; B = "\033[1m"
D = "\033[38;5;244m"; W = "\033[97m";       X = "\033[0m"

def _ip_color(code) -> str:
    try:
        c = int(str(code)[:1])
        return G if c == 2 else Y if c == 3 else R if c == 4 else D
    except (ValueError, TypeError):
        return D

def _clr(): os.system("clear" if os.name != "nt" else "cls")

# ── Iran domain blocking (static) — IP check is live-only via ipnumberia ──────
_IRAN_DOMAINS = [".ir","persiangig","iranserver","pars","ictco","arvancloud"]

_REGION_CACHE:     dict = {}; _REGION_CACHE_LOCK = threading.Lock()


def _https_get(host: str, path: str, timeout: float = 5.0,
               verify: bool = True) -> bytes | None:
    """Minimal HTTPS GET with HTTP/1.1; handles chunked responses."""
    try:
        ctx = ssl.create_default_context()
        ctx.check_hostname = verify
        ctx.verify_mode = ssl.CERT_REQUIRED if verify else ssl.CERT_NONE
        with socket.create_connection((host, 443), timeout=timeout) as s:
            with ctx.wrap_socket(s, server_hostname=host) as ss:
                req = (f"GET {path} HTTP/1.1\r\nHost: {host}\r\n"
                       "User-Agent: Mozilla/5.0\r\nAccept: application/json\r\n"
                       "Connection: close\r\n\r\n")
                ss.sendall(req.encode())
                data = b""
                while True:
                    chunk = ss.recv(8192)
                    if not chunk:
                        break
                    data += chunk
                    if len(data) > 65536:
                        break
        if b"\r\n\r\n" not in data:
            return data
        header, body = data.split(b"\r\n\r\n", 1)
        # decode chunked transfer encoding if needed
        if b"transfer-encoding: chunked" in header.lower():
            decoded = b""
            while body:
                try:
                    crlf = body.index(b"\r\n")
                    size = int(body[:crlf], 16)
                    if size == 0:
                        break
                    decoded += body[crlf+2:crlf+2+size]
                    body = body[crlf+2+size+2:]
                except Exception:
                    decoded += body
                    break
            return decoded
        return body
    except Exception:
        return None


def _parse_cc_from_json(body: bytes) -> str:
    """Extract 2-letter country code from geo API JSON body."""
    text = body.decode(errors="ignore") if isinstance(body, bytes) else body
    try:
        obj = json.loads(text)
        # try common field names across services
        for key in ("country_code", "countryCode", "country", "cc"):
            val = obj.get(key, "")
            if isinstance(val, str) and len(val) == 2 and val.isalpha():
                return val.upper()
        # ipwho.is nests it
        if "connection" in obj:
            for key in ("country_code",):
                val = obj.get(key, "")
                if isinstance(val, str) and len(val) == 2:
                    return val.upper()
    except Exception:
        pass
    # regex fallback
    m = re.search(r'"(?:country_code|countryCode|country|cc)"\s*:\s*"([A-Za-z]{2})"', text)
    if m:
        return m.group(1).upper()
    if "iran" in text.lower() or '"IR"' in text or '"ir"' in text:
        return "IR"
    return ""


def _rdap_cc(ip: str, timeout: float = 4.0) -> str:
    """Get country code via RDAP (RIPE/APNIC/ARIN). Filter-proof."""
    first = int(ip.split(".")[0])
    ripe  = {5,31,37,46,77,78,80,82,85,89,91,94,95,176,178,185,188,193,194,195,212,217}
    apnic = {1,14,27,36,39,42,43,49,58,59,60,61,101,103,106,110,111,112,113,114,
             115,116,117,118,119,120,121,122,123,124,125,126,150,153,163,171,175,
             180,182,183,202,203,210,211,218,219,220,221,222,223}
    if first in ripe:
        candidates = [("rdap.db.ripe.net", f"/ip/{ip}"), ("rdap.arin.net", f"/registry/ip/{ip}")]
    elif first in apnic:
        candidates = [("rdap.apnic.net", f"/ip/{ip}"), ("rdap.arin.net", f"/registry/ip/{ip}")]
    else:
        candidates = [("rdap.arin.net", f"/registry/ip/{ip}"), ("rdap.db.ripe.net", f"/ip/{ip}")]
    for host, path in candidates:
        body = _https_get(host, path, timeout, verify=True)
        if body:
            m = re.search(r'"country"\s*:\s*"([A-Za-z]{2})"', body.decode(errors="ignore"))
            if m:
                return m.group(1).upper()
    return ""


def _get_country_code(ip: str, timeout: float = 4.0) -> str:
    """Get 2-letter ISO country code with fallback chain."""
    with _REGION_CACHE_LOCK:
        if ip in _REGION_CACHE:
            return _REGION_CACHE[ip]
    cc = ""
    # Try multiple services in order
    services = [
        ("ipwho.is",      f"/{ip}",               False),
        ("ipapi.co",      f"/{ip}/json/",          True),
        ("freeipapi.com", f"/api/json/{ip}",       False),
    ]
    for host, path, verify in services:
        try:
            body = _https_get(host, path, timeout, verify=verify)
            if body:
                cc = _parse_cc_from_json(body)
                if cc:
                    break
        except Exception:
            pass
    # Last resort: RDAP
    if not cc:
        cc = _rdap_cc(ip, timeout)
    with _REGION_CACHE_LOCK:
        _REGION_CACHE[ip] = cc
    return cc


def get_country_codes_batch(ips: list, timeout: float = 3.0) -> dict:
    """Fetch country codes for a batch of IPs in parallel."""
    results = {}
    with _REGION_CACHE_LOCK:
        cached_ips = set(_REGION_CACHE.keys())
    to_fetch = [ip for ip in ips if ip not in cached_ips]
    lock = threading.Lock()

    def _worker(ip):
        cc = _get_country_code(ip, timeout)
        with lock:
            results[ip] = cc

    if to_fetch:
        with ThreadPoolExecutor(max_workers=min(len(to_fetch), 30)) as pool:
            list(as_completed([pool.submit(_worker, ip) for ip in to_fetch]))
    for ip in ips:
        results.setdefault(ip, _REGION_CACHE.get(ip, ""))
    return results


def _has_iran_domain(host: str) -> bool:
    if not host:
        return False
    h = host.lower()
    return any(d in h for d in _IRAN_DOMAINS)


# ── Pre-scan Iran range filter ─────────────────────────────────────────────────


def _ip_count_color(count: int) -> str:
    return G if count <= 100 else O if count <= 500 else R

# ── Animation ticker ───────────────────────────────────────────────────────────
_ANIM_STATS: dict = {"pct": 0, "found": 0, "running": False}
_ANIM_LOCK = threading.Lock()

def _start_anim_ticker(label: str = "") -> threading.Thread:
    _ANIM_STATS.update({"pct": 0, "found": 0, "running": True})
    _SPIN_CHARS = ["⠋","⠙","⠹","⠸","⠼","⠴","⠦","⠧","⠇","⠏"]
    spin_state = [0]

    def _loop():
        while _ANIM_STATS["running"]:
            with _ANIM_LOCK:
                pct = _ANIM_STATS["pct"]; found = _ANIM_STATS["found"]
            spin = _SPIN_CHARS[spin_state[0] % len(_SPIN_CHARS)]; spin_state[0] += 1
            found_str = f"  {G}✓ {found}{X}" if found else ""
            bw = 20; fi = max(0, min(bw, round(pct * bw / 100)))
            bar = f"{G}{'█'*fi}{D}{'░'*(bw-fi)}{X}"
            print(f"  {C}{spin}{X} [{bar}] {Y}{pct:>3}%{X}{found_str}"
                  + (f"  {D}{label}{X}" if label else ""), end="\r", flush=True)
            time.sleep(0.25)

    t = threading.Thread(target=_loop, daemon=True, name="anim_ticker")
    t.start(); return t

def _stop_anim_ticker(t: threading.Thread):
    _ANIM_STATS["running"] = False
    if t and t.is_alive(): t.join(timeout=0.3)
    print(f"\r{' '*72}\r", end="", flush=True)

def _show_scan_preview(ip_count: int, concurrency: int, timeout: float, source: str = "", cidrs: list = None):
    if ip_count == 0: return
    ips_per_sec = max(1, concurrency / max(timeout * 0.6, 0.5))
    est_secs = ip_count / ips_per_sec
    est_str = f"~{est_secs:.0f}s" if est_secs < 60 else f"~{est_secs/60:.1f}min"
    est_col = G if est_secs < 60 else Y if est_secs < 300 else R
    ip_col = _ip_count_color(ip_count)
    src_s = f"  {D}{source}{X}" if source else ""
    print(f"\n  {ip_col}{B}{ip_count:,} IPs{X}{src_s}  {D}·{X}  {est_col}{est_str}{X}  {D}· conc={concurrency}{X}")


try:
    import aiohttp; HAS_AIOHTTP = True
except ImportError:
    HAS_AIOHTTP = False

try:
    import requests
    from requests.packages.urllib3.exceptions import InsecureRequestWarning
    requests.packages.urllib3.disable_warnings(InsecureRequestWarning)
    HAS_REQ = True
except ImportError:
    HAS_REQ = False


# ── Range loaders ─────────────────────────────────────────────────────────────
def _load_json_ranges(filename: str) -> list:
    for fpath in [os.path.join(os.path.dirname(os.path.abspath(__file__)), filename),
                  os.path.join(os.getcwd(), filename)]:
        if os.path.isfile(fpath):
            try:
                with open(fpath) as f:
                    data = json.load(f)
                if isinstance(data, list):
                    valid = []
                    for item in data:
                        try: ipaddress.ip_network(item, strict=False); valid.append(item)
                        except ValueError: pass
                    if valid: return valid
            except Exception:
                pass
    return []

def _load_text_ranges_from_path(fpath: str) -> list:
    if not os.path.isfile(fpath): return []
    ranges, seen = [], set()
    try:
        with open(fpath, encoding="utf-8", errors="ignore") as f:
            for line in f:
                line = line.strip()
                if not line or line.startswith("#"): continue
                line = line.split('#')[0].strip()
                for tok in re.split(r'[\s,;]+', line):
                    tok = re.sub(r'^https?://', '', tok.strip().strip('"\''))
                    tok = tok.split(':')[0].strip()
                    if not tok: continue
                    try:
                        net = ipaddress.ip_network(tok, strict=False)
                        entry = str(net)
                        if entry not in seen: seen.add(entry); ranges.append(entry)
                        break
                    except ValueError:
                        m = re.search(r'(\d{1,3}\.\d{1,3}\.\d{1,3}\.\d{1,3}(?:/\d{1,2})?)', tok)
                        if m:
                            try:
                                net = ipaddress.ip_network(m.group(1), strict=False)
                                entry = str(net)
                                if entry not in seen: seen.add(entry); ranges.append(entry)
                                break
                            except ValueError: pass
    except Exception: pass
    return ranges

def _load_text_ranges(filename: str) -> list:
    for fpath in [os.path.join(os.path.dirname(os.path.abspath(__file__)), filename),
                  os.path.join(os.getcwd(), filename)]:
        r = _load_text_ranges_from_path(fpath)
        if r: return r
    return []


# ── Load CDN ranges ────────────────────────────────────────────────────────────
_FASTLY_RANGES = _load_json_ranges("fastly-AS54113.json")
_AKAMAI_JSON   = _load_json_ranges("akamai-AS20940__1_.json")
_HOSTS_RANGES  = _load_text_ranges("hosts.txt")
_AKAMAI_FULL   = list(dict.fromkeys(_AKAMAI_JSON + _HOSTS_RANGES))

_AKAMAI_EXTRA = [
    "92.122.0.0/15","92.123.0.0/16","88.221.64.0/18","88.221.128.0/18","88.221.192.0/18",
    "95.100.0.0/15","95.101.0.0/16","213.42.0.0/17","213.42.128.0/18","62.149.0.0/17",
    "212.118.0.0/16","212.119.0.0/17","195.229.0.0/17","195.229.128.0/18","217.33.0.0/18",
    "109.200.192.0/18","217.25.128.0/18","62.200.0.0/17","62.200.128.0/18",
    "82.178.0.0/16","82.179.0.0/17",
]
_FASTLY_EXTRA = [
    "151.101.0.0/22","151.101.64.0/22","151.101.128.0/22","151.101.192.0/22",
    "146.75.64.0/20","146.75.128.0/20","146.75.0.0/20","146.75.16.0/20","146.75.32.0/20",
    "146.75.192.0/20","199.232.64.0/22","199.232.128.0/22",
    "23.235.32.0/22","43.249.72.0/22","103.244.50.0/24","103.245.222.0/23",
    "104.156.80.0/20","167.82.64.0/18",
]
_CLOUDFLARE_RANGES = [
    "173.245.48.0/20","103.21.244.0/22","103.22.200.0/22","103.31.4.0/22",
    "141.101.64.0/18","108.162.192.0/18","190.93.240.0/20","188.114.96.0/20",
    "197.234.240.0/22","198.41.128.0/17","162.158.0.0/15","104.16.0.0/13",
    "104.24.0.0/14","172.64.0.0/13","131.0.72.0/22",
]

_GOOGLE_RANGES = [
    # Google/GFE edge ranges (ASN 15169)
    "8.8.4.0/24","8.8.8.0/24","8.34.208.0/20","8.35.192.0/20",
    "23.236.48.0/20","23.251.128.0/19","34.0.0.0/15","34.2.0.0/16",
    "34.64.0.0/10","34.128.0.0/10","35.184.0.0/13","35.192.0.0/12",
    "35.208.0.0/12","35.224.0.0/12","35.240.0.0/13",
    "64.233.160.0/19","66.102.0.0/20","66.249.64.0/19",
    "72.14.192.0/18","74.125.0.0/16","108.177.8.0/21",
    "172.217.0.0/16","172.253.0.0/16","173.194.0.0/16",
    "209.85.128.0/17","216.58.192.0/19","216.239.32.0/19",
]

_AMAZON_CF_RANGES = [
    "13.32.0.0/15","13.35.0.0/16","13.224.0.0/14","13.249.0.0/16",
    "54.182.0.0/16","54.192.0.0/16","54.230.0.0/16","54.239.128.0/18",
    "54.240.128.0/18","52.46.0.0/18","52.84.0.0/15","52.222.128.0/17",
    "64.252.64.0/18","64.252.128.0/18","70.132.0.0/18","71.152.0.0/17",
    "99.86.0.0/16","130.176.0.0/17","143.204.0.0/16","204.246.164.0/22",
    "204.246.168.0/22","204.246.174.0/23","204.246.176.0/20",
    "205.251.192.0/19","205.251.249.0/24","205.251.250.0/23",
    "205.251.252.0/23","205.251.254.0/24","216.137.32.0/19",
]

_GCORE_RANGES = [
    "92.223.64.0/18","92.223.0.0/19","5.188.24.0/22","5.188.106.0/23",
    "5.188.108.0/22","185.22.152.0/22","185.254.196.0/22","185.254.200.0/22",
    "199.34.28.0/22","193.178.88.0/21","212.92.128.0/17","45.9.152.0/22",
    "46.8.48.0/20","46.8.80.0/20","46.8.64.0/20","77.83.240.0/22",
    "87.246.0.0/20","89.185.224.0/20","91.213.48.0/22","91.213.56.0/22",
]

CDN_RANGES = {
    "Akamai":      list(dict.fromkeys((_AKAMAI_FULL or ["92.122.0.0/17","92.123.0.0/17","95.100.0.0/17","95.101.0.0/17","92.122.128.0/18"]) + _AKAMAI_EXTRA)),
    "Fastly":      list(dict.fromkeys((_FASTLY_RANGES or ["151.101.128.0/22","151.101.192.0/22","151.101.64.0/22","151.101.0.0/22","151.101.32.0/22"]) + _FASTLY_EXTRA)),
    "Cloudflare":  _CLOUDFLARE_RANGES,
    "Google":      _GOOGLE_RANGES,
    "CloudFront":  _AMAZON_CF_RANGES,
    "Gcore":       _GCORE_RANGES,
}

USER_LOADED_RANGES: list = []

# ── Range auto-updater ─────────────────────────────────────────────────────────
_RANGE_SOURCES = {
    "Cloudflare": {"url": "https://www.cloudflare.com/ips-v4", "format": "lines", "file": "cloudflare-ips.txt"},
    "Fastly":     {"url": "https://api.fastly.com/public-ip-list", "format": "fastly_json", "file": "fastly-AS54113.json"},
    "Akamai":     {"url": "https://raw.githubusercontent.com/lord-alfred/ipranges/main/akamai/ipv4.txt",
                   "format": "lines", "file": "akamai-AS20940__1_.json",
                   "fallback_url": "https://raw.githubusercontent.com/nickspaargaren/no-google/master/categories/akamai.parsed"},
}
_UPDATE_META_FILE = os.path.join(os.path.dirname(os.path.abspath(__file__)), ".cdnscan_range_meta.json")

def _load_update_meta() -> dict:
    try:
        with open(_UPDATE_META_FILE) as f: return json.load(f)
    except Exception: return {}

def _save_update_meta(meta: dict):
    try:
        with open(_UPDATE_META_FILE, "w") as f: json.dump(meta, f, indent=2)
    except Exception: pass

def _range_update_age_days(cdn: str) -> float | None:
    ts = _load_update_meta().get(cdn, {}).get("updated")
    if not ts: return None
    try: return max((datetime.now() - datetime.fromisoformat(ts)).total_seconds() / 86400, 0)
    except Exception: return None

def _fetch_url(url: str, timeout: float = 12.0) -> bytes | None:
    try:
        req = urllib.request.Request(url, headers={"User-Agent": "curl/7.88", "Accept": "*/*"})
        ctx = ssl.create_default_context(); ctx.check_hostname = False; ctx.verify_mode = ssl.CERT_NONE
        with urllib.request.urlopen(req, context=ctx, timeout=timeout) as resp:
            return resp.read()
    except Exception: return None

def _parse_ranges_from_bytes(data: bytes, fmt: str) -> list:
    ranges = []; text = data.decode("utf-8", errors="ignore")
    if fmt == "fastly_json":
        try:
            obj = json.loads(text)
            for item in obj.get("addresses", obj.get("ipv4Addresses", [])):
                try: ipaddress.ip_network(item.strip(), strict=False); ranges.append(item.strip())
                except ValueError: pass
        except Exception: pass
    else:
        try:
            obj = json.loads(text)
            if isinstance(obj, list):
                for item in obj:
                    try: ipaddress.ip_network(str(item).strip(), strict=False); ranges.append(str(item).strip())
                    except ValueError: pass
                return list(dict.fromkeys(ranges))
        except Exception: pass
        for line in text.splitlines():
            line = line.strip().split("#")[0].strip()
            if not line: continue
            try: ipaddress.ip_network(line, strict=False); ranges.append(line)
            except ValueError:
                m = re.search(r"(\d{1,3}\.\d{1,3}\.\d{1,3}\.\d{1,3}(?:/\d{1,2})?)", line)
                if m:
                    try: ipaddress.ip_network(m.group(1), strict=False); ranges.append(m.group(1))
                    except ValueError: pass
    return list(dict.fromkeys(ranges))

def update_cdn_ranges(cdn_list: list | None = None, verbose: bool = True) -> dict:
    if cdn_list is None: cdn_list = list(_RANGE_SOURCES.keys())
    results = {}; meta = _load_update_meta()
    for cdn in cdn_list:
        src = _RANGE_SOURCES.get(cdn)
        if not src: results[cdn] = {"ok": False, "count": 0, "msg": "Unknown CDN"}; continue
        if verbose: print(f"  {C}↓{X}  {W}{cdn}{X}  {D}{src['url'][:55]}...{X}", end="", flush=True)
        data = _fetch_url(src["url"])
        if data is None and src.get("fallback_url"):
            data = _fetch_url(src["fallback_url"])
        if data is None:
            if verbose: print(f"  {R}FAILED{X}")
            results[cdn] = {"ok": False, "count": 0, "msg": "Network error"}; continue
        ranges = _parse_ranges_from_bytes(data, src["format"])
        if not ranges:
            if verbose: print(f"  {R}no valid ranges{X}")
            results[cdn] = {"ok": False, "count": 0, "msg": "Parse error"}; continue
        script_dir = os.path.dirname(os.path.abspath(__file__))
        fpath = os.path.join(script_dir, src["file"])
        saved = False
        try:
            with open(fpath, "w") as f:
                if src["file"].endswith(".json"): json.dump(ranges, f, indent=2)
                else:
                    f.write(f"# Auto-updated {datetime.now().strftime('%Y-%m-%d %H:%M')}\n")
                    for r in ranges: f.write(r + "\n")
            saved = True
        except Exception: pass
        CDN_RANGES[cdn] = ranges
        meta[cdn] = {"updated": datetime.now().isoformat(), "count": len(ranges), "source": src["url"]}
        results[cdn] = {"ok": True, "count": len(ranges), "msg": "OK"}
        if verbose: print(f"  {G if saved else Y}{len(ranges)} ranges{X}")
    _save_update_meta(meta)
    return results

def show_update_ranges():
    _clr(); meta = _load_update_meta()
    _UW = _box_width(min_w=36, max_w=52)
    print(f"\n  {W}{B}{'Update CDN Ranges':^{_UW}}{X}")
    print(f"  {D}{'Downloads fresh IP ranges from official sources':^{_UW}}{X}\n")
    print(f"  {Y}{B}── Current Status ──{X}\n")
    cdn_names = list(_RANGE_SOURCES.keys())
    for i, cdn in enumerate(cdn_names, 1):
        age = _range_update_age_days(cdn)
        count = meta.get(cdn, {}).get("count", len(CDN_RANGES.get(cdn, [])))
        age_s = (f"{R}never updated{X}" if age is None else
                 f"{G}today{X}" if age < 1 else
                 f"{G}{age:.0f}d ago{X}" if age < 7 else
                 f"{Y}{age:.0f}d ago{X}" if age < 30 else
                 f"{R}{age:.0f}d ago  ← outdated{X}")
        print(f"  {C}{B}[{i}]{X}  {W}{B}{cdn:<12}{X}  {D}{count} ranges{X}  {age_s}")
        print(f"       {D}{_RANGE_SOURCES[cdn]['url'][:48]}{X}\n")
    print(f"  {C}{B}[A]{X}  {W}{B}Update ALL{X}\n  {D}blank = cancel{X}\n")
    try:
        sys.stdout.write("  > "); sys.stdout.flush()
        choice = input("").strip().lower()
    except (EOFError, KeyboardInterrupt): return
    if not choice: return
    to_update = cdn_names if choice == "a" else [cdn_names[int(t)-1] for t in choice.replace(",", " ").split() if t.isdigit() and 1 <= int(t) <= len(cdn_names)]
    if not to_update:
        print(f"  {R}Invalid choice.{X}")
        try: input(f"\n  {D}[ Enter to return ]{X}")
        except (EOFError, KeyboardInterrupt): pass
        return
    print(f"  {D}Fetching...{X}\n")
    results = update_cdn_ranges(to_update, verbose=True)
    ok_count = sum(1 for r in results.values() if r["ok"])
    print(f"\n  {G if ok_count else R}{'✓' if ok_count else '✗'}  {ok_count}/{len(results)} updated{X}")
    try: input(f"\n  {D}[ Enter to return ]{X}")
    except (EOFError, KeyboardInterrupt): pass


# ── Learned IPs ────────────────────────────────────────────────────────────────
LEARNED_FILE     = os.path.join(os.path.dirname(os.path.abspath(__file__)), ".cdnscan_learned.json")
LEARNED_TXT_FILE = os.path.join(os.path.dirname(os.path.abspath(__file__)), "cdnscan_learned.txt")
MAX_LEARNED_IPS  = 30
MAX_LEARNED_DAYS = 14

def _learned_entry_age_days(entry: dict) -> float:
    ts = entry.get("ts", "")
    if not ts: return 0.0
    for fmt in ("%Y-%m-%d %H:%M", "%m-%d %H:%M"):
        try:
            year = datetime.now().year
            dt = datetime.strptime((f"{year}-{ts}" if fmt == "%m-%d %H:%M" else ts), fmt)
            age = (datetime.now() - dt).total_seconds() / 86400
            if age < -0.1 and fmt == "%m-%d %H:%M":
                dt = datetime.strptime(f"{year-1}-{ts}", fmt)
                age = (datetime.now() - dt).total_seconds() / 86400
            return max(age, 0.0)
        except Exception: continue
    return 0.0

def _load_learned() -> dict:
    try:
        with open(LEARNED_FILE) as f:
            data = json.load(f)
        data.setdefault("ips", []); data.setdefault("entries", [])
        before = len(data["entries"])
        data["entries"] = [e for e in data["entries"] if _learned_entry_age_days(e) <= MAX_LEARNED_DAYS]
        if len(data["entries"]) < before:
            data["ips"] = [e["ip"] for e in data["entries"]]
            _save_learned(data)
        return data
    except Exception: return {"ips": [], "entries": []}

def _sync_learned_txt(data: dict):
    try:
        entries = data.get("entries", [])
        lines = ["# CDN Scanner — Learned IPs",
                 f"# Updated: {datetime.now().strftime('%Y-%m-%d %H:%M')}",
                 f"# Total: {len(entries)} IP(s)",
                 "#─────────────────────────────────────────"]
        for e in reversed(entries):
            lines.append(f"{e.get('ip',''):<18} {str(e.get('ms','')+'ms' if e.get('ms') else '—'):<8} "
                         f"{(e.get('cdn') or e.get('sni') or '—'):<12} {e.get('ts','')}")
        with open(LEARNED_TXT_FILE, "w") as f: f.write("\n".join(lines) + "\n")
    except Exception: pass

def _save_learned(data: dict):
    try:
        with open(LEARNED_FILE, "w") as f: json.dump(data, f, indent=2)
        _sync_learned_txt(data)
    except Exception: pass

def auto_save_healthy_ips(results: list) -> int:
    if not results: return 0
    data = _load_learned(); entries = list(data.get("entries", []))
    for r in results:
        ip = r["ip"] if isinstance(r, dict) else r
        entry = {"ip": ip,
                 "ms": r.get("ms", 9999) if isinstance(r, dict) else 9999,
                 "code": r.get("code", 0) if isinstance(r, dict) else 0,
                 "cdn": r.get("cdn_type", r.get("fronting_sni", "")) if isinstance(r, dict) else "",
                 "sni": r.get("fronting_sni", "") if isinstance(r, dict) else "",
                 "ts": datetime.now().strftime("%Y-%m-%d %H:%M"),
                 "fronting": r.get("fronting_ok", False) if isinstance(r, dict) else False}
        entries = [e for e in entries if e["ip"] != ip]
        entries.append(entry)
    entries = entries[-MAX_LEARNED_IPS:]
    data["entries"] = entries; data["ips"] = [e["ip"] for e in entries]
    _save_learned(data); return len(entries)

def get_learned_entries() -> list:
    return list(reversed(_load_learned().get("entries", [])))

def get_learned_ranges() -> list:
    data = _load_learned(); result = []
    for ip in data.get("ips", []):
        pseudo = f"{ip}/32"
        if pseudo not in result: result.append(pseudo)
    return result

def clear_learned(): _save_learned({"ips": [], "entries": []})

def ping_learned_ips_background():
    def _worker():
        data = _load_learned(); entries = data.get("entries", [])
        if not entries: return
        alive, dead = [], []
        for e in entries:
            ip = e.get("ip", "")
            if not ip: continue
            try:
                with socket.create_connection((ip, 443), timeout=2.5): alive.append(e)
            except Exception: dead.append(ip)
        if dead:
            data["entries"] = alive; data["ips"] = [e["ip"] for e in alive]
            _save_learned(data)
    threading.Thread(target=_worker, daemon=True, name="ping_learned").start()


# ── Keep-Alive ─────────────────────────────────────────────────────────────────
class KeepAlive:
    def __init__(self, host="snapp.ir", port=443, interval=4):
        self.host = host; self.port = port; self.interval = interval
        self._enabled = False; self._thread = None; self._stop = threading.Event()

    def _loop(self):
        while not self._stop.wait(self.interval):
            if self._enabled:
                try:
                    with socket.create_connection((self.host, self.port), timeout=3): pass
                except Exception: pass

    def _ensure_thread(self):
        if self._thread is None or not self._thread.is_alive():
            self._stop.clear()
            self._thread = threading.Thread(target=self._loop, daemon=True, name="keepalive")
            self._thread.start()

    def enable(self): self._enabled = True; self._ensure_thread()
    def disable(self): self._enabled = False
    def toggle(self) -> bool:
        if self._enabled: self.disable()
        else: self.enable()
        return self._enabled
    def stop(self):
        self._enabled = False; self._stop.set()
        if self._thread and self._thread.is_alive(): self._thread.join(timeout=1.0)

    @property
    def is_enabled(self) -> bool: return self._enabled
    def status_str(self) -> str:
        return (f"{G}{B}ON{X}  {D}(TCP → {self.host}:{self.port} / {self.interval}s){X}"
                if self._enabled else f"{R}OFF{X}")

keepalive = KeepAlive()

# ── Last scan memory ───────────────────────────────────────────────────────────
SCAN_HISTORY:    list = []
MAX_SCAN_HISTORY = 10

def _save_last_scan(source_label: str, ranges_used: list, healthy: list):
    global SCAN_HISTORY
    if healthy:
        SCAN_HISTORY.insert(0, {
            "timestamp": datetime.now().strftime("%Y-%m-%d %H:%M:%S"),
            "source": source_label, "ranges": list(ranges_used),
            "healthy": sorted(healthy, key=lambda r: r["ms"]),
        })
        if len(SCAN_HISTORY) > MAX_SCAN_HISTORY:
            SCAN_HISTORY = SCAN_HISTORY[:MAX_SCAN_HISTORY]

def _last_scan(): return SCAN_HISTORY[0] if SCAN_HISTORY else None

_partial_healthy: list = []
_partial_healthy_lock = threading.Lock()


# ── SSL / cert ─────────────────────────────────────────────────────────────────
def _extract_domains_from_der(der_bytes):
    domains = []
    try:
        result = subprocess.run(["openssl","x509","-inform","DER","-noout","-text"],
                                input=der_bytes, capture_output=True, timeout=5)
        text = result.stdout.decode(errors="ignore")
        for m in re.finditer(r"DNS:([^\s,]+)", text): domains.append(m.group(1).strip())
        if not domains:
            cn = re.search(r"CN\s*=\s*([^\n,/]+)", text)
            if cn: domains.append(cn.group(1).strip())
    except Exception: pass
    if domains: return list(dict.fromkeys(domains))
    try:
        from cryptography import x509 as _x509
        cert = _x509.load_der_x509_certificate(der_bytes)
        try:
            san = cert.extensions.get_extension_for_class(_x509.SubjectAlternativeName)
            domains.extend(san.value.get_values_for_type(_x509.DNSName))
        except Exception: pass
        if not domains:
            from cryptography.x509 import NameOID
            for attr in cert.subject.get_attributes_for_oid(NameOID.COMMON_NAME):
                domains.append(attr.value)
    except ImportError: pass
    if not domains:
        try:
            raw = der_bytes.decode("latin-1", errors="replace")
            found = re.findall(r"[a-z0-9][a-z0-9\-]{1,61}(?:\.[a-z]{2,}){1,}", raw)
            domains = [d for d in found if "." in d and len(d) > 5][:30]
        except Exception: pass
    return list(dict.fromkeys(domains))

_SSL_CTX = ssl.create_default_context()
_SSL_CTX.check_hostname = False; _SSL_CTX.verify_mode = ssl.CERT_NONE


# ── Async check ────────────────────────────────────────────────────────────────
async def check_async(session, ip, host, port, path, timeout, retries=2):
    headers = {"User-Agent": "curl/7.88", "Accept": "*/*", "Connection": "close"}
    if host: headers["Host"] = host
    timings = []; last_code = None
    for attempt in range(retries):
        t0 = time.monotonic()
        try:
            async with session.get(
                f"https://{ip}:{port}{path}", headers=headers,
                timeout=aiohttp.ClientTimeout(connect=timeout * 0.65, total=timeout),
                allow_redirects=False,
            ) as resp:
                ms = int((time.monotonic() - t0) * 1000)
                timings.append(ms); last_code = resp.status
                if resp.status < 500:
                    return {"ip": ip, "ok": True, "code": last_code, "ms": ms, "host": host}
        except asyncio.TimeoutError: timings.append(int(timeout * 1000)); break
        except (aiohttp.ClientConnectorError, asyncio.CancelledError): break
        except Exception: timings.append(int(timeout * 1000))
        if attempt < retries - 1: await asyncio.sleep(0.04)
    avg_ms = int(sum(timings) / len(timings)) if timings else 9999
    return {"ip": ip, "ok": False, "code": last_code, "ms": avg_ms, "host": host}


# ── Sync fallback ──────────────────────────────────────────────────────────────
def check_sync(ip, host, port, path, timeout, retries=2):
    timings = []; last_code = None
    for attempt in range(retries):
        t0 = time.monotonic()
        try:
            if HAS_REQ:
                h = {"User-Agent": "curl/7.88", "Connection": "close"}
                if host: h["Host"] = host
                r = requests.get(f"https://{ip}:{port}{path}", headers=h, timeout=timeout,
                                 verify=False, allow_redirects=False, stream=True)
                r.close(); ms = int((time.monotonic() - t0) * 1000)
                timings.append(ms); last_code = r.status_code
                if r.status_code < 500:
                    return {"ip": ip, "ok": True, "code": last_code, "ms": ms, "host": host}
            else:
                with socket.create_connection((ip, port), timeout=timeout) as s:
                    with _SSL_CTX.wrap_socket(s, server_hostname=host or ip) as ss:
                        req = (f"GET {path} HTTP/1.0\r\nHost: {host or ip}\r\n"
                               "User-Agent: curl/7.88\r\nConnection: close\r\n\r\n")
                        ss.sendall(req.encode())
                        resp = ss.recv(256).decode(errors="ignore")
                ms = int((time.monotonic() - t0) * 1000); timings.append(ms)
                parts = resp.split()
                if len(parts) >= 2 and resp.startswith("HTTP/"):
                    try:
                        code = int(parts[1]); last_code = code
                        if code < 500: return {"ip": ip, "ok": True, "code": code, "ms": ms, "host": host}
                    except ValueError: pass
        except Exception: timings.append(int(timeout * 1000))
        if attempt < retries - 1: time.sleep(0.04)
    avg_ms = int(sum(timings) / len(timings)) if timings else 9999
    return {"ip": ip, "ok": False, "code": last_code, "ms": avg_ms, "host": host}


# ── CDN Ranges — extra providers ──────────────────────────────────────────────
_AMAZON_CF_RANGES = [
    # Amazon CloudFront
    "13.32.0.0/15","13.35.0.0/16","52.84.0.0/15","52.222.128.0/17",
    "54.182.0.0/16","54.192.0.0/16","54.230.0.0/16","54.239.128.0/18",
    "64.252.64.0/18","64.252.128.0/18","70.132.0.0/18","99.84.0.0/16",
    "99.86.0.0/16","108.138.0.0/15","108.160.0.0/15","143.204.0.0/16",
    "204.246.164.0/22","204.246.168.0/22","204.246.174.0/23",
    "205.251.192.0/19","205.251.249.0/24","216.137.32.0/19",
]
_GCORE_RANGES = [
    "2.56.24.0/22","5.188.86.0/23","5.188.88.0/22","92.223.64.0/18",
    "93.123.16.0/21","95.85.0.0/17","146.19.183.0/24","185.79.76.0/22",
    "185.234.218.0/23","188.114.96.0/20",
]


def _detect_cdn_for_ip(ip: str) -> str | None:
    try:
        ip_obj = ipaddress.ip_address(ip)
        for cdn_name, cidrs in CDN_RANGES.items():
            for cidr in cidrs:
                try:
                    if ip_obj in ipaddress.ip_network(cidr, strict=False):
                        return cdn_name
                except ValueError:
                    pass
    except ValueError:
        pass
    return None


_FRONTING_PAIRS = {
    # SNI = what firewall sees | Host = what CDN routes to
    "Akamai": [
        ("a248.e.akamai.net",           "a.akamaihd.net"),
        ("a248.e.akamai.net",           "a.akamaiedge.net"),
        ("a.akamaihd.net",              "a248.e.akamai.net"),
        ("a248.e.akamai.net",           "psiphon3.com"),
        ("a.akamaihd.net",              "psiphon3.com"),
    ],
    "Fastly": [
        ("prod.global.ssl.fastly.net",  "global.ssl.fastly.net"),
        ("global.ssl.fastly.net",       "prod.global.ssl.fastly.net"),
        ("a.ssl.fastly.net",            "b.sni.global.fastly.net"),
        ("prod.global.ssl.fastly.net",  "psiphon3.com"),
        ("b.sni.global.fastly.net",     "prod.global.ssl.fastly.net"),
    ],
    # Cloudflare blocked Host-header fronting in 2019.
    # Only Workers-based or same-zone routing works.
    # These pairs test if the IP responds to any CF domain at all.
    "Cloudflare": [
        ("cloudflare.com",              "cloudflare.com"),
        ("www.cloudflare.com",          "www.cloudflare.com"),
        ("one.one.one.one",             "one.one.one.one"),
        ("cloudflare.com",              "ajax.cloudflare.com"),
    ],
    "Google": [
        ("www.google.com",              "www.google.com"),
        ("www.googleapis.com",          "www.googleapis.com"),
        ("accounts.google.com",         "accounts.google.com"),
        ("www.google.com",              "www.googleapis.com"),
    ],
    "CloudFront": [
        ("cloudfront.net",              "cloudfront.net"),
        ("d111111abcdef8.cloudfront.net","d111111abcdef8.cloudfront.net"),
        ("d111111abcdef8.cloudfront.net","psiphon3.com"),
    ],
    "Gcore": [
        ("gcore.com",                   "gcore.com"),
        ("api.gcore.com",               "api.gcore.com"),
    ],
    "Unknown": [
        ("a248.e.akamai.net",           "a.akamaihd.net"),
        ("prod.global.ssl.fastly.net",  "global.ssl.fastly.net"),
        ("www.google.com",              "www.googleapis.com"),
        ("cloudfront.net",              "cloudfront.net"),
    ],
}
_FRONTING_OK_CODES   = {200,204,206,301,302,303,307,308,400,403,404,405,406,426}
_FRONTING_FAIL_CODES = {421,502,503,504}

def _check_fronting_sync(ip: str, sni_host: str, timeout: float = 4.0, host_header: str = "") -> dict:
    if not host_header: host_header = sni_host
    result = {"ip": ip, "sni": sni_host, "host_header": host_header,
              "fronting_ok": False, "code": None, "ms": 9999, "reason": ""}
    try:
        ctx = ssl.create_default_context(); ctx.check_hostname = False; ctx.verify_mode = ssl.CERT_NONE
        t0 = time.monotonic()
        with socket.create_connection((ip, 443), timeout=timeout) as s:
            with ctx.wrap_socket(s, server_hostname=sni_host) as ss:
                # HTTP/1.1 required by many CDNs (HTTP/1.0 gets 426 from some)
                req = (f"HEAD / HTTP/1.1\r\nHost: {host_header}\r\n"
                       "User-Agent: Mozilla/5.0\r\nAccept: */*\r\nConnection: close\r\n\r\n")
                ss.sendall(req.encode())
                resp = b""
                while True:
                    chunk = ss.recv(512)
                    if not chunk: break
                    resp += chunk
                    if b"\r\n\r\n" in resp or len(resp) > 1024: break
        ms = int((time.monotonic() - t0) * 1000); result["ms"] = ms
        resp_s = resp.decode(errors="ignore")
        parts = resp_s.split()
        if len(parts) >= 2 and resp_s.startswith("HTTP/"):
            code = int(parts[1]) if parts[1].isdigit() else 0; result["code"] = code
            if code in _FRONTING_OK_CODES:
                result["fronting_ok"] = True; result["reason"] = f"OK {code}"
            elif code in _FRONTING_FAIL_CODES:
                result["reason"] = f"CDN rejected ({code})"
            else:
                result["fronting_ok"] = True; result["reason"] = f"code {code}"
        else:
            result["reason"] = "no HTTP response"
    except socket.timeout: result["reason"] = "timeout"
    except ConnectionRefusedError: result["reason"] = "refused"
    except ssl.SSLError as e: result["reason"] = f"SSL: {str(e)[:40]}"
    except Exception as e: result["reason"] = str(e)[:50]
    return result

def _check_fronting_all_pairs(ip: str, cdn: str, timeout: float, sni_override: str = "") -> dict:
    """Try all fronting pairs for the CDN, return first success or best attempt."""
    pairs = _FRONTING_PAIRS.get(cdn or "Unknown", _FRONTING_PAIRS["Unknown"])
    if sni_override:
        # put user override first
        override_pairs = [(sni_override, h) for _, h in pairs] + pairs
        pairs = list(dict.fromkeys(override_pairs))  # dedup
    best = None
    for sni, host_hdr in pairs:
        r = _check_fronting_sync(ip, sni, timeout, host_hdr)
        if r["fronting_ok"]:
            return r
        if best is None or (r["code"] is not None and best["code"] is None):
            best = r
    return best or {"ip": ip, "fronting_ok": False, "code": None, "ms": 9999,
                    "sni": pairs[0][0] if pairs else "", "reason": "all pairs failed"}

_FRONTING_CACHE: dict = {}; _FRONTING_CACHE_TTL = 600; _FRONTING_CACHE_LOCK = threading.Lock()

def _fronting_cached(ip: str) -> dict | None:
    with _FRONTING_CACHE_LOCK:
        entry = _FRONTING_CACHE.get(ip)
    return entry if entry and (time.monotonic() - entry["ts"]) < _FRONTING_CACHE_TTL else None

def _fronting_cache_set(ip: str, ok: bool, sni: str, cdn: str, code):
    with _FRONTING_CACHE_LOCK:
        _FRONTING_CACHE[ip] = {"fronting_ok": ok, "fronting_sni": sni, "cdn_type": cdn,
                               "fronting_code": code, "ts": time.monotonic()}

def check_psiphon_fronting(healthy: list, timeout: float = 4.0) -> list:
    if not healthy: return []
    total = len(healthy); results = [None] * total; done = [0]; lock = threading.Lock()
    print(f"  {D}Checking fronting ({total} IPs)...{X}", end="\r", flush=True)

    def _worker(idx, r):
        ip  = r["ip"]
        cdn = _detect_cdn_for_ip(ip) or "Unknown"
        cached = _fronting_cached(ip)
        if cached:
            entry = dict(r); entry.update(cached); results[idx] = entry
            with lock: done[0] += 1; return
        sni_override = SCAN_CFG.get("psiphon_sni", "").strip()
        fr = _check_fronting_all_pairs(ip, cdn, timeout, sni_override)
        sni_used = fr.get("sni", "")
        host_used = fr.get("host_header", sni_used)
        entry = dict(r)
        entry.update({
            "fronting_ok":     fr["fronting_ok"],
            "fronting_sni":    sni_used,
            "fronting_host":   host_used,
            "fronting_code":   fr.get("code"),
            "fronting_reason": fr.get("reason",""),
            "cdn_type":        cdn,
        })
        results[idx] = entry
        _fronting_cache_set(ip, fr["fronting_ok"], sni_used, cdn, fr.get("code"))
        with lock: done[0] += 1

    with ThreadPoolExecutor(max_workers=min(total, 100)) as pool:
        list(as_completed([pool.submit(_worker, i, r) for i, r in enumerate(healthy)]))
    print(f"\r{' '*70}\r", end="")
    return [e for e in results if e is not None]


# ── Throughput test ────────────────────────────────────────────────────────────
_THROUGHPUT_PATHS   = ["/robots.txt", "/", "/favicon.ico", "/cdn-cgi/trace"]
_THROUGHPUT_MIN_KBPS = 3

def _test_throughput(ip: str, host: str, timeout: float = 5.0) -> dict:
    result = {"kbps": 0.0, "bytes": 0, "ok": False, "reason": ""}
    ctx = ssl.create_default_context(); ctx.check_hostname = False; ctx.verify_mode = ssl.CERT_NONE
    for path in _THROUGHPUT_PATHS:
        try:
            with socket.create_connection((ip, 443), timeout=timeout) as s:
                with ctx.wrap_socket(s, server_hostname=host or ip) as ss:
                    req = (f"GET {path} HTTP/1.0\r\nHost: {host or ip}\r\n"
                           "User-Agent: curl/7.88\r\nConnection: close\r\n\r\n")
                    t0 = time.monotonic(); ss.sendall(req.encode()); data = b""
                    while len(data) < 8192:
                        chunk = ss.recv(4096)
                        if not chunk: break
                        data += chunk
            elapsed = max(time.monotonic() - t0, 0.001)
            header_part = data.split(b"\r\n\r\n", 1)[0].decode(errors="ignore") if b"\r\n\r\n" in data else ""
            status = 0
            if header_part.startswith("HTTP/"):
                parts_h = header_part.split()
                try: status = int(parts_h[1]) if len(parts_h) >= 2 and parts_h[1].isdigit() else 0
                except (IndexError, ValueError): pass
            if status >= 100:
                kbps = (len(data) / 1024) / elapsed
                result.update({"bytes": len(data), "kbps": round(kbps, 1), "ok": True,
                                "reason": f"{kbps:.0f} kB/s [{status}]"})
                return result
        except Exception as e: result["reason"] = str(e)[:30]
    return result

def run_throughput_checks(candidates: list, timeout: float = 5.0) -> list:
    if not candidates: return []
    total = len(candidates); results = [None] * total; lock = threading.Lock()
    print(f"  {D}Throughput check...{X}", end="\r", flush=True)

    def _worker(idx, r):
        ip = r["ip"]; host = r.get("host") or r.get("fronting_sni") or ip
        tp = _test_throughput(ip, host, timeout)
        entry = dict(r); entry["tp_kbps"] = tp["kbps"]; entry["tp_ok"] = tp["ok"]
        entry["tp_reason"] = tp["reason"]; results[idx] = entry
        with lock: pass

    with ThreadPoolExecutor(max_workers=min(total, 20)) as pool:
        list(as_completed([pool.submit(_worker, i, r) for i, r in enumerate(candidates)]))
    print(f"\r{' '*60}\r", end="")
    final = [e for e in results if e is not None]
    final.sort(key=lambda r: (not r.get("tp_ok"), r.get("ms", 9999)))
    return final


# ── Smart retry ────────────────────────────────────────────────────────────────
def smart_retry(border_ips: list, host: str, port: int, timeout: float) -> list:
    if not border_ips: return []
    clean = list(border_ips)
    relaxed = min(timeout * 1.2, 6.0); recovered = []; lock = threading.Lock()

    def _worker(r):
        ip = r["ip"] if isinstance(r, dict) else r
        h = (r.get("host") or host) if isinstance(r, dict) else host
        result = check_sync(ip, h, port, "/", relaxed, retries=1)
        if result.get("ok"):
            result["retried"] = True
            with lock: recovered.append(result)

    with ThreadPoolExecutor(max_workers=min(len(clean), 50)) as pool:
        list(as_completed([pool.submit(_worker, r) for r in clean]))
    return recovered


# ── Scan engines ───────────────────────────────────────────────────────────────
async def _scan_engine(ips, host, port, path, concurrency, timeout, retries):
    global _partial_healthy
    if not HAS_AIOHTTP:
        loop = asyncio.get_running_loop()
        return await loop.run_in_executor(
            None, lambda: _scan_engine_sync(ips, host, port, path, concurrency, timeout, retries))

    sem = asyncio.Semaphore(concurrency); total = len(ips)
    healthy = []; done_count = 0; lock = asyncio.Lock()
    connector = aiohttp.TCPConnector(ssl=_SSL_CTX, limit=concurrency+100, limit_per_host=0,
                                     ttl_dns_cache=600, enable_cleanup_closed=True,
                                     keepalive_timeout=20, force_close=False, happy_eyeballs_delay=0.1)

    async with aiohttp.ClientSession(connector=connector) as session:
        async def worker(ip):
            nonlocal done_count
            if _STOP_SCAN.is_set(): return
            async with sem:
                if _STOP_SCAN.is_set(): return
                r = await check_async(session, ip, host, port, path, timeout, retries)
                async with lock:
                    done_count += 1
                    if r["ok"]:
                        healthy.append(r)
                        with _partial_healthy_lock: _partial_healthy.append(r)
                    with _ANIM_LOCK:
                        _ANIM_STATS["pct"] = (done_count * 100 // total) if total else 100
                        _ANIM_STATS["found"] = len(healthy)
                    with _WEB_LOCK:
                        feed = _WEB_STATE["live_ips"]
                        feed.append({"ip": ip, "ok": r["ok"], "code": r.get("code",""), "ms": r.get("ms",0)})
                        if len(feed) > 500: _WEB_STATE["live_ips"] = feed[-500:]

        tasks = [asyncio.create_task(worker(ip)) for ip in ips]

        async def _watchdog():
            while not _STOP_SCAN.is_set(): await asyncio.sleep(0.05)
            for t in tasks:
                if not t.done(): t.cancel()
            await asyncio.gather(*tasks, return_exceptions=True)

        watchdog = asyncio.create_task(_watchdog())
        try: await asyncio.gather(*tasks, return_exceptions=True)
        finally:
            watchdog.cancel()
            try: await watchdog
            except asyncio.CancelledError: pass

    return healthy

def _scan_engine_sync(ips, host, port, path, concurrency, timeout, retries, stop_event=None):
    global _partial_healthy
    total = len(ips); healthy = []; done_count = 0; tlock = threading.Lock()

    def _should_stop(): return _STOP_SCAN.is_set() or (stop_event and stop_event.is_set())

    def worker(ip):
        nonlocal done_count
        if _should_stop(): return None
        r = check_sync(ip, host, port, path, timeout, retries)
        with tlock:
            done_count += 1
            if r["ok"]:
                healthy.append(r)
                with _partial_healthy_lock: _partial_healthy.append(r)
            with _ANIM_LOCK:
                _ANIM_STATS["pct"] = (done_count * 100 // total) if total else 100
                _ANIM_STATS["found"] = len(healthy)
            with _WEB_LOCK:
                feed = _WEB_STATE["live_ips"]
                feed.append({"ip": ip, "ok": r["ok"], "code": r.get("code",""), "ms": r.get("ms",0)})
                if len(feed) > 500: _WEB_STATE["live_ips"] = feed[-500:]
        return r

    with ThreadPoolExecutor(max_workers=concurrency) as pool:
        list(as_completed([pool.submit(worker, ip) for ip in ips]))
    return healthy


# ── run_scan ───────────────────────────────────────────────────────────────────
def run_scan(ips, host, port, path, concurrency, timeout, out_file,
             retries=2, source_label="", ranges_used=None, _skip_clr=False,
             _web_mode=False):
    global _partial_healthy
    with _partial_healthy_lock: _partial_healthy = []
    if not _skip_clr: _clr()
    total = len(ips)
    engine = "aiohttp async" if HAS_AIOHTTP else ("requests" if HAS_REQ else "socket")
    mode_str = f"host: {B}{host}{X}" if host else f"{D}auto SSL{X}"
    cnt_col = _ip_count_color(total)
    print(f"    {cnt_col}{B}{total:,} IPs{X}  ·  {mode_str}  ·  {D}{engine}{X}")
    print(f"  {D}scan → filter → fronting → throughput{X}")
    print(f"  {Y}Ctrl+C to stop early{X}\n")

    t_start = time.monotonic(); stopped_early = False; stop_ev = threading.Event()
    _result = []; _exc = []
    _anim_t = _start_anim_ticker()

    def _run_in_thread():
        try:
            if HAS_AIOHTTP:
                loop = asyncio.new_event_loop(); asyncio.set_event_loop(loop)
                try:
                    result = loop.run_until_complete(
                        _scan_engine(ips, host, port, path, concurrency, timeout, retries))
                finally:
                    pending = asyncio.all_tasks(loop)
                    for task in pending: task.cancel()
                    if pending: loop.run_until_complete(asyncio.gather(*pending, return_exceptions=True))
                    loop.close()
            else:
                result = _scan_engine_sync(ips, host, port, path, concurrency, timeout, retries, stop_ev)
            _result.append(result)
        except Exception as e: _exc.append(e)

    t = threading.Thread(target=_run_in_thread, daemon=True); t.start()
    try:
        while t.is_alive(): t.join(timeout=0.3)
    except KeyboardInterrupt:
        _STOP_SCAN.set(); stop_ev.set(); stopped_early = True
        _stop_anim_ticker(_anim_t)
        print(f"\n  {Y}Stopping...{X}", flush=True); t.join(timeout=3)
        healthy = sorted(list(_partial_healthy), key=lambda r: r["ms"])
        print(f"  {Y}{len(healthy)} IP(s) found so far.{X}")
    else:
        _stop_anim_ticker(_anim_t)
        healthy = _result[0] if _result else []

    elapsed = time.monotonic() - t_start
    _SEP_W = _box_width(min_w=36, max_w=52, content_w=42)
    print(f"\n  {D}{'─'*_SEP_W}{X}")
    label = f"{Y}Partial{X}" if stopped_early else f"{G}Done{X}"
    print(f"  {label}  {B}{total:,}{X} scanned  {G}{B}✓ {len(healthy)}{X} healthy  "
          f"{R}✗ {total - len(healthy)}{X} failed  {D}{elapsed:.1f}s{X}")

    fronting_all = []; fronting_verified = []

    def _show_status(msg): print(f"  {D}{msg}{X}", end="\r", flush=True)
    def _clear_status(): print(f"\r{' '*72}\r", end="", flush=True)

    if healthy:
        healthy.sort(key=lambda x: x["ms"])

        # Phase 1: Smart retry
        if not stopped_early:
            border = [r for r in healthy if r["ms"] > timeout * 1000 * 0.7]
            if border:
                recovered = smart_retry(border, host, port, timeout)
                for r in recovered:
                    if not any(h["ip"] == r["ip"] for h in healthy): healthy.append(r)
                healthy.sort(key=lambda x: x["ms"])

        _save_last_scan(source_label or "manual", ranges_used or [], healthy)

        # Phase 2: Iran filter (live geo check)
        _show_status("Checking regions (ipwho.is)...")
        _iran_check_map = get_country_codes_batch([r["ip"] for r in healthy], timeout=4.0)
        _clear_status(); iran_clean = []
        for _r in healthy:
            _cc = _iran_check_map.get(_r["ip"], ""); _r["country"] = _cc
            # only drop if CONFIRMED Iran — empty CC = keep
            if _cc.upper() == "IR" or _has_iran_domain(_r.get("host", "")):
                pass
            else:
                iran_clean.append(_r)
        if len(iran_clean) < len(healthy):
            print(f"  {R}✗ {len(healthy)-len(iran_clean)} Iranian IP(s) removed{X}  "
                  f"{G}✓ {len(iran_clean)} IPs passed{X}")
        healthy = iran_clean if iran_clean else healthy

        auto_save_healthy_ips(healthy)

        # Phase 3: Fronting verification
        _show_status("Checking CDN fronting...")
        fronting_all = check_psiphon_fronting(healthy, timeout)
        fronting_verified = [r for r in fronting_all if r.get("fronting_ok")]
        _fa_map = {r["ip"]: r for r in fronting_all}
        for r in healthy:
            if r["ip"] in _fa_map:
                for k in ("cdn_type","fronting_ok","fronting_sni","fronting_host","fronting_code"):
                    r[k] = _fa_map[r["ip"]].get(k)
        if fronting_verified: auto_save_healthy_ips(fronting_verified)
        final = healthy

        # Phase 4: Throughput
        if fronting_verified:
            _show_status("Checking throughput...")
            fronting_verified = run_throughput_checks(fronting_verified, timeout)
            tp_ok = [r for r in fronting_verified if r.get("tp_ok")]
            if tp_ok: fronting_verified = tp_ok

        # Phase 5: Country lookup (reuse map from Phase 2, fill any missing)
        missing_ips = [r["ip"] for r in final if not r.get("country")]
        if missing_ips:
            _show_status("Looking up missing regions...")
            extra = get_country_codes_batch(missing_ips, timeout=3.0)
            _iran_check_map.update(extra)
            _clear_status()
        for r in final:
            if not r.get("country"):
                r["country"] = _iran_check_map.get(r["ip"], "")

        fronting_ok_cnt = sum(1 for r in fronting_all if r.get("fronting_ok"))
        tp_ok_cnt = sum(1 for r in fronting_verified if r.get("tp_ok"))
        print(f"  {G}✓ {fronting_ok_cnt} fronting OK{X}  {D}·{X}  "
              f"{D if tp_ok_cnt else Y}{tp_ok_cnt if tp_ok_cnt else '⚠ throttled'}{X}")

        # Results table
        _W = _box_width(min_w=44, max_w=64, content_w=58)
        print(f"\n  {W}{'─'*_W}{X}")
        print(f"  {W}Results — {len(final)} IPs  /  {len(fronting_verified)} fronting OK{X}")
        print(f"  {D}  {'IP':<17} {'ms':>5}  {'code':>4}  {'CDN':<10}  {'CC':>3}  {'front':>5}  SNI{X}")
        print(f"  {W}{'─'*_W}{X}")

        for r in final:
            ip   = r["ip"]; ms = r["ms"]; code = r.get("code") or "—"
            cdn  = (r.get("cdn_type") or "?")[:10]
            fok  = r.get("fronting_ok", False)
            sni  = r.get("fronting_sni", "") or ""
            # shorten SNI for display: "prod.global.ssl.fastly.net" → "fastly"
            sni_short = (sni.split(".")[2] if sni.count(".") >= 2 else sni.split(".")[0]) if sni else "—"
            kbps = f"{r['tp_kbps']:.0f}" if r.get("tp_kbps") else "—"
            cc_str = (r.get("country") or "??").upper()
            ip_col = _ip_color(code); ms_col = G if ms < 200 else Y if ms < 400 else R
            cc_col = R if cc_str == "IR" else Y if cc_str not in ("","??") else D
            fok_s  = f"{G}{B}★{X}" if fok else f"{D}—{X}"
            print(f"  {ip_col}{ip:<17}{X} {ms_col}{ms:>4}ms{X}  {ip_col}{str(code):>4}{X}  "
                  f"{C}{cdn:<10}{X}  {cc_col}{cc_str:>3}{X}  {fok_s}  {D}{sni_short}{X}")

        print(f"  {W}{'─'*_W}{X}")

        if fronting_verified:
            print(f"\n  {G}{B}★  {len(fronting_verified)} IPs verified for ShirOKhorshid{X}\n")
            # Group by SNI+Host and show full details
            sni_groups: dict = {}
            for r in fronting_verified:
                sni    = r.get("fronting_sni","") or "—"
                host_v = r.get("fronting_host", sni) or sni
                sni_groups.setdefault((sni, host_v), []).append(r["ip"])
            for (sni, host_v), ip_list in sni_groups.items():
                print(f"  {D}SNI {X} {C}{B}{sni}{X}")
                if host_v and host_v != sni:
                    print(f"  {D}Host{X} {W}{host_v}{X}")
                print(f"  {D}IPs {X} {G}{', '.join(ip_list)}{X}\n")
        else:
            print(f"  {Y}No IPs passed fronting check{X}")
            try:
                if _ask_yn("  Show raw healthy IPs anyway (debug)?"):
                    fronting_verified = final
            except (EOFError, KeyboardInterrupt): pass

        if out_file:
            base = out_file.rsplit(".", 1)[0] if "." in os.path.basename(out_file) else out_file
            try:
                with open(out_file, "w") as f:
                    f.write(f"# CDN Scan — {datetime.now().isoformat()}\n")
                    for r in final: f.write(f"{r['ip']}  # {r['ms']}ms  {r.get('host','')}\n")
                print(f"\n  {C}Saved:{X} {out_file}")
            except Exception as e: print(f"\n  {R}Save failed: {e}{X}")
            for ext, data in [(".json", json.dumps({"timestamp": datetime.now().isoformat(),
                "total_scanned": total, "healthy": [{"ip": r["ip"],"ms": r["ms"],"code": r.get("code",0),
                "host": r.get("host",""),"fronting": r.get("fronting_ok",False)} for r in final]}, indent=2)),
                (".csv", "ip,ms,code,host,fronting\n" + "\n".join(
                    f"{r['ip']},{r['ms']},{r.get('code',0)},{r.get('host','')},{r.get('fronting_ok',False)}"
                    for r in final))]:
                try:
                    with open(base + ext, "w") as f: f.write(data if isinstance(data, str) else data)
                except Exception: pass

        if fronting_verified:
            final_ips = [r["ip"] for r in fronting_verified if r.get("country","").upper() != "IR"] or \
                        [r["ip"] for r in fronting_verified]
            _label_ips = f"{G}★ Fronting-verified  (IR excluded){X}"
        else:
            final_ips = []; _label_ips = ""

        if final_ips:
            _has_ranges = bool(ranges_used)
            if _web_mode:
                ans = "n"
            else:
                try:
                    sys.stdout.write(f"\n  {C}Copy screen?{X} {D}[Y/n{'  r=retry' if _has_ranges else ''}]{X}: ")
                    sys.stdout.flush(); ans = input("").strip().lower()
                except (EOFError, KeyboardInterrupt): ans = "n"
            if ans == "r" and _has_ranges:
                _seen_ips = set(ips)
                _all_single = all(r.endswith("/32") for r in ranges_used)
                if _all_single:
                    _retry_ips = list(ips); random.shuffle(_retry_ips)
                    return run_scan(_retry_ips, host, port, path, concurrency, timeout,
                                    out_file, retries, source_label=source_label+" ↻", ranges_used=ranges_used)
                _retry_ips = [ip for ip in expand_cidrs(ranges_used, max(len(ips)*5, 5000)) if ip not in _seen_ips]
                random.shuffle(_retry_ips)
                if _retry_ips:
                    return run_scan(_retry_ips, host, port, path, concurrency, timeout,
                                    out_file, retries, source_label=source_label+" ↻", ranges_used=ranges_used)
            elif ans != "n":
                _show_copy_page(final_ips, _label_ips)
    else:
        print(f"\n  {Y}No healthy IPs found.{X}")
        if not _web_mode:
            if ranges_used and not stopped_early:
                try:
                    sys.stdout.write(f"  {C}Retry scan?{X} {D}[y/N]{X}: ")
                    sys.stdout.flush(); ans2 = input("").strip().lower()
                except (EOFError, KeyboardInterrupt): ans2 = ""
                if ans2 in ("y","yes"):
                    _retry_ips = expand_cidrs(ranges_used, max(len(ips), 3000)); random.shuffle(_retry_ips)
                    if _retry_ips:
                        return run_scan(_retry_ips, host, port, path, concurrency, timeout,
                                        out_file, retries, source_label=source_label+" ↻",
                                        ranges_used=ranges_used)
            else:
                try: input(f"\n  {D}[ Enter to return ]{X}")
                except (EOFError, KeyboardInterrupt): pass
        fronting_all = []

    return fronting_all or healthy or []


# ── IP expansion ───────────────────────────────────────────────────────────────
def expand_cidrs(cidrs, sample):
    nets = []; total_size = 0
    if not cidrs: return []
    for cidr in cidrs:
        try:
            net = ipaddress.ip_network(cidr, strict=False)
            size = 1 if net.prefixlen >= 31 else max(net.num_addresses - 2, 1)
            nets.append((net, size)); total_size += size
        except ValueError: print(f"  {R}Invalid CIDR: {cidr}{X}")
    if total_size == 0: return []
    MAX_EXPAND = 50_000
    if total_size <= sample and total_size <= MAX_EXPAND:
        ips = []
        for net, size in nets:
            if net.prefixlen >= 31: ips.append(str(net.network_address))
            else: ips.extend(str(ip) for ip in net.hosts())
        return list(dict.fromkeys(ips))
    if total_size > MAX_EXPAND and sample >= MAX_EXPAND:
        print(f"  {Y}~{total_size:,} IPs total — capped at {sample:,}{X}")
    weights = [s for _, s in nets]; total_w = sum(weights)
    if total_w == 0 or sample <= 0: return []
    picked = set(); attempts = 0; max_att = sample * 5
    while len(picked) < sample and attempts < max_att:
        attempts += 1; r = random.random() * total_w; cum = 0
        chosen_net, chosen_size = nets[-1]
        for (net, size), w in zip(nets, weights):
            cum += w
            if r <= cum: chosen_net, chosen_size = net, size; break
        if chosen_size < 1: continue
        if chosen_net.prefixlen >= 31: picked.add(str(chosen_net.network_address))
        else:
            idx = random.randint(1, chosen_net.num_addresses - 2)
            picked.add(str(chosen_net.network_address + idx))
    result = list(picked); random.shuffle(result)
    return result

def _sample_ip_from_cidr(cidr: str) -> str | None:
    try:
        net = ipaddress.ip_network(cidr, strict=False)
        if net.prefixlen >= 31: return str(net.network_address)
        n = net.num_addresses - 2
        if n < 1: return None
        return str(net.network_address + random.randint(1, n))
    except ValueError: return None


# ── Smart Scan engines ─────────────────────────────────────────────────────────
async def _probe_engine_async(probe_list, timeout=2.0, concurrency=180):
    sem = asyncio.Semaphore(concurrency); results = []; lock = asyncio.Lock()
    done = [0]; total = len(probe_list)
    connector = aiohttp.TCPConnector(ssl=_SSL_CTX, limit=concurrency+20, limit_per_host=0,
                                     enable_cleanup_closed=True)
    async with aiohttp.ClientSession(connector=connector) as session:
        async def worker(ip, cdn_name, cidr):
            async with sem:
                t0 = time.monotonic(); ok = False; ms = int(timeout * 1000)
                try:
                    async with session.get(f"https://{ip}:443/",
                        headers={"User-Agent": "curl/7.88","Connection": "close"},
                        timeout=aiohttp.ClientTimeout(connect=timeout*0.6, total=timeout),
                        allow_redirects=False) as resp:
                        ms = int((time.monotonic()-t0)*1000); ok = resp.status < 500
                except asyncio.CancelledError: raise
                except Exception: ms = int((time.monotonic()-t0)*1000)
                async with lock:
                    done[0] += 1; results.append((ip, cdn_name, cidr, ok, ms))
                    with _ANIM_LOCK:
                        _ANIM_STATS["pct"] = (done[0]*100//total) if total else 100
                        _ANIM_STATS["found"] = sum(1 for *_, o, _ in results if o)
        await asyncio.gather(*[asyncio.create_task(worker(ip,cdn,cidr)) for ip,cdn,cidr in probe_list],
                             return_exceptions=True)
    return results

def _probe_engine_sync(probe_list, timeout=2.0, concurrency=80):
    results = []; done = [0]; total = len(probe_list); tlock = threading.Lock()

    def worker(ip, cdn_name, cidr):
        t0 = time.monotonic(); ok = False; ms = int(timeout*1000)
        try:
            if HAS_REQ:
                r = requests.get(f"https://{ip}:443/", headers={"User-Agent":"curl/7.88"},
                                  timeout=timeout, verify=False, allow_redirects=False, stream=True)
                r.close(); ms = int((time.monotonic()-t0)*1000); ok = r.status_code < 500
            else:
                with socket.create_connection((ip, 443), timeout=timeout) as s:
                    with _SSL_CTX.wrap_socket(s, server_hostname=ip) as ss:
                        ss.sendall(b"GET / HTTP/1.0\r\nHost: "+ip.encode()+b"\r\nConnection: close\r\n\r\n")
                        resp = ss.recv(128).decode(errors="ignore")
                ms = int((time.monotonic()-t0)*1000); parts = resp.split()
                ok = (len(parts)>=2 and resp.startswith("HTTP/") and parts[1].isdigit() and int(parts[1])<500)
        except Exception: ms = int((time.monotonic()-t0)*1000)
        with tlock:
            done[0] += 1; results.append((ip, cdn_name, cidr, ok, ms))
            with _ANIM_LOCK:
                _ANIM_STATS["pct"] = (done[0]*100//total) if total else 100
                _ANIM_STATS["found"] = sum(1 for *_,o,_ in results if o)

    with ThreadPoolExecutor(max_workers=concurrency) as pool:
        list(as_completed([pool.submit(worker,ip,cdn,cidr) for ip,cdn,cidr in probe_list]))
    return results

def _build_probe_list(cdn_ranges_dict: dict, max_ranges_per_cdn: int = 120, ips_per_range: int = 2) -> list:
    probe_list = []
    for cdn_name, ranges in cdn_ranges_dict.items():
        for cidr in random.sample(ranges, min(len(ranges), max_ranges_per_cdn)):
            for _ in range(ips_per_range):
                ip = _sample_ip_from_cidr(cidr)
                if ip: probe_list.append((ip, cdn_name, cidr))
    random.shuffle(probe_list); return probe_list

def _aggregate_probe(probe_results: list) -> list:
    stats: dict = {}
    for ip, cdn_name, cidr, ok, ms in probe_results:
        key = (cdn_name, cidr)
        if key not in stats: stats[key] = {"ok": 0, "total": 0, "ms_list": []}
        stats[key]["total"] += 1
        if ok: stats[key]["ok"] += 1; stats[key]["ms_list"].append(ms)
    ranked = []
    for (cdn_name, cidr), s in stats.items():
        if s["ok"] == 0: continue
        avg_ms = int(sum(s["ms_list"]) / len(s["ms_list"]))
        score  = (s["ok"] / s["total"]) * 100 - avg_ms / 10
        try: net = ipaddress.ip_network(cidr, strict=False); size = max(net.num_addresses-2,1)
        except ValueError: size = 0
        ranked.append({"cdn": cdn_name, "cidr": cidr, "ok": s["ok"], "total": s["total"],
                        "avg_ms": avg_ms, "score": score, "size": size})
    ranked.sort(key=lambda x: (-x["score"], x["avg_ms"]))
    return ranked

def run_smart_scan():
    _clr(); host, retries, concurrency, timeout, out_file = _get_cfg()
    cdn_ranges_full = dict(CDN_RANGES)

    # learned IPs → /24 ranges so whole neighbourhood is scanned
    learned_ips = _load_learned().get("ips", [])
    if learned_ips:
        learned_cidrs = []
        for ip in learned_ips[:20]:
            try:
                broad = str(ipaddress.ip_network(f"{ip}/24", strict=False))
                if broad not in learned_cidrs: learned_cidrs.append(broad)
            except Exception:
                learned_cidrs.append(f"{ip}/32")
        cdn_ranges_full["Learned"] = learned_cidrs
        print(f"  {G}✓ {len(learned_cidrs)} learned /24 range(s){X}")

    if USER_LOADED_RANGES: cdn_ranges_full["Loaded IPs"] = list(USER_LOADED_RANGES)
    _draw_box("  Smart Scan  Auto Probe  ", ["Probes CDN ranges, then deep scan"])
    probe_list = _build_probe_list(cdn_ranges_full, max_ranges_per_cdn=50, ips_per_range=2)
    n_probe = len(probe_list)
    print(f"  {Y}Probing {n_probe} IPs...{X}\n")
    t_probe = time.monotonic(); probe_results = []
    _probe_ticker = _start_anim_ticker("probing")
    try:
        if HAS_AIOHTTP: probe_results = asyncio.run(_probe_engine_async(probe_list, min(timeout,2.0), 80))
        else: probe_results = _probe_engine_sync(probe_list, min(timeout,2.0), 80)
    except KeyboardInterrupt:
        _stop_anim_ticker(_probe_ticker); print(f"\n\n  {Y}Probe cancelled.{X}"); return
    _stop_anim_ticker(_probe_ticker)
    good_probes = sum(1 for *_,ok,_ in probe_results if ok)
    print(f"  {G}✓ Probe done{X}  {D}{time.monotonic()-t_probe:.1f}s  {good_probes}/{n_probe} responded{X}")
    ranked = _aggregate_probe(probe_results)
    if not ranked:
        print(f"\n  {R}No ranges responded. Try increasing timeout in Settings.{X}"); return
    # Display probe results
    _W = _box_width(min_w=46, max_w=58, content_w=50)
    print(f"  {W}{B}Probe Results — Ranked Ranges{X}")
    print(f"  {D}  #    Range                  CDN         Hit    ms{X}")
    for i, r in enumerate(ranked, 1):
        hit_col = G if r["ok"] == r["total"] else Y
        ms_col = G if r["avg_ms"] < 200 else Y if r["avg_ms"] < 400 else R
        print(f"  {G}{B}{i:>3}{X}  {W}{r['cidr']:<21}{X}  {C}{r['cdn'][:10]:<10}{X}  "
              f"  {hit_col}{r['ok']}/{r['total']:<5}{X}  {ms_col}{r['avg_ms']}ms{X}")
    print(f"  {D}{len(ranked)} ranges responded{X}")
    print(f"  {D}e.g: 1 2 5  ·  top5  ·  top10  ·  all{X}")
    try:
        sys.stdout.write(f"  {C}{B}Select{X}: "); sys.stdout.flush()
        raw = input("").strip().lower()
    except (EOFError, KeyboardInterrupt): return
    if not raw: return
    selected = (ranked if raw in ("all","*") else
                ranked[:int(raw[3:])] if raw.startswith("top") and raw[3:].isdigit() else
                [ranked[int(t)-1] for t in raw.replace(","," ").split()
                 if t.isdigit() and 1 <= int(t) <= len(ranked)])
    if not selected: print(f"  {R}No ranges selected.{X}"); return
    cdns_used = list(dict.fromkeys(r['cdn'] for r in selected))
    print(f"\n  {C}→ {len(selected)} range(s) selected  {D}·  {', '.join(cdns_used)}{X}")
    cidrs_selected = [r["cidr"] for r in selected]
    ips = expand_cidrs(cidrs_selected, ask_int("Max IPs to scan (0 = all)", 0) or 999999)
    if not ips: print(f"  {R}No IPs could be generated.{X}"); return
    random.shuffle(ips)
    source_label = f"Smart Scan ({', '.join(dict.fromkeys(r['cdn'] for r in selected))})"
    _clr(); _show_scan_preview(len(ips), concurrency, timeout, source=source_label)
    run_scan(ips, host, 443, "/", concurrency, timeout, out_file, retries,
             source_label=source_label, ranges_used=cidrs_selected, _skip_clr=True)


# ── Input helpers ──────────────────────────────────────────────────────────────
def ask(prompt, default=""):
    try:
        sys.stdout.write(f"  {prompt} [{default}]: "); sys.stdout.flush()
        val = input("").strip(); return val if val else default
    except EOFError: return default

def ask_int(prompt, default: int) -> int:
    while True:
        sys.stdout.write(f"  {prompt} [{default}]: "); sys.stdout.flush()
        try: raw = input("").strip()
        except EOFError: return default
        val = raw if raw else str(default)
        try: return int(val)
        except ValueError: print(f"  {R}Enter a whole number (e.g. {default}){X}")

def _ask_yn(prompt) -> bool:
    try:
        sys.stdout.write(f"  {prompt} [y/n]: "); sys.stdout.flush()
        return input("").strip().lower() == "y"
    except (EOFError, KeyboardInterrupt): return False

def _parse_token(token: str):
    t = re.sub(r'^https?://', '', token.strip().strip('"\''))
    t = t.split('#')[0].split('?')[0].strip()
    if not t: return None
    if '/' in t:
        try: net = ipaddress.ip_network(t.split(':')[0].strip(), strict=False); return (str(net), True)
        except ValueError: pass
    if t.startswith('['):
        m = re.match(r'^\[(.+)\](?::\d+)?$', t)
        if m:
            try: ipaddress.ip_address(m.group(1)); return (m.group(1), False)
            except ValueError: pass
    if t.count(':') == 1:
        host, _ = t.rsplit(':', 1)
        try: ipaddress.ip_address(host); return (host, False)
        except ValueError: pass
    try: ipaddress.ip_address(t); return (t, False)
    except ValueError: pass
    m = re.search(r'(\d{1,3}\.\d{1,3}\.\d{1,3}\.\d{1,3}(?:/\d{1,2})?)', t)
    if m:
        candidate = m.group(1)
        try:
            net = ipaddress.ip_network(candidate, strict=False)
            return (str(net) if '/' in candidate else str(net.network_address), '/' in candidate)
        except ValueError: pass
    return None

def _extract_ip_from_token(token: str):
    result = _parse_token(token)
    if result is None: return None
    val, is_cidr = result
    if is_cidr:
        try:
            net = ipaddress.ip_network(val, strict=False)
            return str(net.network_address) if net.num_addresses == 1 else None
        except ValueError: return None
    return val

def _parse_ip_block(text: str) -> list:
    """
    Extract valid IPs/CIDRs from a block of text (handles clipboard paste).
    Strips prompt chars like '>', '#', blank lines, mixed separators.
    Returns list of (val, is_cidr) tuples.
    """
    results = []
    seen = set()
    # split on newlines first, then commas/spaces within each line
    for line in re.split(r'[\r\n]+', text):
        # strip leading prompt chars: '>', '+', '-', '#', digits followed by '.'
        line = re.sub(r'^[\s>+\-#]*', '', line).strip()
        line = line.split('#')[0].strip()
        if not line:
            continue
        for tok in re.split(r'[\s,;]+', line):
            tok = tok.strip()
            if not tok:
                continue
            result = _parse_token(tok)
            if result is None:
                continue
            val, is_cidr = result
            key = val
            if key not in seen:
                seen.add(key)
                results.append((val, is_cidr))
    return results


def input_manual_ips():
    _clr()
    _draw_box("  Single IP Scan  ", [
        "Paste IPs — one per line or comma separated",
        "Empty line when done  ·  Ctrl+D to finish",
    ])
    ips = []; ips_set = set()
    print(f"\n  {D}Paste your IPs below:{X}\n")

    def _add_token(val, is_cidr) -> int:
        """Add val (IP or CIDR≤1024) to ips list. Returns count added."""
        added = 0
        if is_cidr:
            try:
                net = ipaddress.ip_network(val, strict=False)
                n   = net.num_addresses - 2
                if n <= 1024:
                    for h in (net.hosts() if n > 0 else [net.network_address]):
                        s = str(h)
                        if s not in ips_set:
                            ips_set.add(s); ips.append(s); added += 1
                else:
                    print(f"  {Y}!{X}  Large CIDR {val} ({n:,} hosts) — use CIDR scan")
            except ValueError:
                print(f"  {R}✗{X}  Invalid CIDR: {val}")
        else:
            if val not in ips_set:
                ips_set.add(val); ips.append(val); added += 1
        return added

    buf = []   # accumulate lines from stdin
    while True:
        try:
            sys.stdout.write(f"  {D}>{X} "); sys.stdout.flush()
            line = input("")
        except EOFError:
            break
        except KeyboardInterrupt:
            break

        # Termux clipboard paste sends all lines at once via stdin buffer.
        # We get one line at a time from input(), but collect them all then
        # process together so mixed-up prompt chars are handled uniformly.
        buf.append(line)

        # Empty line = done signal
        if line.strip() == "":
            if ips:
                break
            # no IPs yet — process what we have and keep going
            if buf:
                block = "\n".join(buf)
                tokens = _parse_ip_block(block)
                added_total = 0
                for val, is_cidr in tokens:
                    added_total += _add_token(val, is_cidr)
                buf = []
                if added_total:
                    print(f"  {G}+{added_total}{X}  {D}total: {len(ips)}{X}")
            continue

    # Process any remaining buffered lines
    if buf:
        block = "\n".join(buf)
        tokens = _parse_ip_block(block)
        added_total = 0
        for val, is_cidr in tokens:
            added_total += _add_token(val, is_cidr)
        if added_total:
            print(f"  {G}+{added_total}{X}  {D}total: {len(ips)}{X}")

    print(f"\n  {G}✓  {len(ips):,} IP(s) ready{X}\n")
    return ips

def input_cidr_manual():
    _clr()
    _draw_box("  CIDR Range Scan  ", [
        "Paste ranges — one per line or comma separated",
        "Empty line when done  ·  Ctrl+D to finish",
    ])
    print(f"\n  {D}Example: 104.16.0.0/12  or  2.16.0.0/13{X}\n")
    ranges = []; ranges_set = set()

    def _add_range(tok) -> int:
        result = _parse_token(tok)
        if result is None:
            print(f"  {R}✗{X}  {D}{tok}{X}"); return 0
        val, is_cidr = result
        if not is_cidr:
            val = val + '/32'
        try:
            net   = ipaddress.ip_network(val, strict=False)
            entry = str(net)
            if entry not in ranges_set:
                ranges_set.add(entry); ranges.append(entry); return 1
        except ValueError:
            print(f"  {R}✗{X}  Invalid: {val}")
        return 0

    buf = []
    while True:
        try:
            sys.stdout.write(f"  {D}>{X} "); sys.stdout.flush()
            line = input("")
        except (EOFError, KeyboardInterrupt):
            break

        buf.append(line)

        if line.strip() == "":
            if ranges:
                break
            if buf:
                block = "\n".join(buf)
                added = 0
                for tok in re.split(r'[\s,;\r\n]+', re.sub(r'^[\s>+\-#]*', '', block, flags=re.MULTILINE)):
                    tok = tok.strip()
                    if tok:
                        added += _add_range(tok)
                buf = []
                if added:
                    total_ips = sum(max(ipaddress.ip_network(r, strict=False).num_addresses-2,1) for r in ranges)
                    print(f"  {G}+{added}{X}  {D}total: {len(ranges)} range(s) · ~{total_ips:,} IPs{X}")
            else:
                print(f"  {Y}Enter at least one range (e.g. 104.16.0.0/12){X}")
            continue

    # Process remaining buffer
    if buf:
        block = "\n".join(buf)
        added = 0
        for tok in re.split(r'[\s,;\r\n]+', re.sub(r'^[\s>+\-#]*', '', block, flags=re.MULTILINE)):
            tok = tok.strip()
            if tok:
                added += _add_range(tok)
        if added:
            total_ips = sum(max(ipaddress.ip_network(r, strict=False).num_addresses-2,1) for r in ranges)
            print(f"  {G}+{added}{X}  {D}total: {len(ranges)} range(s) · ~{total_ips:,} IPs{X}")

    return ranges


# ── Platform & settings ────────────────────────────────────────────────────────
def _detect_platform() -> str:
    if (os.environ.get("TERMUX_VERSION") or
            os.environ.get("PREFIX","").startswith("/data/data/com.termux") or
            os.path.exists("/data/data/com.termux") or
            os.path.exists("/system/build.prop")):
        return "android"
    return "desktop"

_PLATFORM            = _detect_platform()
_DEFAULT_CONCURRENCY = 80 if _PLATFORM == "android" else 200
_DEFAULT_TIMEOUT     = 3.5

SCAN_CFG = {"host":"","retries":2,"concurrency":_DEFAULT_CONCURRENCY,
            "timeout":_DEFAULT_TIMEOUT,"out_file":None,"psiphon_sni":""}

def _get_cfg():
    c = SCAN_CFG
    return c["host"], c["retries"], c["concurrency"], c["timeout"], c["out_file"]

def show_settings():
    _clr(); c = SCAN_CFG
    _draw_box("  Settings  ", ["Blank = keep  ·  '-' = clear",
                                f"Platform: {'Android / Termux' if _PLATFORM=='android' else 'Desktop'}"])
    print()

    def _field(label, key, cast, hint=""):
        cur = c[key] if c[key] not in (None,"") else "(auto)"
        if hint: print(f"  {D}{hint}{X}")
        sys.stdout.write(f"  {C}{label}{X} [{D}{cur}{X}]: "); sys.stdout.flush()
        try: raw = input("").strip()
        except (EOFError, KeyboardInterrupt): raise KeyboardInterrupt
        if not raw: return
        if cast == "clear":
            c[key] = ("" if key == "psiphon_sni" else None) if raw == "-" else raw; return
        try: c[key] = cast(raw)
        except ValueError: print(f"  {R}  Invalid — keeping: {cur}{X}")

    conc_hint = "Android: 40–80 recommended" if _PLATFORM=="android" else "Laptop: 100–200  /  Server: 300+"
    try:
        _field("Host header        (- to clear = auto SSL)", "host", "clear")
        _field("Retries per IP", "retries", int)
        _field("Concurrency", "concurrency", int, hint=conc_hint)
        _field("Timeout (seconds)", "timeout", float)
        _field("Save results to    (- to disable)", "out_file", "clear")
        _field("Psiphon SNI        (- for auto, e.g. a248.e.akamai.net)", "psiphon_sni", "clear")
    except KeyboardInterrupt:
        print(f"\n  {Y}Cancelled.{X}"); time.sleep(0.4); return
    print(f"\n  {G}✓ Settings saved{X}")
    try: input(f"\n  {D}[ Enter to return ]{X}")
    except (EOFError, KeyboardInterrupt): pass


# ── UI helpers ─────────────────────────────────────────────────────────────────
_ANSI_RE = re.compile(r'\033\[[0-9;]*m')

def _get_term_width() -> int:
    try: return os.get_terminal_size().columns
    except OSError: return 40 if _PLATFORM == "android" else 80

def _box_width(min_w: int = 30, max_w: int = 60, content_w: int = 0) -> int:
    term = _get_term_width(); avail = max(min_w, min(max_w, term - 6))
    return max(avail, content_w)

def _draw_box(title: str, lines: list, min_w: int = 28, max_w: int = 44):
    sep = "─" * max(len(title.strip()), 30)
    print(f"\n  {W}{B}{title.strip()}{X}")
    print(f"  {D}{sep}{X}")
    for ln in lines: print(f"  {D}{ln}{X}")
    if lines: print(f"  {D}{sep}{X}")

def _count_ips_in_ranges(ranges: list) -> int:
    total = 0
    for cidr in ranges:
        try: total += max(ipaddress.ip_network(cidr, strict=False).num_addresses-2, 1)
        except ValueError: pass
    return total

def _show_copy_page(ips: list, label: str = ""):
    if not ips: return
    _clr(); _W = _box_width(min_w=32, max_w=48, content_w=36)
    print(f"  {W}{B}IPs — Copy Screen{X}")
    if label:
        plain_label = re.sub(r'\033\[[^m]*m', '', label)
        print(f"  {label}  {D}({len(ips)} IPs){X}")
    print()
    for ip in ips: print(f"  {G}{B}{ip}{X}")
    clip_cmd = _find_termux_clip() if _PLATFORM == "android" else None
    text_to_copy = "\n".join(ips)
    if clip_cmd is not None or _PLATFORM == "android":
        sys.stdout.write(f"\n  {C}Copy to clipboard?{X} {D}[Y/n]{X}: "); sys.stdout.flush()
        try: ans = input("").strip().lower()
        except (EOFError, KeyboardInterrupt): ans = "n"
        if ans != "n":
            copied = False
            if clip_cmd:
                try:
                    proc = subprocess.run(clip_cmd, input=text_to_copy, capture_output=True, timeout=5, text=True)
                    copied = proc.returncode == 0
                except Exception: pass
            if not copied: copied = _termux_clip_via_am(text_to_copy)
            if copied: print(f"  {G}✓ {len(ips)} IP(s) copied{X}")
            else: _clipboard_install_hint()
    try: input(f"\n  {D}[ Enter to return ]{X}")
    except (EOFError, KeyboardInterrupt): pass
    _clr()


# ── Clipboard ──────────────────────────────────────────────────────────────────
def _find_termux_clip() -> list | None:
    candidates = [
        os.path.join(os.environ.get("PREFIX","/data/data/com.termux/files/usr"), "bin","termux-clipboard-set"),
        "/data/data/com.termux/files/usr/bin/termux-clipboard-set",
    ]
    for path in candidates:
        if os.path.isfile(path): return [path]
    try:
        result = subprocess.run(["which","termux-clipboard-set"], capture_output=True, text=True, timeout=2)
        found = result.stdout.strip()
        if found and os.path.isfile(found): return [found]
    except Exception: pass
    return None

def _termux_clip_via_am(text: str) -> bool:
    for cmd in [["bash","-c","termux-clipboard-set"], ["termux-clipboard-set"],
                ["xclip","-selection","clipboard"], ["xsel","--clipboard","--input"]]:
        try:
            proc = subprocess.run(cmd, input=text, capture_output=True, text=True, timeout=6)
            if proc.returncode == 0: return True
        except Exception: pass
    return False

def _clipboard_install_hint():
    print(f"\n  {Y}┌──────────────────────────────────┐{X}")
    print(f"  {Y}│{X}  {R}{B}⚠  Clipboard unavailable{X}      {Y}│{X}")
    print(f"  {Y}├──────────────────────────────────┤{X}")
    print(f"  {Y}│{X}  {G}pkg install termux-api{X}       {Y}│{X}")
    print(f"  {Y}└──────────────────────────────────┘{X}\n")

def _copy_to_clipboard(ips: list):
    if not ips: return
    text = "\n".join(ips); clip_cmd = _find_termux_clip()
    sys.stdout.write(f"\n  {C}Copy {len(ips)} IP(s) to clipboard?{X}  {D}[Y/n]{X}: "); sys.stdout.flush()
    try: ans = input("").strip().lower()
    except (EOFError, KeyboardInterrupt): _print_ip_grid(ips); return
    if ans == "n": _print_ip_grid(ips); return
    copied = False
    if clip_cmd:
        try:
            proc = subprocess.run(clip_cmd, input=text, capture_output=True, timeout=6, text=True)
            copied = proc.returncode == 0
        except Exception: pass
    if not copied: copied = _termux_clip_via_am(text)
    if copied: print(f"  {G}✓  {len(ips)} IP(s) copied to clipboard{X}")
    else: _clipboard_install_hint(); _print_ip_grid(ips)

def _print_ip_grid(ips: list):
    try: term_w = os.get_terminal_size().columns
    except OSError: term_w = 40
    ip_w = 15; indent = 2; gap = 2; col_w = ip_w + gap
    cols = max(1, (term_w - indent) // col_w)
    sep = "─" * min(cols * col_w + indent - gap, term_w - 2)
    print(f"\n  {D}{sep}{X}\n  {G}{B}Clean IPs  ({len(ips)}){X}\n  {D}{sep}{X}")
    for row_start in range(0, len(ips), cols):
        line = "  "
        for ip in ips[row_start:row_start+cols]: line += f"{G}{B}{ip:<{ip_w}}{X}" + " " * gap
        print(line)
    print(f"  {D}{sep}{X}")


# ── File loading ───────────────────────────────────────────────────────────────
def _find_txt_files() -> list:
    home = os.path.expanduser("~")
    search_dirs = list(dict.fromkeys(filter(os.path.isdir, [
        os.path.dirname(os.path.abspath(__file__)), os.getcwd(),
        "/sdcard/Download", "/sdcard/Downloads",
        "/storage/emulated/0/Download", "/storage/emulated/0/Downloads",
        os.path.join(home, "storage","downloads"),
        os.path.join(home, "storage","shared","Download"),
        os.path.join(home, "Downloads"),
    ])))
    seen_paths = set(); txt_files = []
    for d in search_dirs:
        try:
            for f in sorted(os.listdir(d)):
                if not f.endswith(".txt"): continue
                fpath = os.path.join(d, f)
                if not os.path.isfile(fpath) or fpath in seen_paths: continue
                seen_paths.add(fpath)
                ranges = _load_text_ranges_from_path(fpath)
                if ranges: txt_files.append((f, fpath, len(ranges)))
        except Exception: pass
    return txt_files

def load_ranges():
    _clr(); txt_files = _find_txt_files(); idx = 1
    _LW = _box_width(min_w=36, max_w=54)
    print(f"  {W}{B}Load IP Ranges{X}\n  {D}File · Paste · Edit{X}\n")
    file_rows = []
    if txt_files:
        print(f"  {Y}{B} Found files {X}")
        for fname, fpath, count in txt_files:
            total_ips = _count_ips_in_ranges(_load_text_ranges_from_path(fpath))
            ip_col = G if total_ips < 500_000 else Y
            print(f"  {C}{B}[{idx}]{X}  {W}{fname:<20}{X}  {D}{count} ranges{X}  {ip_col}~{total_ips:,} IPs{X}")
            file_rows.append((fname, fpath)); idx += 1

    custom_idx = idx; print(f"  {C}{B}[{idx}]{X}  {W}Enter custom file path{X}"); idx += 1
    manual_idx = idx; print(f"  {C}{B}[{idx}]{X}  {W}Paste IPs manually{X}"); idx += 1
    print(f"\n  {D}blank = cancel{X}\n")
    try:
        sys.stdout.write("  > "); sys.stdout.flush()
        choice = input("").strip()
    except (EOFError, KeyboardInterrupt): return
    if not choice.isdigit(): return
    choice_num = int(choice); ranges = []; source_file = None

    if 1 <= choice_num <= len(file_rows):
        fname, fpath = file_rows[choice_num-1]
        ranges = _load_text_ranges_from_path(fpath); source_file = fname
        print(f"\n  {G}✓ Loaded: {fname}  ({len(ranges)} ranges){X}" if ranges else f"\n  {R}No valid ranges in {fname}.{X}")
    elif choice_num == custom_idx:
        try:
            sys.stdout.write(f"\n  {C}File name or path{X}: "); sys.stdout.flush()
            fpath_raw = input("").strip()
            if fpath_raw:
                if not os.path.sep in fpath_raw and not os.path.isabs(fpath_raw):
                    for d in ["/sdcard/Download","/sdcard/Downloads","/storage/emulated/0/Download",
                               os.path.dirname(os.path.abspath(__file__)),os.getcwd()]:
                        c = os.path.join(d, fpath_raw)
                        if os.path.isfile(c): fpath_raw = c; break
                if os.path.isfile(fpath_raw):
                    ranges = (_load_json_ranges(os.path.basename(fpath_raw)) if fpath_raw.endswith(".json")
                              else _load_text_ranges_from_path(fpath_raw))
                    ranges = list(dict.fromkeys(ranges)); source_file = os.path.basename(fpath_raw)
                    print(f"\n  {G}✓ Loaded: {source_file}  ({len(ranges)} ranges){X}" if ranges else f"\n  {R}No valid ranges found.{X}")
                else: print(f"\n  {R}File not found: {fpath_raw}{X}")
        except (EOFError, KeyboardInterrupt): return
    elif choice_num == manual_idx:
        try:
            fname = ask("File name", "custom_ranges.txt")
            if not fname.endswith(".txt"): fname += ".txt"
            fpath = os.path.join(os.path.dirname(os.path.abspath(__file__)), fname)
            print(f"\n  {D}Paste IPs/ranges (Ctrl+D to finish):{X}\n"); lines = []
            old_sig = signal.signal(signal.SIGTSTP, signal.SIG_IGN) if hasattr(signal,"SIGTSTP") else None
            try:
                while True:
                    try:
                        line = input()
                        if line.strip(): lines.append(line.strip())
                    except EOFError: break
            finally:
                if old_sig is not None: signal.signal(signal.SIGTSTP, old_sig)
            for raw_line in lines:
                for tok in re.split(r'[\s,;]+', raw_line):
                    tok = tok.strip()
                    if not tok or tok.startswith('#'): continue
                    result = _parse_token(tok)
                    if result is None: continue
                    val, is_cidr = result
                    if not is_cidr: val = val + '/32'
                    try:
                        ipaddress.ip_network(val, strict=False)
                        if val not in ranges: ranges.append(val)
                    except ValueError: pass
            if ranges:
                with open(fpath, "w") as f:
                    f.write(f"# Custom ranges\n# Generated: {datetime.now().isoformat()}\n")
                    for r in ranges: f.write(f"{r}\n")
                source_file = fname
                print(f"\n  {G}✓ Saved: {fname}  ({len(ranges)} ranges){X}")
        except (EOFError, KeyboardInterrupt): return

    if ranges:
        global USER_LOADED_RANGES
        USER_LOADED_RANGES = ranges
        total_ips = _count_ips_in_ranges(ranges)
        print(f"  {G}✓ {len(ranges):,} ranges ready  ~{total_ips:,} IPs{X}")
        if source_file: print(f"  {D}Source: {source_file}{X}")


# ── Scan history ───────────────────────────────────────────────────────────────
def show_last_scan():
    _clr()
    if not SCAN_HISTORY:
        _draw_box("  Scan History  ", ["No results stored yet."])
        try: input(f"\n  {D}[ Enter to return ]{X}")
        except (EOFError, KeyboardInterrupt): pass
        return
    _SH_W = _box_width(min_w=36, max_w=50, content_w=40)
    print(f"  {W}{B}{'Scan History (last ' + str(len(SCAN_HISTORY)) + ')':^{_SH_W}}{X}")
    for i, s in enumerate(SCAN_HISTORY):
        ts_parts = s["timestamp"].split(" ")
        date_part = ts_parts[0]; ts_short = ts_parts[1] if len(ts_parts)>1 else ts_parts[0]
        cnt = len(s["healthy"]); marker = f"{G}●{X}" if i==0 else f"{D}·{X}"
        print(f"    {marker} {D}{date_part} {ts_short:<8}{X}  {G}{B}{cnt:>2} IPs{X}  {D}{s['source'][:16]}{X}")
    s = SCAN_HISTORY[0] if len(SCAN_HISTORY)==1 else None
    if len(SCAN_HISTORY) > 1:
        print(f"\n  {D}Enter 1–{len(SCAN_HISTORY)} for detail, blank = latest:{X}")
        try:
            sys.stdout.write("  > "); sys.stdout.flush()
            raw = input("").strip()
            s = SCAN_HISTORY[max(0,min(int(raw)-1,len(SCAN_HISTORY)-1))] if raw else SCAN_HISTORY[0]
        except (ValueError, EOFError, KeyboardInterrupt): s = SCAN_HISTORY[0]
    print(f"\n  {C}{B}{s['timestamp']}{X}  {D}{s['source']}{X}")
    healthy = s["healthy"]
    _DT_W = _box_width(min_w=34, max_w=50, content_w=32); SEP = "─"*_DT_W
    print(f"\n  {D}{SEP}{X}\n  {D}{'IP':<18}  {'ms':>6}  code{X}\n  {D}{SEP}{X}")
    for r in healthy:
        col = _ip_color(r["code"]); ms_col = G if r["ms"]<200 else Y if r["ms"]<400 else R
        print(f"  {col}{B}{r['ip']:<18}{X}  {ms_col}{r['ms']:>5}ms{X}  {col}{r['code']}{X}")
    print(f"  {D}{SEP}{X}\n  {G}{B}✓ {len(healthy)} healthy IP(s){X}")
    _copy_to_clipboard([r["ip"] for r in healthy])
    try: input(f"\n  {D}[ Enter to return ]{X}")
    except (EOFError, KeyboardInterrupt): pass

def do_reset_learned():
    _clr(); learned = get_learned_entries(); count = len(learned)
    _draw_box("  Reset Learned IPs  ", [])
    if not count:
        print(f"  {D}Learned list is already empty.{X}")
        try: input(f"\n  {D}[ Enter to return ]{X}")
        except (EOFError, KeyboardInterrupt): pass
        return
    print(f"  {Y}Found {count} learned IP(s):{X}")
    for e in learned[:10]:
        print(f"    {D}·{X} {e['ip']:<16} {D}{e.get('ts','')}{X}")
    if count > 10: print(f"    {D}… and {count-10} more{X}")
    print()
    if _ask_yn(f"Delete all {count} learned IP(s)?"):
        clear_learned(); print(f"\n  {G}✓ Learned IPs cleared.{X}")
    else: print(f"\n  {D}Cancelled — nothing changed.{X}")
    try: input(f"\n  {D}[ Enter to return ]{X}")
    except (EOFError, KeyboardInterrupt): pass


# ── Menu ───────────────────────────────────────────────────────────────────────
def _draw_menu_static() -> list:
    c = SCAN_CFG; ka_on = keepalive.is_enabled
    loaded_n = len(USER_LOADED_RANGES)
    _ts = _last_scan(); last_ts = (_ts["timestamp"].split(" ")[1] if _ts else None)
    _lrn_count = len(_load_learned().get("ips", []))
    _upd_ages = [a for a in [_range_update_age_days(cdn) for cdn in _RANGE_SOURCES] if a is not None]
    upd_badge = "never" if not _upd_ages else ("today" if max(_upd_ages)<1 else f"{max(_upd_ages):.0f}d ago")
    upd_col = R if not _upd_ages else (G if max(_upd_ages)<7 else Y if max(_upd_ages)<30 else R)
    sep = "─" * 28

    def _row(key, label, badge="", bc=D, kc=C, lc=W):
        badge_s = f"  {bc}{badge}{X}" if badge else ""
        return f"  {kc}[{key}]{X}  {lc}{label}{X}{badge_s}"

    return ["", f"  {C}{B}CDN-SCANNER  v2{X}", f"  {D}Psiphon · ShirOKhorshid{X}",
            f"  {D}{sep}{X}", f"  {Y}SCAN{X}", f"  {D}{sep}{X}",
            _row("1","Single IP / List"), _row("2","Custom CIDR Range"),
            _row("3","Full CDN List"), _row("4","Smart Scan","auto",Y,O,G),
            f"  {D}{sep}{X}", f"  {Y}MANAGE{X}", f"  {D}{sep}{X}",
            _row("L","Load Ranges",   f"{loaded_n}rng" if loaded_n else "-", G if loaded_n else D),
            _row("U","Update Ranges", upd_badge, upd_col),
            _row("R","Scan History",  last_ts or "-", G if last_ts else D),
            _row("X","Reset Learned", f"{_lrn_count}ip" if _lrn_count else "-", Y if _lrn_count else D),
            f"  {D}{sep}{X}", f"  {Y}CONFIG{X}", f"  {D}{sep}{X}",
            _row("S","Settings",     f"c{c['concurrency']} t{c['timeout']}s", D),
            _row("A","Anti-throttle", "ON" if ka_on else "OFF", G if ka_on else R),
            _row("W","Web UI",        "browser", C),
            f"  {D}{sep}{X}", f"  {D}[Q]  Quit{X}", f"  {D}{sep}{X}"]

def menu():
    _clr(); lines = _draw_menu_static()
    for ln in lines: print(ln)
    VALID = {"1","2","3","4","l","u","r","a","s","w","x","q"}

    def _show_prompt(err=""):
        if err: print(f"\n  {R}✗{X}  {err}")
        sys.stdout.write(f"\n  {C}>{X} "); sys.stdout.flush()

    _show_prompt()
    while True:
        try: raw = input("").strip().lower()
        except EOFError: raw = ""
        except KeyboardInterrupt: print(); return
        if raw in VALID: break
        _clr()
        for ln in lines: print(ln)
        _show_prompt("Invalid choice — use 1 2 3 4  or  L U R A S W X Q")

    mode = raw; _clr()
    if mode == "q":
        print(f"\n\n  {C}{B}ShirOKhorshid Scanner{X}")
        print(f"  {D}Goodbye.{X}"); keepalive.stop(); sys.exit(0)
    if mode == "s": show_settings(); return
    if mode == "a":
        new_state = keepalive.toggle()
        print(f"\n  Anti-throttle {f'{G}{B}ENABLED{X}' if new_state else f'{R}DISABLED{X}'}"); return
    if mode == "l": load_ranges(); return
    if mode == "u": show_update_ranges(); return
    if mode == "x": do_reset_learned(); return
    if mode == "r": show_last_scan(); return
    if mode == "w": start_web_ui(); return
    if mode == "4":
        try: run_smart_scan()
        except KeyboardInterrupt: print(f"\n  {Y}Cancelled.{X}"); time.sleep(0.5)
        return

    host, retries, concurrency, timeout, out_file = _get_cfg()
    ips = []; source_label = "manual"; ranges_used = []
    try:
        if mode == "1":
            ips = input_manual_ips(); source_label = "Single IPs"
            if ips: ranges_used = [f"{ip}/32" for ip in ips]
        elif mode == "2":
            cidrs = input_cidr_manual()
            if cidrs:
                ranges_used = cidrs; source_label = "CIDR manual"
                sample = ask_int("Max IPs to scan (0 = all)", 5000) or 999999
                ips = expand_cidrs(cidrs, sample)
                _show_scan_preview(len(ips), concurrency, timeout, source="CIDR manual")
        elif mode == "3":
            _clr(); _MW3 = _box_width(min_w=36, max_w=50)
            _learned_now = get_learned_entries()
            _my_pool = list(dict.fromkeys(list(reversed(get_learned_ranges())) + list(USER_LOADED_RANGES)))
            my_count = len(_my_pool)
            print(f"  {W}{B}Full CDN Scan{X}\n  {D}Enter numbers (e.g. 1 2)  blank=cancel{X}\n")
            provider_list = ["Akamai","Fastly","Cloudflare","Google","CloudFront","Gcore"]
            for i, provider in enumerate(provider_list, 1):
                total_ips = _count_ips_in_ranges(CDN_RANGES.get(provider,[]))
                ip_col = G if total_ips < 5_000_000 else Y
                print(f"    {C}{B}[{i}]{X}  {W}{B}{provider:<8}{X}  "
                      f"{D}{len(CDN_RANGES.get(provider,[]))} ranges{X}  {ip_col}~{total_ips:,} IPs{X}")
            my_col = G if my_count else D
            print(f"    {Y}{B}[4]{X}  {Y}{B}My Range{X}  {D}learned+loaded{X}  {my_col}{my_count} IPs{X}")
            lrn_count = len(_learned_now)
            print(f"    {C}{B}[5]{X}  {C}{B}Learned IPs{X}  "
                  f"{G if lrn_count else D}{lrn_count} IPs{X}\n")
            try:
                sys.stdout.write("  > "); sys.stdout.flush()
                raw = input("").replace(",", " ").split()
            except (EOFError, KeyboardInterrupt): raw = []
            cidrs = []
            for s in raw:
                try:
                    i = int(s) - 1
                    if i in (3, 4):
                        entries = _learned_now if i==4 else _learned_now
                        if not entries: print(f"  {R}No IPs found.{X}"); continue
                        for e in entries:
                            try:
                                broad = ipaddress.ip_network(f"{e['ip']}/24", strict=False)
                                cidrs.append(str(broad))
                            except Exception: cidrs.append(f"{e['ip']}/32")
                    elif 0 <= i < len(provider_list):
                        pool = CDN_RANGES[provider_list[i]]
                        picked = random.sample(pool, min(5, len(pool)))
                        for r in picked: cidrs.append(r)
                except (ValueError, IndexError): pass
            if cidrs:
                ranges_used = cidrs; source_label = "Full CDN"
                sample = ask_int("Max IPs to scan (0 = all)", 5000) or 999999
                ips = expand_cidrs(cidrs, sample)
                _show_scan_preview(len(ips), concurrency, timeout, source=source_label)
    except KeyboardInterrupt:
        print(f"\n  {Y}Cancelled.{X}"); time.sleep(0.5); return

    if ips:
        random.shuffle(ips)
        run_scan(ips, host, 443, "/", concurrency, timeout, out_file, retries,
                 source_label=source_label, ranges_used=ranges_used)
    else:
        print(f"\n  {R}No IPs loaded.{X}")
        try: input(f"\n  {D}[ Enter to return ]{X}")
        except (EOFError, KeyboardInterrupt): pass


def _self_install():
    script_path = os.path.abspath(__file__)
    prefix = os.environ.get("PREFIX", "/data/data/com.termux/files/usr")
    bin_dir = os.path.join(prefix, "bin"); cmd_path = os.path.join(bin_dir, "cdnscanner")
    flag_file = os.path.join(os.path.dirname(script_path), ".cdnscanner_installed")
    if os.path.isfile(flag_file): return
    try:
        if not os.path.isdir(bin_dir): return
        if os.path.lexists(cmd_path): os.remove(cmd_path)
        os.chmod(script_path, 0o755); os.symlink(script_path, cmd_path)
        with open(flag_file, "w"): pass
        print(f"\n  {G}✓ Installed:{X}  type {C}{B}cdnscanner{X} anywhere to launch")
        time.sleep(1.2)
    except Exception: pass


# ══════════════════════════════════════════════════════════════════════════════
#  WEB DASHBOARD
# ══════════════════════════════════════════════════════════════════════════════
_WEB_STATE = {"running":False,"pct":0,"scanned":0,"healthy":0,"failed":0,
              "results":[],"log":[],"source":"","server":None,"live_ips":[]}
_WEB_LOCK = threading.Lock()

def _web_log(msg: str):
    with _WEB_LOCK:
        _WEB_STATE["log"].append(msg)
        if len(_WEB_STATE["log"]) > 200: _WEB_STATE["log"] = _WEB_STATE["log"][-200:]

def _web_get_local_ip() -> str:
    try:
        with socket.socket(socket.AF_INET, socket.SOCK_DGRAM) as s:
            s.connect(("8.8.8.8", 80)); return s.getsockname()[0]
    except Exception: return "127.0.0.1"

def _web_free_port() -> int:
    for _ in range(200):
        p = random.randint(8100, 9900)
        try:
            with socket.socket() as s: s.bind(("0.0.0.0", p))
            return p
        except OSError: pass
    with socket.socket() as s: s.bind(("0.0.0.0", 0)); return s.getsockname()[1]

# Minimal embedded web UI (external HTML served)
_WEB_HTML = """
<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<title>CDN Hunter</title>
<style>
*,*::before,*::after{box-sizing:border-box;margin:0;padding:0}
body{font-family:'Inter',system-ui,-apple-system,sans-serif;background:#000;color:#e5e5e5;min-height:100vh;overflow-x:hidden}
a{color:inherit;text-decoration:none}

.sidebar{position:fixed;top:0;left:0;width:260px;height:100vh;background:#0a0a0a;border-right:1px solid #222;display:flex;flex-direction:column;z-index:1000;transition:transform 0.3s ease}
.sidebar-header{padding:20px;border-bottom:1px solid #222;display:flex;align-items:center;gap:10px}
.sidebar-logo{width:32px;height:32px;background:linear-gradient(135deg,#3b82f6,#8b5cf6);border-radius:8px;display:flex;align-items:center;justify-content:center;font-weight:700;font-size:14px;color:#fff}
.sidebar-title{font-size:15px;font-weight:600;color:#fafafa}
.sidebar-nav{flex:1;padding:12px;overflow-y:auto}
.nav-item{display:flex;align-items:center;gap:10px;padding:10px 12px;border-radius:8px;cursor:pointer;font-size:13px;color:#999;transition:all 0.15s;margin-bottom:2px}
.nav-item:hover{background:#171717;color:#e5e5e5}
.nav-item.active{background:#171717;color:#fff;font-weight:500}
.nav-item .badge{margin-left:auto;background:#222;color:#999;font-size:11px;padding:2px 7px;border-radius:10px;font-weight:500}
.nav-item.active .badge{background:#3b82f6;color:#fff}
.sidebar-footer{padding:16px;border-top:1px solid #222;display:flex;flex-direction:column;gap:8px}
.btn{padding:10px 16px;border:none;border-radius:8px;font-size:13px;font-weight:500;cursor:pointer;display:flex;align-items:center;justify-content:center;gap:6px;transition:all 0.15s}
.btn-primary{background:#3b82f6;color:#fff}
.btn-primary:hover{background:#2563eb}
.btn-danger{background:#1c1c1c;color:#ef4444;border:1px solid #333}
.btn-danger:hover{background:#1a0a0a;border-color:#ef4444}
.btn-ghost{background:transparent;color:#999;border:1px solid #333}
.btn-ghost:hover{background:#171717;color:#fff}

.topbar{position:fixed;top:0;left:260px;right:0;height:56px;background:#0a0a0a;border-bottom:1px solid #222;display:flex;align-items:center;padding:0 20px;z-index:900;gap:12px}
.hamburger{display:none;background:none;border:none;color:#e5e5e5;font-size:20px;cursor:pointer;padding:8px}
.topbar-title{font-size:14px;font-weight:500;color:#fafafa}
.topbar-status{margin-left:8px;display:flex;align-items:center;gap:6px;font-size:12px;color:#666}
.status-dot{width:8px;height:8px;border-radius:50%;background:#333}
.status-dot.running{background:#22c55e;box-shadow:0 0 6px #22c55e}
.topbar-actions{margin-left:auto;display:flex;gap:8px;align-items:center}

.main{margin-left:260px;margin-top:56px;padding:20px;min-height:calc(100vh - 56px)}

.stats-strip{display:grid;grid-template-columns:repeat(auto-fit,minmax(130px,1fr));gap:12px;margin-bottom:20px}
.stat-card{background:#0a0a0a;border:1px solid #222;border-radius:10px;padding:14px 16px}
.stat-label{font-size:11px;color:#666;text-transform:uppercase;letter-spacing:0.5px;margin-bottom:4px}
.stat-value{font-size:20px;font-weight:600;color:#fafafa;font-family:'JetBrains Mono',monospace}
.stat-value.green{color:#22c55e}
.stat-value.red{color:#ef4444}
.stat-value.blue{color:#3b82f6}

.progress-bar-container{margin-bottom:20px;background:#111;border-radius:6px;height:6px;overflow:hidden}
.progress-bar-fill{height:100%;background:linear-gradient(90deg,#3b82f6,#8b5cf6);border-radius:6px;transition:width 0.3s ease;width:0%}

.tab-bar{display:flex;gap:0;border-bottom:1px solid #222;margin-bottom:20px;overflow-x:auto}
.tab-btn{padding:10px 18px;background:none;border:none;color:#666;font-size:13px;cursor:pointer;border-bottom:2px solid transparent;transition:all 0.15s;white-space:nowrap}
.tab-btn:hover{color:#ccc}
.tab-btn.active{color:#fff;border-bottom-color:#3b82f6}

.tab-content{display:none}
.tab-content.active{display:block}

.live-feed{max-height:400px;overflow-y:auto;background:#0a0a0a;border:1px solid #222;border-radius:10px;padding:12px}
.live-item{padding:8px 12px;border-bottom:1px solid #111;font-family:'JetBrains Mono',monospace;font-size:12px;color:#22c55e;display:flex;align-items:center;gap:8px}
.live-item:last-child{border-bottom:none}
.live-dot{width:6px;height:6px;border-radius:50%;background:#22c55e;flex-shrink:0}
.empty-state{text-align:center;padding:60px 20px;color:#444;font-size:14px}

.results-table{width:100%;border-collapse:collapse;font-size:12px}
.results-table th{text-align:left;padding:10px 12px;color:#666;font-weight:500;border-bottom:1px solid #222;font-size:11px;text-transform:uppercase;letter-spacing:0.5px}
.results-table td{padding:10px 12px;border-bottom:1px solid #111;color:#ccc;font-family:'JetBrains Mono',monospace}
.results-table tr:hover{background:#0a0a0a}
.status-ok{color:#22c55e}
.status-fail{color:#ef4444}

.fronting-group{margin-bottom:16px;background:#0a0a0a;border:1px solid #222;border-radius:10px;padding:16px}
.fronting-title{font-size:13px;font-weight:600;color:#fafafa;margin-bottom:10px;display:flex;align-items:center;gap:8px}
.fronting-ips{display:flex;flex-wrap:wrap;gap:6px}
.fronting-ip{background:#171717;border:1px solid #333;padding:4px 10px;border-radius:6px;font-size:12px;font-family:'JetBrains Mono',monospace;color:#ccc}

.config-form{max-width:600px}
.form-group{margin-bottom:16px}
.form-label{display:block;font-size:12px;color:#999;margin-bottom:6px;font-weight:500}
.form-input,.form-select{width:100%;padding:10px 14px;background:#0a0a0a;border:1px solid #222;border-radius:8px;color:#e5e5e5;font-size:13px;font-family:inherit;outline:none;transition:border-color 0.15s}
.form-input:focus,.form-select:focus{border-color:#3b82f6}
.form-row{display:grid;grid-template-columns:1fr 1fr;gap:12px}

.log-container{max-height:500px;overflow-y:auto;background:#0a0a0a;border:1px solid #222;border-radius:10px;padding:12px;font-family:'JetBrains Mono',monospace;font-size:12px}
.log-entry{padding:4px 0;color:#999;border-bottom:1px solid #0d0d0d}
.log-entry:last-child{border-bottom:none}

.toast{position:fixed;bottom:20px;right:20px;background:#171717;border:1px solid #333;border-radius:10px;padding:12px 20px;color:#fafafa;font-size:13px;z-index:9999;opacity:0;transform:translateY(10px);transition:all 0.3s;pointer-events:none}
.toast.show{opacity:1;transform:translateY(0)}

.overlay{display:none;position:fixed;top:0;left:0;right:0;bottom:0;background:rgba(0,0,0,0.6);z-index:999}

@media(max-width:768px){
  .sidebar{transform:translateX(-100%)}
  .sidebar.open{transform:translateX(0)}
  .overlay.open{display:block}
  .topbar{left:0}
  .hamburger{display:block}
  .main{margin-left:0}
  .form-row{grid-template-columns:1fr}
  .stats-strip{grid-template-columns:repeat(2,1fr)}
}
</style>
</head>
<body>

<div class="overlay" id="overlay"></div>

<aside class="sidebar" id="sidebar">
  <div class="sidebar-header">
    <div class="sidebar-logo">CH</div>
    <div class="sidebar-title">CDN Hunter</div>
  </div>
  <nav class="sidebar-nav">
    <div class="nav-item active" id="nav-overview" data-tab="overview">
      <span>Overview</span>
      <span class="badge" id="badge-live">0</span>
    </div>
    <div class="nav-item" id="nav-results" data-tab="results">
      <span>Results</span>
      <span class="badge" id="badge-results">0</span>
    </div>
    <div class="nav-item" id="nav-fronting" data-tab="fronting">
      <span>Fronting IPs</span>
      <span class="badge" id="badge-fronting">0</span>
    </div>
    <div class="nav-item" id="nav-config" data-tab="config">
      <span>Scan Config</span>
    </div>
    <div class="nav-item" id="nav-log" data-tab="log">
      <span>Activity Log</span>
    </div>
  </nav>
  <div class="sidebar-footer">
    <button class="btn btn-primary" id="sidebar-start" type="button">Start Scan</button>
    <button class="btn btn-danger" id="sidebar-stop" type="button">Stop Scan</button>
  </div>
</aside>

<header class="topbar">
  <button class="hamburger" id="hamburger" type="button">&#9776;</button>
  <span class="topbar-title">CDN Hunter</span>
  <span class="topbar-status">
    <span class="status-dot" id="statusDot"></span>
    <span id="statusText">Idle</span>
  </span>
  <div class="topbar-actions">
    <button class="btn btn-ghost" id="copyIpsBtn" type="button">Copy IPs</button>
    <button class="btn btn-danger" id="exitBtn" type="button">Exit</button>
  </div>
</header>

<main class="main">
  <div class="stats-strip">
    <div class="stat-card"><div class="stat-label">Scanned</div><div class="stat-value" id="statScanned">0</div></div>
    <div class="stat-card"><div class="stat-label">Healthy</div><div class="stat-value green" id="statHealthy">0</div></div>
    <div class="stat-card"><div class="stat-label">Failed</div><div class="stat-value red" id="statFailed">0</div></div>
    <div class="stat-card"><div class="stat-label">Progress</div><div class="stat-value blue" id="statPct">0%</div></div>
    <div class="stat-card"><div class="stat-label">Source</div><div class="stat-value" id="statSource">-</div></div>
  </div>

  <div class="progress-bar-container">
    <div class="progress-bar-fill" id="progressBar"></div>
  </div>

  <div class="tab-bar">
    <button class="tab-btn active" data-tab="overview" type="button">Overview</button>
    <button class="tab-btn" data-tab="results" type="button">Results</button>
    <button class="tab-btn" data-tab="fronting" type="button">Fronting</button>
    <button class="tab-btn" data-tab="config" type="button">Config</button>
    <button class="tab-btn" data-tab="log" type="button">Log</button>
  </div>

  <div class="tab-content active" id="tab-overview">
    <div class="live-feed" id="liveFeed">
      <div class="empty-state">No live IPs yet. Start a scan to see results.</div>
    </div>
  </div>

  <div class="tab-content" id="tab-results">
    <div style="overflow-x:auto">
      <table class="results-table">
        <thead>
          <tr><th>IP</th><th>Latency</th><th>Code</th><th>CDN</th><th>Country</th><th>kB/s</th><th>Status</th></tr>
        </thead>
        <tbody id="resultsBody">
          <tr><td colspan="7" style="text-align:center;color:#444;padding:40px">No results yet</td></tr>
        </tbody>
      </table>
    </div>
  </div>

  <div class="tab-content" id="tab-fronting">
    <div id="frontingContainer">
      <div class="empty-state">No fronting data available yet.</div>
    </div>
  </div>

  <div class="tab-content" id="tab-config">
    <div class="config-form">
      <div class="form-group">
        <label class="form-label">CDN Provider</label>
        <select class="form-select" id="cfgCdn">
          <option value="smart">Smart (Auto-detect)</option>
          <option value="cloudflare">Cloudflare</option>
          <option value="akamai">Akamai</option>
          <option value="fastly">Fastly</option>
          <option value="google">Google</option>
          <option value="cloudfront">CloudFront</option>
          <option value="gcore">Gcore</option>
          <option value="all">All CDNs</option>
        </select>
      </div>
      <div class="form-group">
        <label class="form-label">Host Header</label>
        <input class="form-input" id="cfgHost" type="text" placeholder="e.g. speed.cloudflare.com">
      </div>
      <div class="form-group">
        <label class="form-label">SNI (Server Name Indication)</label>
        <input class="form-input" id="cfgSni" type="text" placeholder="e.g. my.sni.domain.com">
      </div>
      <div class="form-row">
        <div class="form-group">
          <label class="form-label">Concurrency</label>
          <input class="form-input" id="cfgConc" type="number" value="60" min="1" max="500">
        </div>
        <div class="form-group">
          <label class="form-label">Timeout (sec)</label>
          <input class="form-input" id="cfgTimeout" type="number" value="4" min="1" max="30" step="0.5">
        </div>
      </div>
      <div class="form-row">
        <div class="form-group">
          <label class="form-label">Max IPs (Sample)</label>
          <input class="form-input" id="cfgSample" type="number" value="3000" min="10" max="100000">
        </div>
        <div class="form-group">
          <label class="form-label">Retries</label>
          <input class="form-input" id="cfgRetries" type="number" value="2" min="0" max="10">
        </div>
      </div>
      <div class="form-group">
        <label class="form-label">Save Results To File</label>
        <input class="form-input" id="cfgOutFile" type="text" placeholder="e.g. results.txt (optional)">
      </div>
      <div style="margin-top:20px">
        <button class="btn btn-primary" id="configStartBtn" type="button">Start Scan</button>
      </div>
    </div>
  </div>

  <div class="tab-content" id="tab-log">
    <div class="log-container" id="logContainer">
      <div class="log-entry" style="color:#444">Waiting for activity...</div>
    </div>
  </div>
</main>

<div class="toast" id="toast"></div>

<script>
var pollTimer = null;
var scanStartTime = null;
var lastState = {};

function $(id) { return document.getElementById(id); }

function showToast(msg) {
  var t = $('toast');
  t.textContent = msg;
  t.className = 'toast show';
  setTimeout(function() { t.className = 'toast'; }, 3000);
}

function switchTab(tabName) {
  var contents = document.querySelectorAll('.tab-content');
  var buttons = document.querySelectorAll('.tab-btn');
  var navItems = document.querySelectorAll('.nav-item');
  var i;
  for (i = 0; i < contents.length; i++) {
    contents[i].className = 'tab-content';
  }
  for (i = 0; i < buttons.length; i++) {
    buttons[i].className = 'tab-btn';
  }
  for (i = 0; i < navItems.length; i++) {
    navItems[i].className = 'nav-item';
  }
  var target = document.getElementById('tab-' + tabName);
  if (target) target.className = 'tab-content active';
  for (i = 0; i < buttons.length; i++) {
    if (buttons[i].getAttribute('data-tab') === tabName) {
      buttons[i].className = 'tab-btn active';
    }
  }
  for (i = 0; i < navItems.length; i++) {
    if (navItems[i].getAttribute('data-tab') === tabName) {
      navItems[i].className = 'nav-item active';
    }
  }
  closeSidebar();
}

function openSidebar() {
  $('sidebar').className = 'sidebar open';
  $('overlay').className = 'overlay open';
}

function closeSidebar() {
  $('sidebar').className = 'sidebar';
  $('overlay').className = 'overlay';
}

function startScan() {
  var cfg = {
    cdn: $('cfgCdn').value,
    host: $('cfgHost').value,
    sni: $('cfgSni').value,
    conc: parseInt($('cfgConc').value) || 60,
    timeout: parseFloat($('cfgTimeout').value) || 4,
    sample: parseInt($('cfgSample').value) || 3000,
    retries: parseInt($('cfgRetries').value) || 2,
    out_file: $('cfgOutFile').value
  };
  var xhr = new XMLHttpRequest();
  xhr.open('POST', '/api/start', true);
  xhr.setRequestHeader('Content-Type', 'application/json');
  xhr.onreadystatechange = function() {
    if (xhr.readyState === 4) {
      try {
        var resp = JSON.parse(xhr.responseText);
        if (resp.ok) {
          scanStartTime = Date.now();
          showToast('Scan started');
        } else {
          showToast(resp.msg || 'Failed to start');
        }
      } catch(e) {
        showToast('Error starting scan');
      }
    }
  };
  xhr.send(JSON.stringify(cfg));
}

function stopScan() {
  var xhr = new XMLHttpRequest();
  xhr.open('POST', '/api/stop', true);
  xhr.onreadystatechange = function() {
    if (xhr.readyState === 4) {
      showToast('Scan stopped');
    }
  };
  xhr.send('');
}

function exitApp() {
  if (confirm('Exit CDN Hunter?')) {
    var xhr = new XMLHttpRequest();
    xhr.open('POST', '/api/exit', true);
    xhr.send('');
    showToast('Shutting down...');
  }
}

function copyIps() {
  var results = lastState.results || [];
  var ips = [];
  var i;
  for (i = 0; i < results.length; i++) {
    if (results[i].ok) {
      ips.push(results[i].ip);
    }
  }
  if (ips.length === 0) {
    showToast('No healthy IPs to copy');
    return;
  }
  var text = ips.join('\\n');
  if (navigator.clipboard && navigator.clipboard.writeText) {
    navigator.clipboard.writeText(text).then(function() {
      showToast(ips.length + ' IPs copied');
    });
  } else {
    var ta = document.createElement('textarea');
    ta.value = text;
    document.body.appendChild(ta);
    ta.select();
    document.execCommand('copy');
    document.body.removeChild(ta);
    showToast(ips.length + ' IPs copied');
  }
}

function updateUI(state) {
  lastState = state;

  $('statScanned').textContent = state.scanned || 0;
  $('statHealthy').textContent = state.healthy || 0;
  $('statFailed').textContent = state.failed || 0;
  $('statPct').textContent = (state.pct || 0) + '%';
  $('statSource').textContent = state.source || '-';
  $('progressBar').style.width = (state.pct || 0) + '%';

  var dot = $('statusDot');
  var txt = $('statusText');
  if (state.running) {
    dot.className = 'status-dot running';
    txt.textContent = 'Scanning...';
  } else {
    dot.className = 'status-dot';
    txt.textContent = 'Idle';
  }

  var liveFeed = $('liveFeed');
  var liveIps = state.live_ips || [];
  $('badge-live').textContent = state.live_ips_total || liveIps.length;
  if (liveIps.length > 0) {
    var html = '';
    var i;
    for (i = liveIps.length - 1; i >= 0; i--) {
      var ip = typeof liveIps[i] === 'string' ? liveIps[i] : (liveIps[i].ip || liveIps[i]);
      html += '<div class="live-item"><span class="live-dot"></span>' + escHtml(String(ip)) + '</div>';
    }
    liveFeed.innerHTML = html;
  } else if (!state.running && liveIps.length === 0) {
    liveFeed.innerHTML = '<div class="empty-state">No live IPs yet. Start a scan to see results.</div>';
  }

  var results = state.results || [];
  $('badge-results').textContent = results.length;
  if (results.length > 0) {
    var rhtml = '';
    var j;
    for (j = 0; j < results.length; j++) {
      var r = results[j];
      var statusCls = r.ok ? 'status-ok' : 'status-fail';
      var statusTxt = r.ok ? 'OK' : 'FAIL';
      rhtml += '<tr><td>' + escHtml(r.ip) + '</td><td>' + r.ms + 'ms</td><td>' + escHtml(String(r.code)) + '</td><td>' + escHtml(r.cdn || '?') + '</td><td>' + escHtml(r.country || '-') + '</td><td>' + (r.kbps || 0) + '</td><td class="' + statusCls + '">' + statusTxt + '</td></tr>';
    }
    $('resultsBody').innerHTML = rhtml;
  }

  var frontingIps = [];
  var k;
  for (k = 0; k < results.length; k++) {
    if (results[k].ok && results[k].fronting_ok) {
      frontingIps.push(results[k]);
    }
  }
  $('badge-fronting').textContent = frontingIps.length;
  if (frontingIps.length > 0) {
    var groups = {};
    var m;
    for (m = 0; m < frontingIps.length; m++) {
      var cdn = frontingIps[m].cdn || 'Unknown';
      if (!groups[cdn]) groups[cdn] = [];
      groups[cdn].push(frontingIps[m].ip);
    }
    var fhtml = '';
    for (var gkey in groups) {
      if (groups.hasOwnProperty(gkey)) {
        fhtml += '<div class="fronting-group"><div class="fronting-title">' + escHtml(gkey) + ' <span class="badge">' + groups[gkey].length + '</span></div><div class="fronting-ips">';
        var n;
        for (n = 0; n < groups[gkey].length; n++) {
          fhtml += '<span class="fronting-ip">' + escHtml(groups[gkey][n]) + '</span>';
        }
        fhtml += '</div></div>';
      }
    }
    $('frontingContainer').innerHTML = fhtml;
  }

  var logs = state.log || [];
  if (logs.length > 0) {
    var lhtml = '';
    var p;
    for (p = logs.length - 1; p >= 0; p--) {
      lhtml += '<div class="log-entry">' + escHtml(logs[p]) + '</div>';
    }
    $('logContainer').innerHTML = lhtml;
  }
}

function escHtml(s) {
  var div = document.createElement('div');
  div.appendChild(document.createTextNode(s));
  return div.innerHTML;
}

function poll() {
  var xhr = new XMLHttpRequest();
  xhr.open('GET', '/api/state', true);
  xhr.onreadystatechange = function() {
    if (xhr.readyState === 4 && xhr.status === 200) {
      try {
        var state = JSON.parse(xhr.responseText);
        updateUI(state);
      } catch(e) {}
    }
  };
  xhr.send();
}

function init() {
  var tabBtns = document.querySelectorAll('.tab-btn');
  var i;
  for (i = 0; i < tabBtns.length; i++) {
    tabBtns[i].addEventListener('click', function() {
      switchTab(this.getAttribute('data-tab'));
    });
  }

  var navItems = document.querySelectorAll('.nav-item');
  for (i = 0; i < navItems.length; i++) {
    navItems[i].addEventListener('click', function() {
      var tab = this.getAttribute('data-tab');
      if (tab) switchTab(tab);
    });
  }

  $('hamburger').addEventListener('click', function() {
    var sb = $('sidebar');
    if (sb.className.indexOf('open') !== -1) {
      closeSidebar();
    } else {
      openSidebar();
    }
  });

  $('overlay').addEventListener('click', closeSidebar);
  $('sidebar-start').addEventListener('click', startScan);
  $('sidebar-stop').addEventListener('click', stopScan);
  $('configStartBtn').addEventListener('click', startScan);
  $('copyIpsBtn').addEventListener('click', copyIps);
  $('exitBtn').addEventListener('click', exitApp);

  poll();
  pollTimer = setInterval(poll, 1500);
}

if (document.readyState === 'loading') {
  document.addEventListener('DOMContentLoaded', init);
} else {
  init();
}
</script>
</body>
</html>

"""


class _WebHandler(BaseHTTPRequestHandler):
    def log_message(self, *args): pass

    def _send(self, code: int, ctype: str, body: bytes):
        self.send_response(code); self.send_header("Content-Type", ctype)
        self.send_header("Content-Length", str(len(body)))
        self.send_header("Access-Control-Allow-Origin", "*")
        self.end_headers(); self.wfile.write(body)

    def do_GET(self):
        path = urlparse(self.path).path
        if path in ("/", "/index.html"):
            self._send(200, "text/html; charset=utf-8", _WEB_HTML.encode())
        elif path == "/api/state":
            with _WEB_LOCK:
                live = _WEB_STATE["live_ips"]
                data = {k: _WEB_STATE[k] for k in ("running","pct","scanned","healthy","failed","source","log")}
                data["results"] = _WEB_STATE["results"][-100:]
                data["live_ips_total"] = len(live)
                data["live_ips"] = live[-30:]
            self._send(200, "application/json", json.dumps(data).encode())
        else: self._send(404, "text/plain", b"Not found")

    def do_POST(self):
        path = urlparse(self.path).path
        length = int(self.headers.get("Content-Length", 0))
        body = self.rfile.read(length) if length else b""
        if path == "/api/start":
            if _WEB_STATE.get("running"):
                self._send(200, "application/json", b'{"ok":false,"msg":"already running"}'); return
            try: cfg = json.loads(body) if body else {}
            except Exception: cfg = {}
            threading.Thread(target=_run_scan_web, args=(cfg,), daemon=True).start()
            self._send(200, "application/json", b'{"ok":true}')
        elif path == "/api/stop":
            _STOP_SCAN.set(); _web_log("STOP requested by user")
            self._send(200, "application/json", b'{"ok":true}')
        elif path == "/api/exit":
            self._send(200, "application/json", b'{"ok":true}')
            def _shutdown():
                time.sleep(0.3)
                srv = _WEB_STATE.get("server")
                if srv: srv.shutdown()
            threading.Thread(target=_shutdown, daemon=True).start()
        elif path == "/api/update":
            if _WEB_STATE.get("running"):
                self._send(200, "application/json", b'{"ok":false,"msg":"scan running"}'); return
            try: cfg_u = json.loads(body) if body else {}
            except Exception: cfg_u = {}
            cdns = cfg_u.get("cdns") or list(_RANGE_SOURCES.keys())
            def _do_update():
                _web_log("Updating CDN ranges...")
                for cdn, r in update_cdn_ranges(cdns, verbose=False).items():
                    _web_log(f"{'✓' if r['ok'] else '✗'} {cdn}: {r['count'] if r['ok'] else r['msg']}")
                _web_log("Range update complete")
            threading.Thread(target=_do_update, daemon=True).start()
            self._send(200, "application/json", b'{"ok":true}')
        else: self._send(404, "text/plain", b"Not found")


def _run_scan_web(cfg: dict):
    _STOP_SCAN.clear()
    cdn_choice = cfg.get("cdn", "smart"); sample = int(cfg.get("sample", 3000))
    concurrency = int(cfg.get("conc", 60)); timeout = float(cfg.get("timeout", 4.0))
    retries = int(cfg.get("retries", 2)); host = cfg.get("host", "").strip()
    out_file = cfg.get("out_file","").strip() or None
    if cfg.get("sni","").strip(): SCAN_CFG["psiphon_sni"] = cfg["sni"].strip()

    with _WEB_LOCK:
        _WEB_STATE.update({"running":True,"pct":0,"scanned":0,"healthy":0,"failed":0,
                           "results":[],"log":[],"live_ips":[],"source":cdn_choice})
    _web_log(f"Scan started — CDN={cdn_choice}  conc={concurrency}  timeout={timeout}s")

    ips = []; source = cdn_choice
    try:
        if cdn_choice == "manual":
            tokens = re.split(r'[,\s\n]+', cfg.get("manual","").strip())
            for tok in tokens:
                tok = tok.strip()
                if not tok: continue
                if '/' in tok:
                    try: ips += expand_cidrs([tok], sample)
                    except Exception: pass
                else:
                    ip = _extract_ip_from_token(tok)
                    if ip: ips.append(ip)
            ips = list(dict.fromkeys(ips))[:sample]; source = "Manual"
        elif cdn_choice == "cidr":
            raw_cidrs = [c.strip() for c in re.split(r'[,\s\n]+', cfg.get("cidr","")) if c.strip()]
            ips = expand_cidrs(raw_cidrs, sample); source = "Custom CIDR"
        elif cdn_choice == "smart":
            # ── Smart: probe all CDN ranges + learned, pick best, expand ──
            cdn_ranges_full = dict(CDN_RANGES)
            # learned IPs → broaden to /24 so we scan the whole neighbourhood
            learned_ips = _load_learned().get("ips", [])
            if learned_ips:
                learned_cidrs = []
                for ip in learned_ips[:20]:
                    try:
                        broad = str(ipaddress.ip_network(f"{ip}/24", strict=False))
                        if broad not in learned_cidrs:
                            learned_cidrs.append(broad)
                    except Exception:
                        learned_cidrs.append(f"{ip}/32")
                cdn_ranges_full["Learned"] = learned_cidrs
            if USER_LOADED_RANGES:
                cdn_ranges_full["Loaded"] = list(USER_LOADED_RANGES)

            _web_log("Smart: probing CDN ranges...")
            probe_list = _build_probe_list(cdn_ranges_full, max_ranges_per_cdn=30, ips_per_range=2)
            try:
                if HAS_AIOHTTP:
                    loop = asyncio.new_event_loop()
                    probe_results = loop.run_until_complete(
                        _probe_engine_async(probe_list, min(timeout, 2.0), min(concurrency, 80)))
                    loop.close()
                else:
                    probe_results = _probe_engine_sync(probe_list, min(timeout, 2.0), min(concurrency, 80))
            except Exception as e:
                _web_log(f"Probe error: {e}")
                probe_results = []

            ranked = _aggregate_probe(probe_results)
            good_cidrs = [r["cidr"] for r in ranked[:12]] if ranked else []
            _web_log(f"Smart probe: {len(ranked)} ranges responded, using top {len(good_cidrs)}")

            # fallback: if probe found nothing, use built-in ranges
            if not good_cidrs:
                for cdn_n in ("Akamai", "Fastly", "Cloudflare"):
                    pool_r = CDN_RANGES.get(cdn_n, [])
                    good_cidrs += random.sample(pool_r, min(4, len(pool_r)))

            ips = expand_cidrs(good_cidrs, sample)
            source = "Smart"
        elif cdn_choice == "all":
            pool = []
            for cdn_n, ranges in CDN_RANGES.items():
                pool += expand_cidrs(random.sample(ranges, min(4,len(ranges))), sample//len(CDN_RANGES))
            ips = list(dict.fromkeys(pool))[:sample]; source = "All CDNs"
        elif cdn_choice in ("akamai","fastly","cloudflare","google","cloudfront","gcore"):
            # map lowercase web value → CDN_RANGES key
            key_map = {"akamai":"Akamai","fastly":"Fastly","cloudflare":"Cloudflare",
                       "google":"Google","cloudfront":"CloudFront","gcore":"Gcore"}
            key = key_map.get(cdn_choice, cdn_choice.capitalize())
            ranges = CDN_RANGES.get(key, [])
            ips = expand_cidrs(random.sample(ranges, min(8,len(ranges))), sample)
            source = key
        else:
            ra = CDN_RANGES.get("Akamai",[]); rf = CDN_RANGES.get("Fastly",[])
            ips = expand_cidrs(random.sample(ra,min(5,len(ra))) + random.sample(rf,min(5,len(rf))), sample)
            source = "Akamai+Fastly"

        random.shuffle(ips)
        with _WEB_LOCK: _WEB_STATE["source"] = source
        _web_log(f"{len(ips):,} IPs loaded from {source}")
    except Exception as e:
        _web_log(f"ERR loading IPs: {e}")
        with _WEB_LOCK: _WEB_STATE["running"] = False
        return

    def _progress_watcher():
        while _WEB_STATE.get("running") and not _STOP_SCAN.is_set():
            with _ANIM_LOCK: pct = _ANIM_STATS.get("pct",0); fnd = _ANIM_STATS.get("found",0)
            scanned_est = int(len(ips) * pct / 100) if pct else 0
            with _WEB_LOCK:
                _WEB_STATE.update({"pct":pct,"healthy":fnd,"scanned":scanned_est,"failed":max(scanned_est-fnd,0)})
            time.sleep(0.25)

    watcher = threading.Thread(target=_progress_watcher, daemon=True); watcher.start()
    try:
        result = run_scan(ips, host, 443, "/", concurrency, timeout, out_file, retries,
                          source_label=source, ranges_used=[], _web_mode=True)
    except Exception as e: _web_log(f"Scan error: {e}"); result = []
    finally:
        with _WEB_LOCK: _WEB_STATE["running"] = False

    if not result and _partial_healthy:
        with _partial_healthy_lock: result = list(_partial_healthy)
    results_clean = []
    for r in (result or []):
        if isinstance(r, dict):
            cc = _get_country_code(r.get("ip",""), timeout=2.5) if r.get("ok") else ""
            results_clean.append({"ip": r.get("ip",""), "ms": r.get("ms",9999),
                "code": r.get("code",""), "ok": r.get("ok",False),
                "fronting_ok": r.get("fronting_ok",False),
                "cdn": r.get("cdn_type","?") or "?", "kbps": round(r.get("tp_kbps",0) or 0),
                "country": cc})
            if r.get("ok"): _web_log(f"OK  {r.get('ip','')}  {r.get('ms','')}ms  {cc}")
    with _WEB_LOCK:
        _WEB_STATE.update({"running":False,"pct":100,"scanned":len(ips),
            "healthy":len([r for r in results_clean if r["ok"]]),
            "failed":len([r for r in results_clean if not r["ok"]]),
            "results":results_clean})
    _web_log(f"Done — {len([r for r in results_clean if r['ok']])} healthy / {len(ips)} scanned")


def start_web_ui():
    port = _web_free_port(); local_ip = _web_get_local_ip()
    server = HTTPServer(("0.0.0.0", port), _WebHandler)
    _WEB_STATE["server"] = server
    srv_thread = threading.Thread(target=server.serve_forever, daemon=True); srv_thread.start()
    _clr(); url = f"http://{local_ip}:{port}"
    _MW = _box_width(min_w=36, max_w=52)
    print(f"\n  {W}{B}{'CDN HUNTER  Web Dashboard':^{_MW}}{X}")
    print(f"\n    {D}Local IP :{X}  {G}{B}{local_ip}{X}")
    print(f"    {D}Port     :{X}  {G}{B}{port}{X}")
    print(f"\n    {Y}{B}{url}{X}")
    print(f"\n    {D}1) Open URL in your browser{X}")
    print(f"    {D}2) Press EXIT in web UI or Ctrl+C to stop{X}\n")
    _real_stdout = sys.stdout
    def _write_status(msg): _real_stdout.write(f"\r  {msg}          \r"); _real_stdout.flush()
    _write_status(f"{D}Waiting for browser...  Ctrl+C to stop{X}")
    try:
        while srv_thread.is_alive():
            srv_thread.join(timeout=1.0)
            with _WEB_LOCK:
                running = _WEB_STATE.get("running",False); pct = _WEB_STATE.get("pct",0)
                healthy = _WEB_STATE.get("healthy",0); scanned = _WEB_STATE.get("scanned",0)
            if running: _write_status(f"{Y}Scanning  {pct}%  {G}✓{healthy}  {D}/{scanned}  Ctrl+C to stop{X}")
            elif scanned > 0: _write_status(f"{G}Done  ✓{healthy} healthy  {D}/{scanned} scanned  Ctrl+C to exit{X}")
            else: _write_status(f"{D}Waiting...  {url}  Ctrl+C to stop{X}")
    except KeyboardInterrupt: pass
    _real_stdout.write(f"\n\n  {D}Web server closed.{X}\n"); _real_stdout.flush()
    server.server_close()


# ── Entry point ────────────────────────────────────────────────────────────────
def main():
    _self_install(); ping_learned_ips_background()
    if len(sys.argv) > 1:
        import argparse
        p = argparse.ArgumentParser(prog="cdnscanner", description="CDN-SCANNER v2")
        p.add_argument("cidr", nargs="?"); p.add_argument("--file")
        p.add_argument("--host", default=""); p.add_argument("--concurrency", type=int, default=_DEFAULT_CONCURRENCY)
        p.add_argument("--timeout", type=float, default=_DEFAULT_TIMEOUT); p.add_argument("--retries", type=int, default=2)
        p.add_argument("--out", default=None); p.add_argument("--sample", type=int, default=5000)
        p.add_argument("--keepalive", action="store_true"); a = p.parse_args()
        if a.keepalive: keepalive.enable(); print(f"  {G}Keep-alive enabled{X}")
        if a.file:
            ips = []
            try:
                with open(a.file) as f:
                    for line in f:
                        line = line.strip()
                        if not line or line.startswith("#"): continue
                        for tok in re.split(r'[\s,;]+', line):
                            ip = _extract_ip_from_token(tok)
                            if ip: ips.append(ip)
            except FileNotFoundError: print(f"  {R}File not found: {a.file}{X}"); sys.exit(1)
            ips = list(dict.fromkeys(ips)); random.shuffle(ips)
            run_scan(ips, a.host, 443, "/", a.concurrency, a.timeout, a.out, a.retries)
        elif a.cidr:
            ips = expand_cidrs([a.cidr], a.sample)
            if ips: random.shuffle(ips); run_scan(ips, a.host, 443, "/", a.concurrency, a.timeout, a.out, a.retries)
        else:
            while True:
                _STOP_SCAN.clear()
                try: menu()
                except KeyboardInterrupt: pass
    else:
        while True:
            _STOP_SCAN.clear()
            try: menu()
            except KeyboardInterrupt: pass
    print(f"\n  {D}Goodbye.{X}\n"); keepalive.stop(); sys.exit(0)


if __name__ == "__main__":
    main()
