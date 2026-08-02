#!/usr/bin/env python3
"""Dependency-free consistency checks for the checked-in Android/PHP API contracts."""
import copy
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
BASE_PATH = ROOT / "api-contract/openapi.json"
EXTENSION_PATHS = [
    ROOT / "api-contract/openapi-phase08.json",
    ROOT / "api-contract/openapi-phase09.json",
]

BASE_TEXT = BASE_PATH.read_text(encoding="utf-8")
BASE_CONTRACT = json.loads(BASE_TEXT)
CONTRACT = copy.deepcopy(BASE_CONTRACT)
CONTRACT_PARTS = [BASE_TEXT]


def require(condition: bool, message: str) -> None:
    if not condition:
        raise SystemExit(f"Contract validation failed: {message}")


def merge_unique(target: dict, additions: dict, location: str) -> None:
    duplicates = set(target) & set(additions)
    require(not duplicates, f"duplicate definitions in {location}: {sorted(duplicates)}")
    target.update(copy.deepcopy(additions))


for extension_path in EXTENSION_PATHS:
    require(extension_path.is_file(), f"contract extension is missing: {extension_path.name}")
    text = extension_path.read_text(encoding="utf-8")
    extension = json.loads(text)
    require(extension.get("openapi") == "3.1.0", f"{extension_path.name} must use OpenAPI 3.1.0")
    merge_unique(
        CONTRACT.setdefault("paths", {}),
        extension.get("paths", {}),
        f"{extension_path.name}.paths",
    )
    components = CONTRACT.setdefault("components", {})
    extension_components = extension.get("components", {})
    merge_unique(
        components.setdefault("responses", {}),
        extension_components.get("responses", {}),
        f"{extension_path.name}.components.responses",
    )
    merge_unique(
        components.setdefault("schemas", {}),
        extension_components.get("schemas", {}),
        f"{extension_path.name}.components.schemas",
    )
    CONTRACT_PARTS.append(text)

CONTRACT_TEXT = "\n".join(CONTRACT_PARTS)

EXPECTED = {
    "/api/v1/health": (("get",), "server-hostinger/public_html/api/v1/health.php"),
    "/api/v1/registration/cities": (("get",), "server-hostinger/public_html/api/v1/registration/cities.php"),
    "/api/v1/registration/grades": (("get",), "server-hostinger/public_html/api/v1/registration/grades.php"),
    "/api/v1/registration/schools": (("get",), "server-hostinger/public_html/api/v1/registration/schools.php"),
    "/api/v1/auth/student/register": (("post",), "server-hostinger/public_html/api/v1/auth/student/register.php"),
    "/api/v1/auth/student/login": (("post",), "server-hostinger/public_html/api/v1/auth/student/login.php"),
    "/api/v1/auth/refresh": (("post",), "server-hostinger/public_html/api/v1/auth/refresh.php"),
    "/api/v1/auth/logout": (("post",), "server-hostinger/public_html/api/v1/auth/logout.php"),
    "/api/v1/me": (("get",), "server-hostinger/public_html/api/v1/me.php"),
    "/api/v1/student/home": (("get",), "server-hostinger/public_html/api/v1/student/home.php"),
    "/api/v1/student/subjects": (("get",), "server-hostinger/public_html/api/v1/student/subjects.php"),
    "/api/v1/student/push-token": (("put", "delete"), "server-hostinger/public_html/api/v1/student/push-token.php"),
    "/api/v1/student/activity/preview": (("post",), "server-hostinger/public_html/api/v1/student/activity/preview.php"),
    "/api/v1/student/activity/start": (("post",), "server-hostinger/public_html/api/v1/student/activity/start.php"),
    "/api/v1/student/activity/start-status": (("get",), "server-hostinger/public_html/api/v1/student/activity/start-status.php"),
}

PUBLIC_ROUTES = {
    "/api/v1/health",
    "/api/v1/auth/student/login",
    "/api/v1/auth/student/register",
    "/api/v1/auth/refresh",
    "/api/v1/registration/cities",
    "/api/v1/registration/grades",
    "/api/v1/registration/schools",
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
    "subjects-success.json": "StudentSubjectsResponse",
    "push-token-success.json": "PushTokenResponse",
    "activity-preview-success.json": "ActivityPreparationPreviewResponse",
    "activity-start-success.json": "ActivityStartResponse",
    "error.json": "ErrorResponse",
}

REQUEST_FIXTURES = {
    "push-token-request.json": "AndroidPushTokenRequest",
    "activity-preview-request.json": "ActivityPreparationRequest",
}


