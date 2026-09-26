# Phase 12 production smoke trigger

This branch exists only to run the read-only Phase 12 production smoke workflow
before merging the phase into the official branch.

The workflow requires either `MASARY_SMOKE_ACCESS_TOKEN` or the pair
`MASARY_SMOKE_USERNAME` / `MASARY_SMOKE_PASSWORD` in GitHub Actions secrets.
It reads production subject/unit/lesson state and calls activity preview only.
It does not start an activity or mutate student progress.
