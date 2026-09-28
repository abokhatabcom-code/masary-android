#!/usr/bin/env python3
"""Ensure every supported extensionless API endpoint is routed by Hostinger Apache."""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
API_ROOT = ROOT / "server-hostinger/public_html/api"
HTACCESS = API_ROOT / ".htaccess"

ROUTES = {
    "/api/v1/health": "v1/health.php",
    "/api/v1/registration/cities": "v1/registration/cities.php",
    "/api/v1/registration/grades": "v1/registration/grades.php",
    "/api/v1/registration/schools": "v1/registration/schools.php",
    "/api/v1/auth/student/register": "v1/auth/student/register.php",
    "/api/v1/auth/student/login": "v1/auth/student/login.php",
    "/api/v1/auth/refresh": "v1/auth/refresh.php",
    "/api/v1/auth/logout": "v1/auth/logout.php",
    "/api/v1/me": "v1/me.php",
    "/api/v1/student/home": "v1/student/home.php",
    "/api/v1/student/subjects": "v1/student/subjects.php",
    "/api/v1/student/subject": "v1/student/subject.php",
    "/api/v1/student/subject/training-center": "v1/student/subject/training-center.php",
    "/api/v1/student/activity/preview": "v1/student/activity/preview.php",
    "/api/v1/student/activity/start": "v1/student/activity/start.php",
    "/api/v1/student/activity/start-status": "v1/student/activity/start-status.php",
    "/api/v1/student/activity/session": "v1/student/activity/session.php",
    "/api/v1/student/activity/answer": "v1/student/activity/answer.php",
    "/api/v1/student/activity/finish": "v1/student/activity/finish.php",
    "/api/v1/student/push-token": "v1/student/push-token.php",
}


def fail(message: str) -> None:
    raise SystemExit(f"Hostinger route validation failed: {message}")


if not HTACCESS.is_file():
    fail("public_html/api/.htaccess is missing")

text = HTACCESS.read_text(encoding="utf-8")
for public_route, handler in ROUTES.items():
    if not (API_ROOT / handler).is_file():
        fail(f"handler is missing for {public_route}: {handler}")
    rewrite_path = public_route.removeprefix("/api/")
    expected = f"RewriteRule ^{rewrite_path}/?$ {handler} [L,QSA]"
    if expected not in text:
        fail(f"rewrite rule is missing for {public_route}")

print(f"Validated {len(ROUTES)} Hostinger extensionless API rewrite routes.")