def validate_schema(value: object, schema: dict, location: str) -> None:
    if "$ref" in schema:
        prefix = "#/components/schemas/"
        reference = schema["$ref"]
        require(reference.startswith(prefix), f"unsupported reference at {location}")
        name = reference[len(prefix):]
        require(name in CONTRACT["components"]["schemas"], f"unknown schema {name} at {location}")
        validate_schema(value, CONTRACT["components"]["schemas"][name], location)
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
    if "enum" in schema:
        require(value in schema["enum"], f"value is outside enum at {location}")

    expected_type = schema.get("type")
    type_matches = {
        "object": lambda item: isinstance(item, dict),
        "array": lambda item: isinstance(item, list),
        "string": lambda item: isinstance(item, str),
        "integer": lambda item: isinstance(item, int) and not isinstance(item, bool),
        "number": lambda item: isinstance(item, (int, float)) and not isinstance(item, bool),
        "boolean": lambda item: isinstance(item, bool),
        "null": lambda item: item is None,
    }
    if expected_type:
        require(expected_type in type_matches, f"unsupported type {expected_type} at {location}")
        require(type_matches[expected_type](value), f"expected {expected_type} at {location}")

    if isinstance(value, (int, float)) and not isinstance(value, bool) and "minimum" in schema:
        require(value >= schema["minimum"], f"value is below minimum at {location}")
    if isinstance(value, str):
        if "minLength" in schema:
            require(len(value) >= schema["minLength"], f"string is too short at {location}")
        if "maxLength" in schema:
            require(len(value) <= schema["maxLength"], f"string is too long at {location}")

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


def require_idempotency_header(route: str, method: str) -> None:
    operation = CONTRACT["paths"][route][method]
    matching = [
        parameter
        for parameter in operation.get("parameters", [])
        if parameter.get("in") == "header" and parameter.get("name") == "Idempotency-Key"
    ]
    require(len(matching) == 1, f"{method.upper()} {route} must declare one Idempotency-Key header")
    require(matching[0].get("required") is True, f"{method.upper()} {route} must require Idempotency-Key")


require(BASE_CONTRACT.get("openapi") == "3.1.0", "base OpenAPI version must be 3.1.0")
require(set(CONTRACT.get("paths", {})) == set(EXPECTED), "endpoint set differs from the supported v1 API")

for route, (methods, php_file) in EXPECTED.items():
    require((ROOT / php_file).is_file(), f"PHP handler is missing: {php_file}")
    for method in methods:
        operation = CONTRACT["paths"][route].get(method)
        require(operation is not None, f"{method.upper()} {route} is missing")
        require("200" in operation.get("responses", {}), f"{method.upper()} {route} has no success response")
        if route not in PUBLIC_ROUTES:
            require(
                operation.get("security") == [{"bearerAuth": []}],
                f"{method.upper()} {route} must require bearer auth",
            )

require_idempotency_header("/api/v1/student/activity/start", "post")
require_idempotency_header("/api/v1/student/activity/start-status", "get")

servers = BASE_CONTRACT.get("servers", [])
server_urls = {item["url"] for item in servers}
require(
    server_urls == {
        "https://development.masary.invalid/",
        "https://staging.masary.invalid/",
        "https://masary.app/",
    },
    "OpenAPI must use safe placeholders for unconfigured non-production environments",
)
require(
    all(url.startswith("https://") and url.endswith("/") for url in server_urls),
    "every OpenAPI server must use HTTPS and end with /",
)
require(
    "dev.masary.app" not in CONTRACT_TEXT and "staging.masary.app" not in CONTRACT_TEXT,
    "the contract must not assume unprovisioned masary.app subdomains",
)
require(
    "/api/android/v1" in BASE_CONTRACT["info"].get("description", ""),
    "the base contract must state that the Android-specific path is not implemented",
)

fixture_root = ROOT / "api-contract/fixtures"
for name, schema_name in REQUEST_FIXTURES.items():
    fixture = fixture_root / name
    require(fixture.is_file(), f"request fixture is missing: {name}")
    validate_schema(
        json.loads(fixture.read_text(encoding="utf-8")),
        {"$ref": f"#/components/schemas/{schema_name}"},
        name,
    )

response_fixtures = sorted(
    item for item in fixture_root.glob("*.json") if item.name not in REQUEST_FIXTURES
)
require(
    {item.name for item in response_fixtures} == set(FIXTURE_SCHEMAS),
    "fixture set changed unexpectedly",
)
for fixture in response_fixtures:
    payload = json.loads(fixture.read_text(encoding="utf-8"))
    require(
        set(payload) == {"success", "data", "error", "request_id"},
        f"invalid envelope in {fixture.name}",
    )
    require(isinstance(payload["success"], bool), f"success is not boolean in {fixture.name}")
    require(
        isinstance(payload["request_id"], str) and payload["request_id"],
        f"request_id missing in {fixture.name}",
    )
    require(
        (payload["data"] is None) != (payload["error"] is None),
        f"exactly one of data/error is required in {fixture.name}",
    )
    validate_schema(
        payload,
        {"$ref": f"#/components/schemas/{FIXTURE_SCHEMAS[fixture.name]}"},
        fixture.name,
    )

print(
    f"Validated {len(EXPECTED)} endpoints, {len(REQUEST_FIXTURES)} request fixtures, "
    f"and {len(response_fixtures)} response fixtures across base, phase 08, and phase 09 contracts.",
)
