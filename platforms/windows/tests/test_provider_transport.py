"""Runs WinHTTP against disposable loopback SSE endpoints; never uses stored keys."""
import argparse
import http.server
import subprocess
import threading
import time

class Handler(http.server.BaseHTTPRequestHandler):
    def log_message(self, *args):
        pass

    def do_POST(self):
        mode = self.path.split('/')[1]
        body = self.rfile.read(int(self.headers['Content-Length']))
        assert b'local-test' in body and self.headers['Authorization'] == 'Bearer rimes-loopback-test'
        self.send_response(503 if mode == 'http_error' else 200)
        self.send_header('Content-Type', 'text/event-stream')
        self.end_headers()
        if mode == 'timeout':
            time.sleep(7)
            return
        if mode == 'malformed':
            self.wfile.write(b'data: invalid\n\n')
            return
        data = 'data: {"choices":[{"delta":{"content":"你好💡"}}]}\r\n\r\n'.encode()
        if mode not in ('disconnect', 'http_error'):
            data += b'data: [DONE]\n\n'
        try:
            for byte in data:
                self.wfile.write(bytes([byte]))
                self.wfile.flush()
        except (ConnectionError, OSError):
            pass

if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('probe')
    args = parser.parse_args()
    with http.server.ThreadingHTTPServer(('127.0.0.1', 0), Handler) as server:
        threading.Thread(target=server.serve_forever, daemon=True).start()
        for mode in ('success', 'disconnect', 'malformed', 'http_error', 'cancel', 'timeout'):
            subprocess.run([args.probe, f'http://127.0.0.1:{server.server_port}/{mode}', mode], check=True, timeout=20)
        server.shutdown()
    # No listener: immediate connection failure must retain the source contract.
    subprocess.run([args.probe, f'http://127.0.0.1:{server.server_port}', 'offline'], check=True, timeout=20)
