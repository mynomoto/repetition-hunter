# CLJC Platform-Target Analysis Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make `.cljc` analysis deterministic by requiring explicit `:platform` selection, selecting the correct reader-conditional branch during parsing, and propagating that target cleanly through all reachable analysis entry points.

**Architecture:** Keep reader-conditional complexity at the parser/source boundary. `parse.clj` becomes the single place that validates `:platform` and resolves a concrete analysis target, `source.clj` produces platform-aware source units, and `core.clj` stays mostly unchanged below that boundary while both path-based and namespace-based entry points enforce the same `.cljc` contract.

**Tech Stack:** Clojure, `clojure.test`, `borkdude/edamame`, deps.edn / Leiningen

---

## File Structure

### Files to modify
- `src/repetition/hunter/parse.clj` — validate `.cljc` platform requirements and configure edamame for platform-targeted reads
- `src/repetition/hunter/source.clj` — build source units with `:platform` and pass options to parsing
- `src/repetition/hunter/core.clj` — thread `:platform` through path- and namespace-based analysis and preserve a mostly dialect-neutral analyzer
- `src/repetition/hunter.clj` — expose `:platform` in the public API for `hunt`, `hunt-path`, and `hunt-paths`
- `test/repetition/hunter/parse_test.clj` — parser contract tests for `.cljc`
- `test/repetition/hunter/source_test.clj` — source-unit contract tests for `.cljc`
- `test/repetition/hunter/core_test.clj` — integration tests for path-based and namespace-based `.cljc` analysis
- `test/repetition/hunter/sample_cljc.cljc` — replace the simple fixture with a branch-sensitive `.cljc` fixture
- `README.md` — document `:platform` requirements and examples for `.cljc`

### Responsibilities after implementation
- `parse.clj` owns dialect detection, platform validation, target resolution, and platform-specific parser options
- `source.clj` owns source-unit creation from paths plus platform propagation
- `core.clj` owns orchestration and analysis, not reader-conditional semantics
- `hunter.clj` owns the user-facing API surface and error-forwarding behavior
- tests prove branch selection, clear failures, and entry-point consistency

---

### Task 1: Add failing `.cljc` contract tests and a branch-sensitive fixture

**Files:**
- Modify: `test/repetition/hunter/sample_cljc.cljc`
- Modify: `test/repetition/hunter/parse_test.clj`
- Modify: `test/repetition/hunter/source_test.clj`
- Modify: `test/repetition/hunter/core_test.clj`

- [ ] **Step 1: Replace the `.cljc` fixture with explicit shared, `:clj`, and `:cljs` branches**

```clojure
(ns repetition.hunter.sample-cljc)

(defn shared-a [x]
  (-> x first second))

(defn shared-b [y]
  (-> y first second))

#?(:clj
   (do
     (defn clj-only-a [x]
       (-> x rest next))

     (defn clj-only-b [y]
       (-> y rest next)))
   :cljs
   (do
     (defn cljs-only-a [x]
       (-> x last butlast))

     (defn cljs-only-b [y]
       (-> y last butlast))))
```

- [ ] **Step 2: Add parser tests that fail until `.cljc` requires `:platform` and selects the correct branch**

```clojure
(deftest parse-cljc-file-requires-platform
  (is (thrown-with-msg?
        clojure.lang.ExceptionInfo
        #"CLJC analysis requires :platform"
        (parse/read-forms "test/repetition/hunter/sample_cljc.cljc" {}))))

(deftest parse-cljc-file-selects-clj-branch
  (let [forms (parse/read-forms "test/repetition/hunter/sample_cljc.cljc" {:platform :clj})
        printed (pr-str forms)]
    (is (re-find #"clj-only-a" printed))
    (is (re-find #"clj-only-b" printed))
    (is (not (re-find #"cljs-only-a" printed)))))

(deftest parse-cljc-file-selects-cljs-branch
  (let [forms (parse/read-forms "test/repetition/hunter/sample_cljc.cljc" {:platform :cljs})
        printed (pr-str forms)]
    (is (re-find #"cljs-only-a" printed))
    (is (re-find #"cljs-only-b" printed))
    (is (not (re-find #"clj-only-a" printed)))))

(deftest parse-rejects-invalid-platform
  (is (thrown-with-msg?
        clojure.lang.ExceptionInfo
        #"Invalid :platform :foo"
        (parse/read-forms "test/repetition/hunter/sample_cljc.cljc" {:platform :foo}))))
```

