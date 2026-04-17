# Tool CLI Design for Repetition Hunter

**Date:** 2026-04-17

**Status:** Proposed and approved in interactive design review

## Goal

Add a real command-line entrypoint for file-based analysis using Clojure CLI tools mode (`clj -T`) so users can run repetition-hunter without a REPL and without embedding `-e` expressions.

## Problem

The project already supports file-based analysis of `.clj`, `.cljs`, and `.cljc` files through the library API:

- `repetition.hunter/hunt-path`
- `repetition.hunter/hunt-paths`
- `repetition.hunter.core/results-from-paths`

That means users can run analysis from the command line today, but only indirectly with forms such as:

```bash
clojure -e "(require 'repetition.hunter) (repetition.hunter/hunt-path \"src/app/core.cljs\")"
```

This has several problems:

- awkward quoting
- poor ergonomics for normal CLI use
- harder documentation and copy/paste
- no dedicated validation boundary for command-line usage
- no tool-style interface for scripting or CI

## Scope

This design covers a first minimal CLI/tool entrypoint only.

Included:

- add a `:rh` tools alias in `deps.edn`
- add a dedicated tool namespace for command-line argument validation and dispatch
- support file-based analysis only
- support `:path`, `:paths`, `:platform`, `:sort`, and `:filter`
- reuse the existing analysis engine and output format
- add tests for the tool namespace
- document the new commands in `README.md`

Explicitly out of scope for this phase:

- JSON output
- directory scanning or globbing
- recursive source discovery
- a shell wrapper script in `bin/`
- a Leiningen plugin
- adding a separate `-M` CLI in the same phase
- changing the core analysis output format

## User Decisions Captured

The user selected the following product decisions during brainstorming:

1. They want a CLI that works without a REPL.
2. The preferred interface family is Clojure CLI alias or tool usage.
3. The official first interface should prioritize `clj -T:rh` over `-M`.
4. Argument style can be whichever is simplest and best documented.
5. The command should remain minimal and reuse the existing text output.

## Evaluated Approaches

### Approach 1: `-T` with a single `hunt` function

Example:

```bash
clj -T:rh hunt :path '"src/app/core.cljs"'
clj -T:rh hunt :paths '["src/app/a.cljs" "src/app/b.cljs"]'
clj -T:rh hunt :path '"src/app/shared.cljc"' :platform :cljs
```

**Pros**
- small API surface
- easy to document
- maps naturally to current path-based library functions

**Cons**
- needs light validation for `:path` versus `:paths`
- slightly ambiguous whether `hunt` is a subcommand or just the tool function

### Approach 2: `-T` with separate functions `hunt-path` and `hunt-paths`

Example:

```bash
clj -T:rh hunt-path :path '"src/app/core.cljs"'
clj -T:rh hunt-paths :paths '["src/app/a.cljs" "src/app/b.cljs"]'
```

**Pros**
- mirrors the current API exactly
- lower dispatch complexity in the tool namespace

**Cons**
- wider surface area
- more verbose
- feels more like exposing internal API names than designing a CLI

### Approach 3: `-T` with a single normalized tool function

Example:

```bash
clj -T:rh :path '"src/app/core.cljs"'
clj -T:rh :paths '["src/app/a.cljs" "src/app/b.cljs"]'
clj -T:rh :path '"src/app/shared.cljc"' :platform :cljs
```

The alias points directly to one tool entrypoint function that validates and dispatches.

**Pros**
- smallest user-facing surface
- best fit for Clojure CLI tools mode
- best foundation for future extension
- centralizes validation and terminal UX

**Cons**
- requires a small dedicated adapter layer
- if the tool grows too much later, one function may need to evolve into subcommands

## Recommendation

Adopt **Approach 3**.

The first release should expose a single tools entrypoint through `clj -T:rh` with keyword arguments. Internally, it should use a dedicated namespace that validates command input, normalizes `:path` and `:paths`, then delegates to the existing file-based engine.

This keeps the CLI small, predictable, and easy to document while avoiding new complexity in the core analysis namespaces.

## External Interface

### Alias

Add a `:rh` alias to `deps.edn` using `:exec-fn`.

Target function:

- `repetition.hunter.tool/hunt`

### Commands

