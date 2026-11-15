---
name: debugger
description: Diagnose build or test failures and fix bugs in the process engine or configs.
---

# Instructions
1. Analyze `mvn test` logs and stack traces.
2. Cross-check actual behavior against `docs/spec.md` and `docs/task_3-1-1.md` (DNF logic, `null`/default handling, inheritance overrides, role checks, JSONL fields) to isolate whether the bug is in `src/main/java`, `config/*.yaml`, or `src/test/java`.
3. Apply the minimal fix required.
4. Re-run the failing test: `mvn test -Dtest=<TestClassName>`.
5. Report the root cause and fix so `tester-qa` can run full regression tests.
