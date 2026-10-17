"""Local browser QA against a read-only catalog; never a deployed API.

Serves the actual debug HTML assets and a loopback-only identity query adapter.
The browser harness substitutes the Android host. Native coordinator/security
are verified separately by Android tests; this does not certify device behavior.
"""
from __future__ import annotations

import argparse
import functools
import hashlib
import json
import subprocess
import threading
from http.server import HTTPServer, SimpleHTTPRequestHandler
from pathlib import Path

from tools.catalog_discovery.index import DiscoveryIndex, QUERY_VERSION


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--index", required=True, type=Path)
    parser.add_argument("--node", required=True, type=Path)
    parser.add_argument("--modules", required=True, type=Path)
    parser.add_argument("--browser", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    args = parser.parse_args()
    root = Path(__file__).resolve().parents[1]
    assets = root / "android/app/src/debug/assets/valuepilot_next_html"
    args.output.mkdir(parents=True, exist_ok=True)
    with DiscoveryIndex(args.index) as index:
        class Handler(SimpleHTTPRequestHandler):
            def log_message(self, *_args):
                pass

            def do_POST(self):
                if self.path != "/qa/discovery":
                    self.send_error(404)
                    return
                length = int(self.headers.get("Content-Length", "0"))
                if not 0 < length <= 16384:
                    self.send_error(400)
                    return
                try:
                    query = json.loads(self.rfile.read(length))
                    result = index.query(query).to_dict()
                except (ValueError, TypeError) as error:
                    self.send_error(400, str(error))
                    return
                body = json.dumps(result, ensure_ascii=False).encode("utf-8")
                self.send_response(200)
                self.send_header("Content-Type", "application/json; charset=utf-8")
                self.send_header("Content-Length", str(len(body)))
                self.end_headers()
                self.wfile.write(body)

        server = HTTPServer(("127.0.0.1", 0), functools.partial(Handler, directory=str(assets)))
        thread = threading.Thread(target=server.serve_forever, daemon=True)
        thread.start()
        with args.index.open("rb") as handle:
            digest = hashlib.file_digest(handle, "sha256").hexdigest()
        metadata = {
            "environment": "desktop browser with test Android host adapter",
            "deviceVerified": False,
            "queryVersion": QUERY_VERSION,
            "indexSha256": digest,
        }
        (args.output / "input.json").write_text(json.dumps(metadata, indent=2), encoding="utf-8")
        try:
            process = subprocess.run([
                str(args.node), str(root / "tools/verify_catalog_discovery_html.cjs"),
                str(args.modules.resolve()), str(args.browser.resolve()),
                f"http://127.0.0.1:{server.server_port}", str(args.output.resolve()),
            ], check=False, timeout=300)
            return process.returncode
        finally:
            server.shutdown()
            server.server_close()


if __name__ == "__main__":
    raise SystemExit(main())
