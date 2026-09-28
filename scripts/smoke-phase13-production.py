#!/usr/bin/env python3
"""Non-mutating production readiness smoke for Masary phase 13 question sessions.

Uses a deliberately missing session ID. A 404 from session/answer/finish proves
the authenticated routes are deployed and the phase-13 answer/result tables are
available, without creating a session, storing an answer, or debiting balances.
"""

from __future__ import annotations

import json
import os
import sys
import urllib.error
import urllib.parse
import urllib.request

BASE_URL = os.environ.get("MASARY_SMOKE_BASE_URL", "https://masary.app/").rstrip("/") + "/"
TIMEOUT = 20


class SmokeError(RuntimeError):
    pass


def require(condition: bool, message: str) -> None:
    if not condition:
        raise SmokeError(message)


def request_json(
    method: str,
    path: str,
    *,
    token: str = "",
    body: dict | None = None,
    headers: dict[str, str] | None = None,
):
    url = urllib.parse.urljoin(BASE_URL, path.lstrip("/"))
    data = None if body is None else json.dumps(body).encode("utf-8")
    request_headers = {
        "Accept": "application/json",
        "User-Agent": "masary-phase13-smoke/1.0",
    }
    if body is not None:
        request_headers["Content-Type"] = "application/json"
    if token:
        request_headers["Authorization"] = f"Bearer {token}"
    request_headers.update(headers or {})

    request = urllib.request.Request(url, data=data, headers=request_headers, method=method)
    try:
        with urllib.request.urlopen(request, timeout=TIMEOUT) as response:
            return response.status, json.loads(response.read().decode("utf-8"))
    except urllib.error.HTTPError as error:
        raw = error.read().decode("utf-8", errors="replace")
        try:
            payload = json.loads(raw)
        except json.JSONDecodeError:
            payload = {
                "success": False,
                "error": {"code": "non_json_error", "message": raw[:200]},
            }
        return error.code, payload
    except (urllib.error.URLError, TimeoutError) as error:
        raise SmokeError(f"Network failure while calling {path}: {error}") from error
    except json.JSONDecodeError as error:
        raise SmokeError(f"Non-JSON response from {path}") from error


def authenticate() -> str:
    access_token = os.environ.get("MASARY_SMOKE_ACCESS_TOKEN", "").strip()
    if access_token:
        return access_token

    username = os.environ.get("MASARY_SMOKE_USERNAME", "").strip()
    password = os.environ.get("MASARY_SMOKE_PASSWORD", "")
    require(
        bool(username and password),
        "Configure MASARY_SMOKE_ACCESS_TOKEN or both smoke username/password.",
    )
    status, payload = request_json(
        "POST",
        "/api/v1/auth/student/login",
        body={
            "username": username,
            "password": password,
            "device_name": "github-phase13-smoke",
        },
    )
    require(status == 200 and payload.get("success") is True, "Student smoke login failed.")
    token = str((payload.get("data") or {}).get("access_token") or "").strip()
    require(bool(token), "Login succeeded without an access token.")
    return token


def expect_missing(status: int, payload: dict, label: str) -> None:
    code = str((payload.get("error") or {}).get("code") or "")
    require(status == 404, f"{label} expected 404 readiness response, got HTTP {status} ({code}).")
    require(payload.get("success") is False, f"{label} did not return an error envelope.")
    require(code == "activity_session_not_found", f"{label} returned unexpected code: {code!r}.")


def main() -> int:
    token = authenticate()
    missing_session = "phase13-smoke-missing-session-0001"

    status, payload = request_json(
        "GET",
        "/api/v1/student/activity/session?"
        + urllib.parse.urlencode({"session_id": missing_session}),
        token=token,
    )
    expect_missing(status, payload, "question session")

    status, payload = request_json(
        "POST",
        "/api/v1/student/activity/answer",
        token=token,
        headers={"Idempotency-Key": "phase13-smoke-answer-00000001"},
        body={
            "session_id": missing_session,
            "question_id": "0123456789abcdef0123456789abcdef",
            "answer": {
                "kind": "choice",
                "option_id": "0123456789abcdef0123456789abcdef",
            },
        },
    )
    expect_missing(status, payload, "question answer")

    status, payload = request_json(
        "POST",
        "/api/v1/student/activity/finish",
        token=token,
        headers={"Idempotency-Key": "phase13-smoke-finish-00000001"},
        body={"session_id": missing_session},
    )
    expect_missing(status, payload, "question finish")

    print("Phase 13 production readiness smoke passed: session/answer/finish routes and schemas are live.")
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except SmokeError as error:
        print(f"Phase 13 production smoke FAILED: {error}", file=sys.stderr)
        raise SystemExit(1)
