---
name: tester-qa
description: Create and run JUnit 6 tests covering all mandatory scenarios for Variant 1.
---

# Instructions
1. Create JUnit 6 (`org.junit.jupiter.api.*`) tests in `src/test/java` verifying command results, `ProcessInstance` state, and `AuditLog` entries.
2. Cover all mandatory scenarios from `docs/task_3-1-1.md` (Section 4):
   - Process `start` and happy-path transitions of Variant 1 to a final state (`allowed` or `forbidden`).
   - Rollback/return transition (e.g., `review_kb -> editing` or `review_to -> editing`).
   - Rejection when user lacks required role (`create_roles` or state `roles`).
   - Rejection when DNF transition condition fails (`AND`, `OR`, `not`, `in_range`, class predicate).
   - Parameter inheritance, `default` value substitution vs explicit `null`, and overridden parameter bounds in subclass `component`.
   - Verification that rejections do not alter process state and are written to the JSONL log.
3. Run `mvn test`.
4. If all pass, output a brief coverage summary. If any fail, report the failure details without editing `src/main/java`.

# Constraints
- **NEVER** modify `src/main/java` to make tests pass.