- [ ] **Step 3: Add source-unit tests that fail until `:platform` is preserved in the source unit**

```clojure
(deftest source-unit-from-cljc-path-requires-platform
  (is (thrown-with-msg?
        clojure.lang.ExceptionInfo
        #"CLJC analysis requires :platform"
        (source/source-unit-from-path "test/repetition/hunter/sample_cljc.cljc" {}))))

(deftest source-unit-from-cljc-path-preserves-platform
  (let [unit (source/source-unit-from-path "test/repetition/hunter/sample_cljc.cljc" {:platform :cljs})]
    (is (= :cljc (:dialect unit)))
    (is (= :cljs (:platform unit)))
    (is (re-find #"cljs-only-a" (pr-str (:forms unit))))))
```

- [ ] **Step 4: Add integration tests that fail until path-based and namespace-based analysis respect the same `.cljc` contract**

```clojure
(deftest results-from-cljc-path-requires-platform
  (is (thrown-with-msg?
        clojure.lang.ExceptionInfo
        #"CLJC analysis requires :platform"
        (rep/results-from-paths ["test/repetition/hunter/sample_cljc.cljc"] {}))))

(deftest results-from-cljc-path-differ-by-platform
  (let [clj-results  (rep/results-from-paths ["test/repetition/hunter/sample_cljc.cljc"] {:platform :clj})
        cljs-results (rep/results-from-paths ["test/repetition/hunter/sample_cljc.cljc"] {:platform :cljs})
        clj-printed  (pr-str clj-results)
        cljs-printed (pr-str cljs-results)]
    (is (re-find #"rest" clj-printed))
    (is (re-find #"last" cljs-printed))
    (is (not= clj-printed cljs-printed))))

(deftest results-from-cljc-namespace-requires-platform
  (require 'repetition.hunter.sample-cljc)
  (is (thrown-with-msg?
        clojure.lang.ExceptionInfo
        #"CLJC analysis requires :platform"
        (rep/results '[repetition.hunter.sample-cljc] {}))))
```

- [ ] **Step 5: Run the focused tests and confirm the new assertions fail for the expected reason**

Run:
```bash
clojure -M:test -m clojure.test repetition.hunter.parse-test repetition.hunter.source-test repetition.hunter.core-test
```

Expected:
- FAIL/ERROR showing `.cljc` does **not** yet require `:platform`
- FAIL/ERROR showing branch-specific parsing is not yet deterministic

- [ ] **Step 6: Commit the red tests and fixture update**

```bash
git add test/repetition/hunter/sample_cljc.cljc \
        test/repetition/hunter/parse_test.clj \
        test/repetition/hunter/source_test.clj \
        test/repetition/hunter/core_test.clj
git commit -m "test: define cljc platform-target contract"
```

---

### Task 2: Implement parser-level target resolution and `.cljc` validation

**Files:**
- Modify: `src/repetition/hunter/parse.clj`
- Test: `test/repetition/hunter/parse_test.clj`

- [ ] **Step 1: Add a central target resolver and clear exception messages in `parse.clj`**

```clojure
(def valid-platforms #{:clj :cljs})

(defn resolve-analysis-target
  [path {:keys [platform]}]
  (let [dialect (detect-dialect path)]
    (cond
      (and platform (not (valid-platforms platform)))
      (throw (ex-info
               (str "Invalid :platform " platform
                    " for path: " path
                    ". Expected one of: :clj, :cljs")
               {:path path :dialect dialect :platform platform}))

      (and (= :cljc dialect) (nil? platform))
      (throw (ex-info
               (str "CLJC analysis requires :platform (:clj or :cljs) for path: " path)
               {:path path :dialect dialect :platform platform}))

      :else
      {:path path
       :dialect dialect
       :platform (when (= :cljc dialect) platform)})))
```

- [ ] **Step 2: Make parser options target-aware and switch `.cljc` parsing to active-branch selection**

```clojure
(defn parser-options
  [{:keys [dialect platform]}]
  {:all true
   :row-key :line
   :col-key :column
   :end-location false
   :read-cond (if (= :cljc dialect) :allow :preserve)
   :features (case dialect
               :cljs #{:cljs}
               :cljc #{platform}
               #{:clj})})
```

- [ ] **Step 3: Update `read-forms` to accept options, resolve the target first, and parse with the computed options**

```clojure
(defn read-forms
  ([path]
   (read-forms path {}))
  ([path options]
   (let [target (resolve-analysis-target path options)]
     (with-open [r (io/reader path)]
       (edamame/parse-string-all (slurp r) (parser-options target))))))
```

