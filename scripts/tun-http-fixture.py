#!/usr/bin/env python3
"""Host-loopback-only HTTP endpoint for the isolated emulator's real TUN test."""
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
import argparse

parser = argparse.ArgumentParser()
parser.add_argument('--port', type=int, default=18798)
args = parser.parse_args()

class Handler(BaseHTTPRequestHandler):
    def do_GET(self):
        body = b'arca-v98-tun-ok\n'
        self.send_response(200)
        self.send_header('Content-Type', 'text/plain')
        self.send_header('Content-Length', str(len(body)))
        self.end_headers()
        self.wfile.write(body)

ThreadingHTTPServer(('127.0.0.1', args.port), Handler).serve_forever()
