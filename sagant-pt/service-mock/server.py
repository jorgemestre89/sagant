

from http.server import BaseHTTPRequestHandler, HTTPServer
import json
from datetime import datetime, timezone


class NotificationHandler(BaseHTTPRequestHandler):

    def do_POST(self):
        print(f"DEBUG Headers:\n{self.headers}", flush=True)

        content_length_header = self.headers.get("Content-Length")
        transfer_encoding = self.headers.get("Transfer-Encoding")

        print(f"DEBUG Content-Length: {content_length_header}", flush=True)
        print(f"DEBUG Transfer-Encoding: {transfer_encoding}", flush=True)

        # El mock registra el cuerpo según Content-Length.
        # Si no existe, no intentamos leer bytes arbitrariamente.
        content_length = int(content_length_header or 0)
        raw_body = self.rfile.read(content_length) if content_length > 0 else b""
        decoded_body = raw_body.decode("utf-8", errors="replace")

        print(f"DEBUG Raw body: {decoded_body!r}", flush=True)

        try:
            payload = json.loads(decoded_body)
        except (json.JSONDecodeError, UnicodeDecodeError) as exc:
            print(f"ERROR parsing request body: {exc}", flush=True)
            self.send_response(400)
            self.send_header("Content-Type", "application/json")
            self.end_headers()
            self.wfile.write(b'{"error":"invalid JSON"}')
            return

        print(json.dumps({
            "event": "notification.received",
            "receivedAt": datetime.now(timezone.utc).isoformat(),
            "path": self.path,
            "payload": payload
        }), flush=True)

        response = json.dumps({
            "status": "received",
            "receivedAt": datetime.now(timezone.utc).isoformat()
        }).encode("utf-8")

        self.send_response(200)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(response)))
        self.end_headers()
        self.wfile.write(response)

    def log_message(self, format, *args):
        print("%s - %s" % (self.address_string(), format % args), flush=True)


HTTPServer(("0.0.0.0", 8090), NotificationHandler).serve_forever()