Expected usage after implementation:

```bash
clj -T:rh :path '"src/app/core.cljs"'
clj -T:rh :path '"src/app/shared.cljc"' :platform :cljs
clj -T:rh :paths '["src/app/a.cljs" "src/app/b.cljs"]'
clj -T:rh :paths '["src/app/shared.cljc" "src/app/ui.cljs"]' :platform :cljs
```

### Accepted arguments

- `:path` — string path to a single file
- `:paths` — vector of string paths
- `:platform` — `:clj` or `:cljs`
- `:sort` — `:complexity` or `:repetition`
- `:filter` — map compatible with the existing library API

### Validation rules

The tool layer must enforce:

- exactly one of `:path` or `:paths`
- `:paths` must be non-empty
- `:path` must be a string
- `:paths` must be a vector of strings
- `:sort` must be valid if provided
- `.cljc` platform rules remain as currently implemented by the parser/analysis boundary

### Output behavior

The tool should print the same human-readable output the project already prints for file-based analysis.

This phase should not introduce a second output format.

## Internal Architecture

### New namespace

Create:

- `src/repetition/hunter/tool.clj`

### Responsibilities of `tool.clj`

This namespace should:

- accept tool arguments from `clj -T`
- validate tool-specific input shape
- normalize a single `:path` into a one-element `:paths` vector
- construct the options map for the engine
- call `repetition.hunter.core/results-from-paths`
- call `repetition.hunter.core/print-results`

This namespace should **not**:

- implement parsing logic
- implement repetition analysis
- duplicate `.cljc` platform validation already handled in `parse.clj`
- create a new rendering format

### Why not call `hunt-path` or `hunt-paths` directly?

The public API in `repetition.hunter` is designed for library/repl convenience and catches exceptions to print a generic `"Hunt failed"` message.

That is not the right boundary for a tools entrypoint. The tool namespace should sit closer to `core` so it can:

- validate CLI usage more precisely
- preserve clearer failure behavior
- remain a thin adapter over the analysis engine

### Data flow

```text
clj -T:rh ...
  -> repetition.hunter.tool/hunt
  -> validate-input
  -> normalize to vector of paths
  -> repetition.hunter.core/results-from-paths
  -> repetition.hunter.core/print-results
```

## Error Handling

### Usage errors

Examples:

- missing both `:path` and `:paths`
- providing both `:path` and `:paths`
- empty `:paths`
- invalid `:sort`
- wrong types for input values

These should raise clear `ex-info` errors with messages that explain what the user did wrong.

### Analysis errors

Examples:

- `.cljc` without `:platform`
- invalid `:platform`
- unreadable file
- parse failure

These should be allowed to surface clearly from the existing engine, without being replaced by vague generic error text.

## Testing Strategy

### New tests

Create:

- `test/repetition/hunter/tool_test.clj`

### Test coverage

The tool tests should cover:

- accepts `:path`
- accepts `:paths`
- rejects neither `:path` nor `:paths`
- rejects both `:path` and `:paths`
- rejects empty `:paths`
- rejects invalid `:sort`
- passes `:platform` through for `.cljc`
- works for a `.cljs` file
- fails clearly for `.cljc` without `:platform`

These tests should verify behavior of the tool layer itself, not re-test the parser in detail.

### Verification

The implementation should also run the full existing test suite to ensure the new tool namespace does not break the library API.

## Documentation

Update `README.md` to add a new CLI usage section for `clj -T:rh` with examples for:

- a single `.cljs` file
- a `.cljc` file with `:platform :cljs`
- multiple files

The docs should explicitly state that `.cljc` requires `:platform`.

## Success Criteria

This phase is successful when:

1. Users can run repetition-hunter without a REPL using `clj -T:rh`.
2. The tool supports `:path` and `:paths`.
3. The tool reuses the existing file-based analysis engine.
4. Invalid tool usage produces clear errors.
5. `.cljc` continues to require `:platform`.
6. The README documents the new invocation style.
7. The new tool namespace is covered by tests.

## Likely Next Phase

After this, likely follow-up work would be one of:

- add a traditional `-M` CLI wrapper for users who prefer subcommands
- add JSON output for machine-readable use
- add directory discovery / glob support
- add CI-oriented exit semantics
