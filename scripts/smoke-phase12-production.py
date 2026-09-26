#!/usr/bin/env python3
"""Read-only production smoke test for Masary student phase 12.

The test never prints credentials or tokens and never starts an activity.
It verifies login/token auth, subject visibility, unit/lesson states, and
activity preview enforcement for one open lesson and one locked lesson.
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


def request_json(method: str, path: str, *, token: str = "", body: dict | None = None):
    url = urllib.parse.urljoin(BASE_URL, path.lstrip("/"))
    data = None if body is None else json.dumps(body).encode("utf-8")
    headers = {"Accept": "application/json", "User-Agent": "masary-phase12-smoke/1.0"}
    if body is not None:
        headers["Content-Type"] = "application/json"
    if token:
        headers["Authorization"] = f"Bearer {token}"

    request = urllib.request.Request(url, data=data, headers=headers, method=method)
    try:
        with urllib.request.urlopen(request, timeout=TIMEOUT) as response:
            raw = response.read().decode("utf-8")
            return response.status, json.loads(raw)
    except urllib.error.HTTPError as error:
        raw = error.read().decode("utf-8", errors="replace")
        try:
            payload = json.loads(raw)
        except json.JSONDecodeError:
            payload = {"success": False, "error": {"code": "non_json_error", "message": raw[:200]}}
        return error.code, payload
    except (urllib.error.URLError, TimeoutError) as error:
        raise SmokeError(f"Network failure while calling {path}: {error}") from error
    except json.JSONDecodeError as error:
        raise SmokeError(f"Non-JSON response from {path}") from error


def require(condition: bool, message: str):
    if not condition:
        raise SmokeError(message)


def authenticate() -> str:
    access_token = os.environ.get("MASARY_SMOKE_ACCESS_TOKEN", "").strip()
    if access_token:
        return access_token

    username = os.environ.get("MASARY_SMOKE_USERNAME", "").strip()
    password = os.environ.get("MASARY_SMOKE_PASSWORD", "")
    require(bool(username and password), "Configure MASARY_SMOKE_ACCESS_TOKEN or both smoke username/password.")

    status, payload = request_json(
        "POST",
        "/api/v1/auth/student/login",
        body={"username": username, "password": password, "device_name": "github-phase12-smoke"},
    )
    require(status == 200 and payload.get("success") is True, "Student smoke login failed.")
    token = str((payload.get("data") or {}).get("access_token") or "").strip()
    require(bool(token), "Login succeeded without an access token.")
    return token


def choose_subject(token: str) -> int:
    status, payload = request_json("GET", "/api/v1/student/subjects", token=token)
    require(status == 200 and payload.get("success") is True, "Unable to load student subjects.")
    subjects = ((payload.get("data") or {}).get("subjects") or [])
    require(bool(subjects), "Smoke student has no visible subjects.")

    configured = os.environ.get("MASARY_SMOKE_SUBJECT_VERSION_ID", "").strip()
    if configured:
        subject_id = int(configured)
        require(
            any(int(item.get("subject_version_id") or 0) == subject_id for item in subjects),
            "Configured smoke subject is not visible to the smoke student.",
        )
        return subject_id

    return int(subjects[0].get("subject_version_id") or 0)


def lesson_candidates(subject: dict):
    content = subject.get("content") or {}
    units = content.get("units") or []
    standalone = content.get("lessons") or []
    flattened = []

    for unit in units:
        unit_id = int(unit.get("id") or 0)
        for lesson in unit.get("lessons") or []:
            item = dict(lesson)
            item["_unit_id"] = int(lesson.get("unit_id") or unit_id)
            flattened.append(item)

    for lesson in standalone:
        item = dict(lesson)
        item["_unit_id"] = int(lesson.get("unit_id") or 0)
        flattened.append(item)

    return units, flattened


def preview_lesson(token: str, subject_id: int, lesson: dict):
    unit_id = int(lesson.get("_unit_id") or 0)
    lesson_id = int(lesson.get("id") or 0)
    require(unit_id > 0 and lesson_id > 0, "Lesson candidate has incomplete identifiers.")
    return request_json(
        "POST",
        "/api/v1/student/activity/preview",
        token=token,
        body={
            "subject_version_id": subject_id,
            "unit_id": unit_id,
            "lesson_id": lesson_id,
            "activity_type": "lesson_practice",
            "activity_mode": "learn",
            "guide_step_id": None,
            "source": "lesson",
        },
    )


def main() -> int:
    token = authenticate()
    subject_id = choose_subject(token)

    status, payload = request_json(
        "GET",
        f"/api/v1/student/subject?{urllib.parse.urlencode({'subject_version_id': subject_id})}",
        token=token,
    )
    require(status == 200 and payload.get("success") is True, "Unable to load phase-12 subject detail.")
    subject = payload.get("data") or {}
    require(int(subject.get("subject_version_id") or 0) == subject_id, "Subject response ID mismatch.")

    content = subject.get("content") or {}
    require(content.get("details_available") is True, "Subject content details are unavailable in production.")
    units, lessons = lesson_candidates(subject)
    require(bool(units), "Production subject returned no units.")
    require(bool(lessons), "Production subject returned no lessons.")

    allowed_states = {"ready", "in_progress", "completed", "locked", "unavailable", "unknown"}
    for lesson in lessons:
        state = str((lesson.get("state") or {}).get("status") or "")
        require(state in allowed_states, f"Unexpected lesson state: {state!r}")

    open_lesson = next(
        (
            lesson
            for lesson in lessons
            if bool((lesson.get("preparation") or {}).get("available"))
            and int(lesson.get("_unit_id") or 0) > 0
        ),
        None,
    )
    locked_lesson = next(
        (
            lesson
            for lesson in lessons
            if str((lesson.get("state") or {}).get("status") or "") == "locked"
            and not bool((lesson.get("preparation") or {}).get("available"))
            and int(lesson.get("_unit_id") or 0) > 0
        ),
        None,
    )

    require(open_lesson is not None, "No production lesson is currently available for preparation.")
    require(locked_lesson is not None, "No locked lesson is available; use a smoke student/subject that exercises the gate.")
    require(bool((locked_lesson.get("state") or {}).get("reason")), "Locked lesson is missing a user-facing reason.")

    open_status, open_preview = preview_lesson(token, subject_id, open_lesson)
    require(open_status == 200 and open_preview.get("success") is True, "Open lesson preview failed.")
    require(
        bool(((open_preview.get("data") or {}).get("eligibility") or {}).get("available")),
        "Open lesson preview was rejected.",
    )

    locked_status, locked_preview = preview_lesson(token, subject_id, locked_lesson)
    locked_available = bool(((locked_preview.get("data") or {}).get("eligibility") or {}).get("available"))
    locked_rejected = (
        locked_status in {400, 401, 403, 409, 422}
        or locked_preview.get("success") is False
        or not locked_available
    )
    require(locked_rejected, "Locked lesson preview was incorrectly allowed.")

    states = {}
    for lesson in lessons:
        state = str((lesson.get("state") or {}).get("status") or "unknown")
        states[state] = states.get(state, 0) + 1

    print(
        "Phase 12 production smoke passed: "
        f"subject={subject_id}, units={len(units)}, lessons={len(lessons)}, states={states}."
    )
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except SmokeError as error:
        print(f"Phase 12 production smoke FAILED: {error}", file=sys.stderr)
        raise SystemExit(1)