- [ ] **Step 4: Run parser tests and verify they now pass**

Run:
```bash
clojure -M:test -m clojure.test repetition.hunter.parse-test
```

Expected:
- PASS for the existing `.clj` / `.cljs` tests
- PASS for the new `.cljc` missing-platform and branch-selection tests

- [ ] **Step 5: Commit the parser implementation**

```bash
git add src/repetition/hunter/parse.clj test/repetition/hunter/parse_test.clj
git commit -m "feat: require platform for cljc parsing"
```

---

### Task 3: Propagate platform-aware source units through file analysis

**Files:**
- Modify: `src/repetition/hunter/source.clj`
- Modify: `src/repetition/hunter/core.clj`
- Test: `test/repetition/hunter/source_test.clj`
- Test: `test/repetition/hunter/core_test.clj`

- [ ] **Step 1: Update source-unit construction so path-based analysis passes options to parsing and preserves `:platform`**

```clojure
(defn source-unit-from-path
  ([path]
   (source-unit-from-path path {}))
  ([path options]
   (let [{:keys [dialect platform] :as target}
         (parse/resolve-analysis-target path options)
         forms (parse/read-forms path options)]
     {:path path
      :dialect dialect
      :platform platform
      :forms forms
      :ns-name (ns-name-from-forms forms)})))

(defn source-units-from-paths
  ([paths]
   (source-units-from-paths paths {}))
  ([paths options]
   (mapv #(source-unit-from-path % options) paths)))
```

- [ ] **Step 2: Thread `:platform` through `results-from-paths` and make `analysis-context-for-source` include it**

```clojure
(defn analysis-context-for-source
  [{:keys [dialect platform]}]
  {:dialect dialect
   :platform platform
   :known-vars special-symbols})

(defn results-from-paths
  [paths options]
  (results-from-source-units (source/source-units-from-paths paths options) options))
```

- [ ] **Step 3: Run source and path-based integration tests and verify `.cljc` behavior is now deterministic**

Run:
```bash
clojure -M:test -m clojure.test repetition.hunter.source-test repetition.hunter.core-test
```

Expected:
- PASS for new source-unit `:platform` assertions
- PASS for `results-from-paths` missing-platform failure
- PASS for different outputs between `:clj` and `:cljs` on the `.cljc` fixture

- [ ] **Step 4: Commit the source-unit and path-based analysis changes**

```bash
git add src/repetition/hunter/source.clj \
        src/repetition/hunter/core.clj \
        test/repetition/hunter/source_test.clj \
        test/repetition/hunter/core_test.clj
git commit -m "feat: propagate platform through source-unit analysis"
```

---

### Task 4: Extend namespace-based and public APIs to enforce the same `.cljc` contract

**Files:**
- Modify: `src/repetition/hunter/core.clj`
- Modify: `src/repetition/hunter.clj`
- Test: `test/repetition/hunter/core_test.clj`

- [ ] **Step 1: Update namespace-based reading in `core.clj` so `.cljc` inputs can require and receive `:platform`**

```clojure
(defn- read-all
  ([readable]
   (read-all readable {}))
  ([readable options]
   (let [path (if (string? readable) readable (.getPath readable))]
     (parse/read-forms path options))))

(defn results
  [nss {:keys [sort] :as options}]
  (let [files (for [n nss
                    :let [f (ns->readable n)]]
                (find-all-generic (read-all f options) n n))]
    (->> (create-repetition-map (first files) (rest files))
         (filter-results (:filter options))
         (sort-results sort))))
```

- [ ] **Step 2: Expose `:platform` in the user-facing API and preserve current behavior for non-`.cljc` inputs**

```clojure
(defn hunt
  [nss & {:keys [sort filter platform]
          :or {sort :complexity}}]
  (let [nss (if (list? nss) nss (list nss))]
    (try
      (rep/check-file nss {:sort sort :filter filter :platform platform})
      (catch Exception e
        (println "Hunt failed")
        (println (.getMessage e))))))

(defn hunt-path
  [path & {:keys [sort filter platform]
           :or {sort :complexity}}]
  (try
    (rep/print-results (rep/results-from-paths [path] {:sort sort :filter filter :platform platform}))
    (catch Exception e
      (println "Hunt failed")
      (println (.getMessage e)))))

(defn hunt-paths
  [paths & {:keys [sort filter platform]
            :or {sort :complexity}}]
  (try
    (rep/print-results (rep/results-from-paths paths {:sort sort :filter filter :platform platform}))
    (catch Exception e
      (println "Hunt failed")
      (println (.getMessage e)))))
```

