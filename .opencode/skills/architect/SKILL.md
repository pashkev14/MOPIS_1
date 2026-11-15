---
name: architect
description: Design system architecture, UML class structure, and YAML schemas for Variant 1 (inheritance only).
---

# Instructions
1. Read `docs/task_3-1-1.md` (Section 2, Section 3 Variant 1, Section 5).
2. Design domain model for Variant 1 using **parameter inheritance** (`parent` in `classes.yaml`):
   - Base class `item` ("Изделие") with common parameters (e.g., `designation`, `mass` with `min`/`max`, and a `default` value).
   - Subclass `component` ("Комплектующее изделие", `parent: item`) inheriting base parameters, overriding a parameter range (e.g., `mass`), and adding component-specific parameters (e.g., `is_produced`, `supplier_approved`).
3. Design process template YAML for Variant 1 ("Включение нового комплектующего в справочник", bound strictly to `class: component`) with all states, actions, roles, and DNF transition conditions (`[[pred1, pred2], [not pred3]]` demonstrating `AND`, `OR`, `not`, `in_range`, and class predicate checking `item`).
4. Define Java packages and classes based on recommended patterns:
   - `Type Object`: `ObjectClass`, `Param`, `Obj`, `ClassRegistry`.
   - `FSM`: `ProcessEngine`, `ProcessInstance`, `StateMachine`, `State`, `Transition`.
   - `Interpreter`: `Expression`, `Or`, `And`, `Not`, `ParamPredicate`, `ClassPredicate`, `Always`.
   - `Command & Log`: `ProcessService`, `Command` (`Start`, `Action`), `AuditLog` (`JsonLinesLog`, `MemoryLog`).
5. Save the complete specification and Mermaid class diagram to `docs/spec.md`.

# Constraints
- **NEVER** create or edit `.java` files or `config/*.yaml` files.
- Keep `StateMachine` (shared rules) strictly separate from `ProcessInstance` (mutable runtime state).
