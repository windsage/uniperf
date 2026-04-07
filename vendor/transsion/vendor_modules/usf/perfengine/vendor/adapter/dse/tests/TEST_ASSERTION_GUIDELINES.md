# DSE Test Assertion Guidelines

This document defines how to write robust tests for DSE flows.

Goal: validate external behavior and flow semantics, not fragile implementation details.

## Core Principle

- Prefer **event-flow semantic assertions**:
  - input event/state sequence
  - stage/service execution
  - externally observable output contract
- Avoid assertions that couple tests to internal transient states.

## Keep vs Replace

### Keep (external contract)

These are stable API/business contracts and should remain asserted:

- Session/decision contract semantics (`effectiveIntent`, `timeMode`, request/grant meaning).
- Snapshot/token API consistency exposed to callers.
- CAS/transaction success-failure observable outcomes.
- Stale update rejection behavior (no externally visible commit).
- Deterministic replay/output equivalence requirements.

Examples:

- `GetCommittedToken().generation` matches current snapshot generation.
- stale update returns failure and does not publish new state.
- replay path returns historical state content for earlier timestamp.

### Replace (implementation detail)

These should be avoided or rewritten:

- Exact `dirtyBits.*` combinations unless they are explicitly part of public contract.
- Fixed generation deltas like `gen2 == gen1 + 1` when semantics only require bounded/no unbounded growth.
- Assertions tied to internal coalesce/throttle/intermediate flags that can vary across build modes.

Rewrite pattern:

- from: internal bit/step count assertion
- to: verify output semantic invariants and externally observable state evolution

## Preferred Assertion Patterns

- **Windowed stabilization flows**:
  - inside window: allow hold/coalesce states from expected set
  - after window: assert target convergence state
- **No-op/idempotent flows**:
  - assert decision semantics unchanged
  - assert state contract unchanged
  - if generation is checked, use bounded/non-regression checks instead of strict single-step assumptions
- **History replay flows**:
  - assert replayed content matches historical snapshot semantics
  - avoid overfitting to internal generation arithmetic

## Review Checklist (before merging tests)

- Does each assertion validate an external contract?
- Would this test still be valid if implementation is refactored but behavior is unchanged?
- Are timing/stability-window expectations modeled as two-phase checks when needed?
- Is the test deterministic across Debug/Coverage/Release where assertions are enabled?

If any answer is "no", rewrite toward flow semantics.
