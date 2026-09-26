#!/usr/bin/env python3
"""Read-only production smoke test for Masary student phase 12.5.

This script never prints credentials or access tokens and performs GET requests
only after optional login. It validates the data foundation added in phase 12.5:
home summary/today/streak/subscription/smart guide, subject summaries, and the
selected subject detail.
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


def request_json(method: str, path: str, *, token: str = "", body: dict | None = None):
    url = urllib.parse.urljoin(BASE_URL, path.lstrip("/"))
    data = None if body is None else json.dumps(body).encode("utf-8")
    headers = {
        "Accept": "application/json",
        "User-Agent": "masary-phase12.5-smoke/1.0",
    }
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
            "device_name": "github-phase12.5-smoke",
        },
    )
    require(status == 200 and payload.get("success") is True, "Student smoke login failed.")
    token = str((payload.get("data") or {}).get("access_token") or "").strip()
    require(bool(token), "Login succeeded without an access token.")
    return token


def expected_level(xp: int, step: int) -> tuple[int, int]:
    safe_xp = max(0, xp)
    level = max(1, min(10, safe_xp // step + 1))
    if level >= 10:
        return 10, 100
    base = (level - 1) * step
    percent = round(max(0.0, min(1.0, (safe_xp - base) / step)) * 100)
    return level, percent


def get_success(token: str, path: str, label: str) -> dict:
    status, payload = request_json("GET", path, token=token)
    require(status == 200 and payload.get("success") is True, f"Unable to load {label}.")
    data = payload.get("data")
    require(isinstance(data, dict), f"{label} response is missing data.")
    return data


def verify_home(token: str) -> dict:
    home = get_success(token, "/api/v1/student/home", "student home")

    summary = home.get("summary") or {}
    global_xp = int(summary.get("global_xp") or 0)
    level = int(summary.get("level") or 0)
    level_percent = int(summary.get("level_percent") or 0)
    expected, expected_percent = expected_level(global_xp, 300)
    require(level == expected, "Home level does not match the current 300-XP platform rule.")
    require(
        level_percent == expected_percent,
        "Home level progress does not match the current 300-XP platform rule.",
    )
    require(int(summary.get("gems") or 0) >= 0, "Home gems must be non-negative.")

    today = home.get("today") or {}
    for field in ("xp", "seconds", "minutes", "attempts"):
        require(int(today.get(field) or 0) >= 0, f"Home today.{field} must be non-negative.")

    streak = home.get("streak") or {}
    require(int(streak.get("current_days") or 0) >= 0, "Current streak must be non-negative.")
    require(int(streak.get("best_days") or 0) >= 0, "Best streak must be non-negative.")
    require(int(streak.get("protection_count") or 0) >= 0, "Streak protection must be non-negative.")
    goal = streak.get("goal") or {}
    require(0 <= int(goal.get("progress_percent") or 0) <= 100, "Streak goal progress is invalid.")

    subscription = home.get("subscription") or {}
    require(isinstance(subscription.get("active"), bool), "Subscription active flag is missing.")
    require(str(subscription.get("status") or "").strip() != "", "Subscription status is missing.")
    if subscription.get("active") is True:
        require(str(subscription.get("ends_at") or "").strip() != "", "Active subscription has no end date.")

    guide = home.get("smart_guide") or {}
    require(isinstance(guide.get("enabled"), bool), "Smart guide enabled flag is missing.")
    completed = int(guide.get("completed_steps") or 0)
    total = int(guide.get("total_steps") or 0)
    progress = int(guide.get("completion_percent") or 0)
    require(completed >= 0 and total >= 0 and completed <= total, "Smart guide step counts are invalid.")
    require(0 <= progress <= 100, "Smart guide completion percent is invalid.")
    steps = guide.get("steps") or []
    require(isinstance(steps, list), "Smart guide steps must be a list.")

    subjects = home.get("subjects") or []
    require(isinstance(subjects, list), "Home subjects must be a list.")

    return home


def verify_subjects(token: str) -> tuple[dict, dict]:
    data = get_success(token, "/api/v1/student/subjects", "student subjects")
    subjects = data.get("subjects") or []
    require(bool(subjects), "Smoke student has no visible subjects.")

    configured = os.environ.get("MASARY_SMOKE_SUBJECT_VERSION_ID", "").strip()
    if configured:
        subject_id = int(configured)
        summary = next(
            (
                item
                for item in subjects
                if int(item.get("subject_version_id") or 0) == subject_id
            ),
            None,
        )
        require(summary is not None, "Configured smoke subject is not visible to the smoke student.")
    else:
        summary = subjects[0]
        subject_id = int(summary.get("subject_version_id") or 0)

    require(subject_id > 0, "Subject summary has an invalid subject_version_id.")
    hearts = int(summary.get("hearts") or 0)
    require(0 <= hearts <= 3, "Subject hearts are outside the current 0..3 platform range.")

    points = summary.get("points")
    level = summary.get("level")
    progress = (summary.get("progress") or {}).get("percent")
    if points is not None:
        points_value = int(points)
        expected, expected_percent = expected_level(points_value, 100)
        require(int(level or 0) == expected, "Subject level does not match subject_xp.")
        require(int(progress or 0) == expected_percent, "Subject level progress does not match subject_xp.")

    access = summary.get("access") or {}
    require(access.get("available") is True, "Visible curriculum subject has unknown access availability.")
    require(str(access.get("status") or "") in {"available", "free"}, "Visible subject access status is unexpected.")

    return data, summary


def verify_subject_detail(token: str, summary: dict) -> dict:
    subject_id = int(summary.get("subject_version_id") or 0)
    query = urllib.parse.urlencode({"subject_version_id": subject_id})
    detail = get_success(token, f"/api/v1/student/subject?{query}", "subject detail")
    require(int(detail.get("subject_version_id") or 0) == subject_id, "Subject detail ID mismatch.")

    hearts = detail.get("hearts") or {}
    current = int(hearts.get("current") or 0)
    maximum = int(hearts.get("maximum") or 0)
    require(maximum == 3, "Subject detail maximum hearts must remain 3 in phase 12.5.")
    require(0 <= current <= maximum, "Subject detail hearts are invalid.")
    require(current == int(summary.get("hearts") or 0), "Subject list/detail hearts mismatch.")

    points = detail.get("points") or {}
    level = detail.get("level") or {}
    progress = detail.get("progress") or {}
    if points.get("available") is True:
        points_value = int(points.get("value") or 0)
        expected, expected_percent = expected_level(points_value, 100)
        require(level.get("available") is True, "Subject points are available but level is unavailable.")
        require(progress.get("available") is True, "Subject points are available but level progress is unavailable.")
        require(int(level.get("value") or 0) == expected, "Subject detail level does not match subject_xp.")
        require(int(progress.get("percent") or 0) == expected_percent, "Subject detail progress does not match subject_xp.")
        if summary.get("points") is not None:
            require(points_value == int(summary.get("points") or 0), "Subject list/detail points mismatch.")

    access = detail.get("access") or {}
    require(access.get("available") is True, "Subject detail access is still unknown.")
    require(str(access.get("status") or "") in {"available", "free"}, "Subject detail access status is unexpected.")

    return detail


def main() -> int:
    token = authenticate()
    home = verify_home(token)
    subjects_data, summary = verify_subjects(token)
    detail = verify_subject_detail(token, summary)

    guide = home.get("smart_guide") or {}
    print(
        "Phase 12.5 production smoke passed: "
        f"subject={int(summary.get('subject_version_id') or 0)}, "
        f"subjects={len(subjects_data.get('subjects') or [])}, "
        f"home_subjects={len(home.get('subjects') or [])}, "
        f"guide_steps={int(guide.get('total_steps') or 0)}, "
        f"hearts={int((detail.get('hearts') or {}).get('current') or 0)}."
    )
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except SmokeError as error:
        print(f"Phase 12.5 production smoke FAILED: {error}", file=sys.stderr)
        raise SystemExit(1)
