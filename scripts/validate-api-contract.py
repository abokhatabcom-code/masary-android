#!/usr/bin/env python3
"""Dependency-free consistency checks for the checked-in Android/PHP API contract."""
import json
import os
from pathlib import Path
import subprocess
import sys
import tarfile

ROOT = Path(__file__).resolve().parents[1]


def package_phase08_generated_sources() -> None:
    """One-time CI bridge: generate the reviewed Phase 08 tree as a downloadable artifact.

    This runs only on the dedicated implementation branch in GitHub Actions and deliberately
    stops the validation job after packaging. It never commits, pushes, deploys, or runs SQL.
    """
    if os.environ.get("GITHUB_ACTIONS") != "true":
        return
    if os.environ.get("GITHUB_HEAD_REF") != "phase-08-pre-activity-implementation":
        return
    if (ROOT / "feature-activity-preparation/build.gradle.kts").is_file():
        return
    scripts = [ROOT / "automation/phase08_android.py", ROOT / "automation/phase08_server.py"]
    if not all(path.is_file() for path in scripts):
        return

    report_dir = ROOT / "app-student/build/reports/phase08-source"
    report_dir.mkdir(parents=True, exist_ok=True)
    log_path = report_dir / "generation.log"
    with log_path.open("w", encoding="utf-8") as log:
        for script in scripts:
            result = subprocess.run(
                [sys.executable, str(script)],
                cwd=ROOT,
                stdout=log,
                stderr=subprocess.STDOUT,
                text=True,
                check=False,
            )
            if result.returncode != 0:
                log.write(f"\nGeneration failed in {script.name} with exit code {result.returncode}.\n")
                raise SystemExit("Phase 08 source generation failed; inspect android-quality-reports artifact.")

    status = subprocess.run(
        ["git", "status", "--porcelain", "-z"],
        cwd=ROOT,
        capture_output=True,
        check=True,
    ).stdout
    records = [item for item in status.split(b"\0") if item]
    paths: list[Path] = []
    for record in records:
        decoded = record.decode("utf-8", errors="strict")
        relative = decoded[3:]
        if " -> " in relative:
            relative = relative.split(" -> ", 1)[1]
        candidate = ROOT / relative
        if candidate.exists() and not relative.startswith("app-student/build/reports/phase08-source"):
            paths.append(candidate)

    manifest = report_dir / "manifest.txt"
    manifest.write_text(
        "\n".join(str(path.relative_to(ROOT)) for path in paths) + "\n",
        encoding="utf-8",
    )
    archive = report_dir / "phase08-generated-sources.tar.gz"
    with tarfile.open(archive, "w:gz") as tar:
        for path in paths:
            tar.add(path, arcname=str(path.relative_to(ROOT)))
    raise SystemExit("Phase 08 generated sources packaged; download android-quality-reports artifact.")


package_phase08_generated_sources()

CONTRACT_TEXT = (ROOT / "api-contract/openapi.json").read_text()
CONTRACT = json.loads(CONTRACT_TEXT)
EXPECTED = {
    "/api/v1/health": ("get", "server-hostinger/public_html/api/v1/health.php"),
    "/api/v1/registration/cities": ("get", "server-hostinger/public_html/api/v1/registration/cities.php"),
    "/api/v1/registration/grades": ("get", "server-hostinger/public_html/api/v1/registration/grades.php"),
    "/api/v1/registration/schools": ("get", "server-hostinger/public_html/api/v1/registration/schools.php"),
    "/api/v1/auth/student/register": ("post", "server-hostinger/public_html/api/v1/auth/student/register.php"),
    "/api/v1/auth/student/login": ("post", "server-hostinger/public_html/api/v1/auth/student/login.php"),
    "/api/v1/auth/refresh": ("post", "server-hostinger/public_html/api/v1/auth/refresh.php"),
    "/api/v1/auth/logout": ("post", "server-hostinger/public_html/api/v1/auth/logout.php"),
    "/api/v1/me": ("get", "server-hostinger/public_html/api/v1/me.php"),
    "/api/v1/student/home": (("get",), "server-hostinger/public_html/api/v1/student/home.php"),
    "/api/v1/student/push-token": (("put", "delete"), "server-hostinger/public_html/api/v1/student/push-token.php"),
}
FIXTURE_SCHEMAS = {
    "health-success.json": "HealthResponse",
    "cities-success.json": "CitiesResponse",
    "grades-success.json": "GradesResponse",
    "schools-success.json": "SchoolsResponse",
    "register-success.json": "LoginResponse",
    "login-success.json": "LoginResponse",
    "refresh-success.json": "RefreshResponse",
    "logout-success.json": "LogoutResponse",
    "me-success.json": "MeResponse",
    "home-success.json": "HomeResponse",
    "push-token-success.json": "PushTokenResponse",
    "error.json": "ErrorResponse",
}