- [ ] **Step 3: Add or adjust tests so namespace-based `.cljc` analysis now matches the path-based contract**

```clojure
(deftest results-from-cljc-namespace-with-platform
  (require 'repetition.hunter.sample-cljc)
  (let [results (rep/results '[repetition.hunter.sample-cljc] {:platform :clj})
        printed (pr-str results)]
    (is (seq results))
    (is (re-find #"rest" printed))))
```

- [ ] **Step 4: Run the core integration tests and verify public/legacy consistency**

Run:
```bash
clojure -M:test -m clojure.test repetition.hunter.core-test
```

Expected:
- PASS for path-based `.cljc` tests
- PASS for namespace-based `.cljc` missing-platform error
- PASS for namespace-based `.cljc` success when `:platform` is supplied

- [ ] **Step 5: Commit the API and namespace-path updates**

```bash
git add src/repetition/hunter/core.clj src/repetition/hunter.clj test/repetition/hunter/core_test.clj
git commit -m "feat: enforce cljc platform contract across APIs"
```

---

### Task 5: Document `.cljc` platform usage and run full verification

**Files:**
- Modify: `README.md`
- Test: `test/repetition/hunter/parse_test.clj`
- Test: `test/repetition/hunter/source_test.clj`
- Test: `test/repetition/hunter/core_test.clj`

- [ ] **Step 1: Update the README examples and behavior notes for `.cljc`**

```markdown
### File-based analysis (new)
The project now parses source files through [edamame](https://github.com/borkdude/edamame), which makes the analysis path friendly to `.clj`, `.cljs`, and `.cljc` files.

```clojure
(require 'repetition.hunter)

(repetition.hunter/hunt-path "src/my_app/core.cljs")

(repetition.hunter/hunt-path "src/my_app/shared.cljc"
                            :platform :clj)

(repetition.hunter/hunt-paths ["src/my_app/shared.cljc"
                               "src/my_app/ui.cljs"]
                              :platform :cljs)
```

For `.cljc`, you must pass `:platform :clj` or `:platform :cljs`.
```

- [ ] **Step 2: Run the full relevant test suite**

Run:
```bash
clojure -M:test -m clojure.test repetition.hunter.parse-test repetition.hunter.source-test repetition.hunter.core-test
```

Expected:
- all tests PASS
- no `.cljc` entry point succeeds without explicit `:platform`
- `.cljc` parsing differs correctly between `:clj` and `:cljs`

- [ ] **Step 3: Optionally run the Lein test command if the project still supports it in CI/user workflows**

Run:
```bash
lein test
```

Expected:
- PASS if Lein remains configured for the current environment
- if Lein is not available, note that the Clojure CLI suite above is the verified path

- [ ] **Step 4: Commit docs and any final test adjustments**

```bash
git add README.md test/repetition/hunter/parse_test.clj test/repetition/hunter/source_test.clj test/repetition/hunter/core_test.clj
git commit -m "docs: describe cljc platform-target analysis"
```

- [ ] **Step 5: Final verification snapshot before completion**

Run:
```bash
git status --short
clojure -M:test -m clojure.test repetition.hunter.parse-test repetition.hunter.source-test repetition.hunter.core-test
```

Expected:
- clean working tree, or only intentional uncommitted changes outside this plan
- verified passing tests shown in terminal output

---

## Self-Review

### Spec coverage
- Explicit `.cljc` platform requirement: covered in Tasks 1, 2, and 4
- Parser/source boundary as the decision point: covered in Tasks 2 and 3
- Propagate `:platform` through source units and analysis context: covered in Task 3
- Any reachable entry point must fail clearly without `:platform`: covered in Tasks 1 and 4
- Reader-conditional branch selection must be tested: covered in Task 1 and verified through Tasks 2–5
- Documentation updates for `.cljc` usage: covered in Task 5

### Placeholder scan
- No `TODO`, `TBD`, or “implement later” placeholders remain
- All code-changing steps include concrete code blocks
- All verification steps include exact commands and expected outcomes

### Type consistency
- `:platform` is consistently named `:platform`
- Valid platform values are consistently `:clj` and `:cljs`
- The main plumbing path remains `read-forms` → `source-unit-from-path` → `results-from-paths`
