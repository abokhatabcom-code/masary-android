#!/usr/bin/env python3
"""Dependency-free consistency checks for the checked-in Android/PHP API contract."""
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
CONTRACT = json.loads((ROOT / "api-contract/openapi.json").read_text())
EXPECTED = {
    "/api/v1/health": ("get", "server-hostinger/public_html/api/v1/health.php"),
    "/api/v1/auth/student/login": ("post", "server-hostinger/public_html/api/v1/auth/student/login.php"),
    "/api/v1/auth/refresh": ("post", "server-hostinger/public_html/api/v1/auth/refresh.php"),
    "/api/v1/auth/logout": ("post", "server-hostinger/public_html/api/v1/auth/logout.php"),
    "/api/v1/me": ("get", "server-hostinger/public_html/api/v1/me.php"),
    "/api/v1/student/home": ("get", "server-hostinger/public_html/api/v1/student/home.php"),
}

def require(condition: bool, message: str) -> None:
    if not condition:
        raise SystemExit(f"Contract validation failed: {message}")

require(CONTRACT.get("openapi") == "3.1.0", "OpenAPI version must be 3.1.0")
require(set(CONTRACT.get("paths", {})) == set(EXPECTED), "endpoint set differs from the supported v1 API")
for route, (method, php_file) in EXPECTED.items():
    operation = CONTRACT["paths"][route].get(method)
    require(operation is not None, f"{method.upper()} {route} is missing")
    require((ROOT / php_file).is_file(), f"PHP handler is missing: {php_file}")
    require("200" in operation.get("responses", {}), f"{route} has no success response")
    if route not in {"/api/v1/health", "/api/v1/auth/student/login", "/api/v1/auth/refresh"}:
        require(operation.get("security") == [{"bearerAuth": []}], f"{route} must require bearer auth")

server_urls = {item["url"] for item in CONTRACT.get("servers", [])}
require(server_urls == {"https://dev.masary.app", "https://staging.masary.app", "https://masary.app"}, "environment URLs changed unexpectedly")

for fixture in sorted((ROOT / "api-contract/fixtures").glob("*.json")):
    payload = json.loads(fixture.read_text())
    require(set(payload) == {"success", "data", "error", "request_id"}, f"invalid envelope in {fixture.name}")
    require(isinstance(payload["success"], bool), f"success is not boolean in {fixture.name}")
    require(isinstance(payload["request_id"], str) and payload["request_id"], f"request_id missing in {fixture.name}")
    require((payload["data"] is None) != (payload["error"] is None), f"exactly one of data/error is required in {fixture.name}")

print(f"Validated {len(EXPECTED)} endpoints and {len(list((ROOT / 'api-contract/fixtures').glob('*.json')))} fixtures.")