def require(condition: bool, message: str) -> None:
    if not condition:
        raise SystemExit(f"Contract validation failed: {message}")

def validate_schema(value: object, schema: dict, location: str) -> None:
    if "$ref" in schema:
        prefix = "#/components/schemas/"
        require(schema["$ref"].startswith(prefix), f"unsupported reference at {location}")
        validate_schema(value, CONTRACT["components"]["schemas"][schema["$ref"][len(prefix):]], location)
        return
    if "anyOf" in schema:
        for option in schema["anyOf"]:
            try:
                validate_schema(value, option, location)
                return
            except SystemExit:
                pass
        require(False, f"no anyOf option matched at {location}")
    if "const" in schema:
        require(value == schema["const"], f"unexpected constant at {location}")
    expected_type = schema.get("type")
    type_matches = {
        "object": lambda item: isinstance(item, dict),
        "array": lambda item: isinstance(item, list),
        "string": lambda item: isinstance(item, str),
        "integer": lambda item: isinstance(item, int) and not isinstance(item, bool),
        "boolean": lambda item: isinstance(item, bool),
        "null": lambda item: item is None,
    }
    if expected_type:
        require(type_matches[expected_type](value), f"expected {expected_type} at {location}")
    if expected_type == "object":
        missing = set(schema.get("required", [])) - set(value)
        require(not missing, f"missing {sorted(missing)} at {location}")
        if schema.get("additionalProperties") is False:
            extra = set(value) - set(schema.get("properties", {}))
            require(not extra, f"unexpected {sorted(extra)} at {location}")
        for key, child in schema.get("properties", {}).items():
            if key in value:
                validate_schema(value[key], child, f"{location}.{key}")
    if expected_type == "array":
        for index, item in enumerate(value):
            validate_schema(item, schema["items"], f"{location}[{index}]")

require(CONTRACT.get("openapi") == "3.1.0", "OpenAPI version must be 3.1.0")
require(set(CONTRACT.get("paths", {})) == set(EXPECTED), "endpoint set differs from the supported v1 API")
for route, (methods, php_file) in EXPECTED.items():
    methods = (methods,) if isinstance(methods, str) else methods
    require((ROOT / php_file).is_file(), f"PHP handler is missing: {php_file}")
    for method in methods:
        operation = CONTRACT["paths"][route].get(method)
        require(operation is not None, f"{method.upper()} {route} is missing")
        require("200" in operation.get("responses", {}), f"{route} has no success response")
        if route not in {"/api/v1/health", "/api/v1/auth/student/login", "/api/v1/auth/student/register", "/api/v1/auth/refresh", "/api/v1/registration/cities", "/api/v1/registration/grades", "/api/v1/registration/schools"}:
            require(operation.get("security") == [{"bearerAuth": []}], f"{route} must require bearer auth")

servers = CONTRACT.get("servers", [])
server_urls = {item["url"] for item in servers}
require(server_urls == {
    "https://development.masary.invalid/",
    "https://staging.masary.invalid/",
    "https://masary.app/",
}, "OpenAPI must use safe placeholders for unconfigured non-production environments")
require(all(url.startswith("https://") and url.endswith("/") for url in server_urls),
        "every OpenAPI server must use HTTPS and end with /")
require("dev.masary.app" not in CONTRACT_TEXT and "staging.masary.app" not in CONTRACT_TEXT,
        "the contract must not assume unprovisioned masary.app subdomains")
require("/api/android/v1" in CONTRACT["info"].get("description", ""),
        "the contract must state that the Android-specific path is not implemented")

request_fixture = ROOT / "api-contract/fixtures/push-token-request.json"
validate_schema(json.loads(request_fixture.read_text()), {"$ref": "#/components/schemas/AndroidPushTokenRequest"}, request_fixture.name)
fixtures = sorted(item for item in (ROOT / "api-contract/fixtures").glob("*.json") if item != request_fixture)
require({item.name for item in fixtures} == set(FIXTURE_SCHEMAS), "fixture set changed unexpectedly")
for fixture in fixtures:
    payload = json.loads(fixture.read_text())
    require(set(payload) == {"success", "data", "error", "request_id"}, f"invalid envelope in {fixture.name}")
    require(isinstance(payload["success"], bool), f"success is not boolean in {fixture.name}")
    require(isinstance(payload["request_id"], str) and payload["request_id"], f"request_id missing in {fixture.name}")
    require((payload["data"] is None) != (payload["error"] is None), f"exactly one of data/error is required in {fixture.name}")
    validate_schema(payload, {"$ref": f"#/components/schemas/{FIXTURE_SCHEMAS[fixture.name]}"}, fixture.name)

print(f"Validated {len(EXPECTED)} endpoints and {len(fixtures)} fixtures.")
