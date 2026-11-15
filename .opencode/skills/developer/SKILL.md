---
name: developer
description: Implement the configurable process engine, config loader, and Variant 1 YAML files per docs/spec.md.
---

# Instructions
1. Read `docs/spec.md` and `docs/task_3-1-1.md`.
2. Implement YAML config loading (`classes.yaml`, `objects.yaml`, `users.yaml`, `process.yaml`):
   - Resolve `parent` inheritance: child parameters with the same `id` completely replace parent parameters (keeping type).
   - Apply `default` values only when scalar fields are omitted; preserve explicit `null`.
   - Do not validate parameter `min`/`max` bounds on load; validate them only during transition predicates.
3. Implement DNF `Interpreter` (`filled`, `=`, `!=`, `<`, `<=`, `>`, `>=`, `in_range`, `not`, `ClassPredicate`, `Always`). Unset/`null` parameter values evaluate predicates to `false`.
4. Implement `ProcessEngine` & `ProcessService`:
   - Enforce `create_roles`, state `roles`, single initial state, no actions from final states, exact class match for process template.
   - Reject ambiguous transitions (>1 matching transition for `(state, action)` with `true` condition).
   - Write every command outcome to JSONL log (`logs/audit.jsonl`).
5. Create Variant 1 YAML files in `config/` and a CLI/demo runner (`ru.etu.mopis.Main`) demonstrating the main path, rollback, and rejections.
6. Run `mvn clean compile` to verify zero errors.

# Constraints
- **NEVER** hardcode Variant 1 names/rules in `src/main/java`.
- Do not write tests (handled by `tester-qa`).
