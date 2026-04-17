# CLJC Platform-Targeted Analysis Design

**Date:** 2026-04-17

**Status:** Proposed and approved in interactive design review

## Goal

Add explicit, deterministic `.cljc` analysis by requiring the caller to choose a target platform (`:clj` or `:cljs`) before reader conditionals are interpreted.

## Problem

The current codebase already supports:

- parsing source files with `edamame`
- detecting file dialect by extension
- file-based analysis through source units
- basic `.cljs` parsing and analysis through the path-based API

However, `.cljc` support is still incomplete:

- `.cljc` parsing is ambiguous because analysis does not require a target platform
- reader conditionals are not yet modeled as a concrete analysis target
- the analysis pipeline can carry a `.cljc` file forward without a deterministic `:clj` or `:cljs` interpretation
- tests do not yet verify platform-specific selection of `#?` or `#?@` branches

This makes `.cljc` support partial rather than explicit.

## Scope

This design covers the next planning phase only:

- require explicit platform selection for `.cljc`
- make parser/source loading choose a concrete platform view of a `.cljc` file
- propagate platform information through source units and path-based analysis
- ensure all entry points fail clearly when `.cljc` analysis is attempted without `:platform`
- add tests for branch selection and error behavior

This design does **not** attempt to solve:

- full ClojureScript symbol accuracy
- complete static binding inference
- custom dialect plugin architecture
- automatic dual-platform analysis by default
- advanced result merging across multiple `.cljc` platform runs

## User Decisions Captured

The user selected the following product decisions during brainstorming:

1. Prioritize `.cljc` and reader conditional support next.
2. For `.cljc`, require explicit platform selection rather than assuming a default.
3. Apply that requirement to any entry point that ends up analyzing `.cljc`, not only the new path-based API.

## Evaluated Approaches

### Approach 1: One target platform at a time

Require `:platform :clj` or `:platform :cljs` and analyze a `.cljc` file as a concrete single-platform view.

**Pros**
- simple and deterministic
- easy to explain
- easy to test
- fits the current forms-as-data pipeline

**Cons**
- the same `.cljc` file may need two runs for full coverage
- no built-in combined output across platforms yet

**Recommendation:** choose this approach.

### Approach 2: Analyze both platforms automatically

Always expand `.cljc` twice and combine results.

**Pros**
- broad coverage by default
- convenient for some users

**Cons**
- more complexity
- result deduplication is non-trivial
- output semantics become less obvious

### Approach 3: Preserve reader conditionals into the analyzer

Keep `#?` and `#?@` in the parsed structure and make the repetition analyzer understand them directly.

**Pros**
- preserves the source shape more faithfully
- may help future advanced analyses

**Cons**
- significantly more complex
- pushes parser-specific concerns deep into the analyzer
- disproportionate to current needs

## Recommended Design

Adopt **single-platform targeted analysis** for `.cljc`.

The parser/source boundary will require a concrete platform before constructing source units for `.cljc`. Once a source unit exists, it represents a deterministic platform-specific view of the file. The remainder of the analysis pipeline continues to operate on normal forms and explicit metadata, without needing to understand reader conditional semantics.

## External Behavior

### Public API expectation

The path-based API should accept `:platform`:

- `hunt-path`
- `hunt-paths`
- `results-from-paths`
- `results-from-source-units`

Accepted values in this phase:

- `:clj`
- `:cljs`

### Semantics by file type

#### `.clj`
- analysis succeeds without `:platform`
- providing `:platform` is tolerated as a no-op or validated but does not alter semantics

#### `.cljs`
- analysis succeeds without `:platform`
- providing `:platform` is tolerated as a no-op or validated but does not alter semantics

#### `.cljc`
- analysis requires `:platform`
- missing `:platform` must fail with a clear error
- invalid `:platform` must fail with a clear error
- `:platform :clj` must analyze the Clojure view
- `:platform :cljs` must analyze the ClojureScript view

### Error behavior

Errors should be raised early and be explicit. Example message:

```text
CLJC analysis requires :platform (:clj or :cljs) for path: src/foo/bar.cljc
```

For invalid values, use a similarly direct message, for example:

```text
Invalid :platform :foo for path: src/foo/bar.cljc. Expected one of: :clj, :cljs
```

### Legacy API rule

Any entry point that ultimately analyzes a `.cljc` file must respect the same contract. That includes namespace-based flows if they resolve to a `.cljc` source file.

The system must not silently default to `:clj` or `:cljs` when the actual source dialect is `.cljc`.

## Internal Architecture

### Separation of concerns

The implementation should separate three concepts:

1. **dialect** — what kind of file this is (`:clj`, `:cljs`, `:cljc`)
2. **platform** — the target analysis platform (`:clj` or `:cljs`)
3. **reader-conditional strategy** — how parsing chooses the active code for `.cljc`

