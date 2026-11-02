"""Loopback-only fixture for MediaViewerInteractionTests; never calls an upstream API."""
import base64
import json
import struct
import time
import zlib
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import urlsplit


def chunk(kind, data):
    return struct.pack(">I", len(data)) + kind + data + struct.pack(">I", zlib.crc32(kind + data))


image = b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", 7, 11, 8, 2, 0, 0, 0))
image += chunk(b"IDAT", zlib.compress((b"\0" + b"\xff\x32\x32" * 7) * 11)) + chunk(b"IEND", b"")


class Handler(BaseHTTPRequestHandler):
    def log_message(self, *_):
        pass

    def respond(self, status, body):
        data = json.dumps(body).encode()
        self.send_response(status)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(data)))
        self.send_header("X-Request-ID", "offline-fixture-request")
        self.end_headers()
        self.wfile.write(data)

    def do_GET(self):
        if urlsplit(self.path).path == "/v1/models":
            self.respond(200, {"data": [{"id": "offline-fixture"}, {"id": "offline-image-alt"}]})
            return
        if urlsplit(self.path).path == "/v1beta/models":
            self.respond(200, {"models": [{"name": "models/offline-gemini-image"}]})
            return
        self.respond(200 if self.path == "/health" else 404, {"fixture": True})

    def do_POST(self):
        try:
            assert self.path == "/v1/images/edits"
            assert self.headers.get("Authorization") == "Bearer offline-fixture-key"
            size = int(self.headers["Content-Length"])
            assert 0 < size < 1024 * 1024
            body = json.loads(self.rfile.read(size))
            assert body["model"] in ("offline-fixture", "offline-fixture-error") and body["n"] == 1
            original = base64.b64decode(body["images"][0]["image_url"].split(",", 1)[1], validate=True)
            assert original.startswith(b"\x89PNG\r\n\x1a\n")
            width, height = struct.unpack(">II", original[16:24])
            assert width in (320, 640, 960) and width * 3 == height * 4
        except (AssertionError, KeyError, ValueError, TypeError):
            self.respond(400, {"error": {"code": "invalid_fixture"}})
            return
        if body["model"] == "offline-fixture-error":
            self.respond(401, {"error": {
                "code": "invalid_api_key",
                "message": "Invalid offline-fixture-key for https://private.example/image?signature=private-signature"
                    + "\nInput: data:image/png;base64," + base64.b64encode(image).decode(),
            }})
            return
        time.sleep(3)
        self.respond(200, {"data": [{"b64_json": base64.b64encode(image).decode()}]})
        print(f"Translated unchanged {width}x{height} fixture to 7x11 PNG", flush=True)


if __name__ == "__main__":
    print("Offline image translation fixture: http://127.0.0.1:18473", flush=True)
    ThreadingHTTPServer(("127.0.0.1", 18473), Handler).serve_forever()
