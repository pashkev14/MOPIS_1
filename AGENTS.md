# Project Rules & Agent Workflow (MOPIS Task 3-1-1 — Variant 1)

## Environment & Stack
- **Runtime:** Native OpenCode v2 (`2.2.5+`) running directly on host OS with strict sandbox permissions (`opencode.jsonc`).
- **Stack:** Java 25 (OpenJDK), Maven (`pom.xml`), Jackson YAML/JSON, JUnit 6 (`org.junit.jupiter.api.*`).
- **Build:** `mvn clean compile`
- **Run Demo:** `mvn exec:java`
- **Test:** `mvn test`

## Domain & Architecture Rules
- **Source Requirements:** Always refer to `docs/task_3-1-1.md` for exact behavior rules (DNF logic, null/default handling, JSONL format, and Variant 1 diagram).
- **Scope:** Variant 1 ("Включение нового комплектующего в справочник") with **parameter inheritance only** (`parent` + full parameter override). Composition and aggregators are NOT required.
- **Decoupled Engine:** NEVER hardcode domain class names, states, actions, or variant rules in Java code. Everything comes from YAML config files.
- **Core Patterns:** `Type Object` (`ObjectClass`, `Param`, `Obj`, `ClassRegistry`), Table-Driven FSM (`StateMachine`, `ProcessInstance`), `Interpreter` (DNF condition tree: `Or`, `And`, `Not`, `ParamPredicate`, `ClassPredicate`, `Always`), `Command` (`Start`, `Action`), and `AuditLog` (`JsonLinesLog`, `MemoryLog`).
- **Mandatory Logging (Task 3-1-1 Section 2.4):** Every command (`start` or `action`)—accepted, rejected, or errored—must write a single JSONL record (`command`, `user`, `object`, `state_before`, `state_after`, `result`, `reason`) via `AuditLog` (`logs/audit.jsonl`) and keep process state unchanged on failure. Do NOT add third-party logging frameworks (no SLF4J/Logback).

## Security & Operational Constraints
- Never read, print, or modify `.env` files or files outside the project root directory.
- Do not modify `opencode.jsonc` or `pom.xml` without explicit user approval.
- Follow standard Maven layout (`src/main/java`, `src/test/java`).
- Execute only ONE subagent stage per turn when instructed, strictly following `.opencode/skills/<role>/SKILL.md`.