### Parser boundary

`src/repetition/hunter/parse.clj` should become the main entry point for target validation and parser configuration.

Suggested responsibilities:

- detect dialect from file path
- validate whether `:platform` is required
- validate whether a provided `:platform` is allowed
- compute parser options for the specific analysis target
- read forms using platform-aware options

The important contract is:

- `.clj` and `.cljs` can be read without platform context
- `.cljc` cannot be read for analysis without first resolving a valid target platform

### Source units

`src/repetition/hunter/source.clj` should accept analysis options when building a source unit.

A source unit should include at least:

- `:path`
- `:dialect`
- `:platform` when relevant to analysis
- `:forms`
- `:ns-name`

This makes a source unit a stable, explicit analysis artifact rather than just a path plus raw forms.

### Invariant after source construction

After a source unit is created:

- it already represents a concrete platform-specific view of the file
- no downstream analyzer logic needs to interpret reader conditionals
- `core.clj` can remain mostly dialect-neutral

This keeps `.cljc` complexity concentrated at the parsing/source-loading boundary.

## Analysis Pipeline Changes

The existing source-unit-based path should remain the main path for file analysis:

```text
results-from-paths
  -> source-units-from-paths
  -> source-unit-from-path
  -> parse/read-forms
  -> repetition analysis
```

The main change is that options now include `:platform`, and source construction is responsible for enforcing `.cljc` requirements before analysis continues.

### Analysis context

`analysis-context-for-source` should continue to produce the static-analysis context, but now it should have access to both:

- `:dialect`
- `:platform`

This phase does not need to introduce broad CLJS-specific symbol intelligence yet. It only needs enough context plumbing to keep the contract clean and prepare later phases.

## Legacy Namespace-Based Path

The namespace-based API remains supported, but if it resolves to a `.cljc` file it must follow the same rule:

- require explicit platform
- fail clearly when absent

If the existing namespace-based function signatures cannot carry this cleanly, the implementation may need one of these outcomes:

1. extend the legacy API to accept `:platform`, or
2. fail with an explicit message that `.cljc` namespace analysis requires `:platform`

Preferred outcome: add support if the implementation cost is small and keeps the API coherent.

Non-goal: preserve legacy behavior by silently choosing a default platform.

## Testing Strategy

This phase should add strong contract-driven tests.

### `test/repetition/hunter/parse_test.clj`

Add tests for:

- `.cljc` read without `:platform` fails
- `.cljc` read with `:platform :clj` returns forms for the `:clj` branch
- `.cljc` read with `:platform :cljs` returns forms for the `:cljs` branch
- invalid `:platform` fails clearly

### `test/repetition/hunter/source_test.clj`

Add tests for:

- `source-unit-from-path` preserving `:platform`
- `.cljc` source units containing branch-appropriate forms
- `.cljc` source unit creation failing without platform

### `test/repetition/hunter/core_test.clj`

Add integration tests for:

- `results-from-paths` on `.cljc` with `:platform :clj`
- `results-from-paths` on `.cljc` with `:platform :cljs`
- observable difference between the two results where the fixture is designed to differ
- clear failure when `.cljc` is analyzed without platform
- namespace-based `.cljc` behavior matching the same contract if reachable in tests

### Fixtures

Add or replace `.cljc` fixtures so they contain:

- code shared across platforms
- a `#?(:clj ...)` branch
- a `#?(:cljs ...)` branch
- repeated expressions that differ by platform

The tests should prove not only that parsing succeeds, but that the correct branch was selected.

## Error Handling

Errors should be raised before the analyzer starts transforming expressions.

This avoids subtle behavior where:

- partial forms are analyzed without a target
- missing platform produces misleading repetition results
- different code paths handle the same invalid case differently

A single validation function should centralize this rule to avoid scattered `:cljc` conditionals.

## Documentation Updates

The next implementation should update user-facing docs to describe:

- `.cljc` requires `:platform`
- accepted platform values
- examples for `hunt-path` and `hunt-paths`
- whether and how the legacy namespace-based path supports `.cljc`

## Success Criteria

This phase is successful when:

1. `.cljc` analysis is impossible without explicit platform selection.
2. `.cljc` analysis produces deterministic platform-specific forms.
3. Reader conditional behavior is covered by tests.
4. Path-based and legacy reachable flows obey the same contract.
5. The analyzer remains mostly unchanged below the parsing/source boundary.
6. The design leaves room for later CLJS-specific symbol improvements.

## Out of Scope / Next Likely Phase

After this work, the likely next planning phase is improved dialect-aware static symbol analysis, especially for ClojureScript-specific forms and built-ins.

Possible follow-up topics:

- platform-specific `known-vars`
- CLJS macro and special-form coverage
- deeper static binding analysis
- optional dual-platform `.cljc` analysis mode
- project-level dialect configuration
