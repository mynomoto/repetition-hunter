(ns repetition.hunter.core
  (:require
   [clojure.java.io :as io]
   [clojure.pprint :as pprint]
   [clojure.string :as str]
   [clojure.walk :as walk]
   [repetition.hunter.parse :as parse]
   [repetition.hunter.source :as source])
  (:import
   (java.io
    File)))

(defn symbol->ns
  [s]
  (require s)
  (the-ns s))

(defn ns->file
  [ns]
  (-> (symbol->ns ns)
      ns-publics
      vals
      (nth 0)
      meta
      :file))

(defn is-file?
  [x]
  (.isFile
   (File. x)))

(defn adjust-zipfile
  [z]
  (-> z
      (str/replace "zipfile:///" "file:/")
      (str/replace "::" "!/")))

(defn adjust-file-name
  [s]
  (cond
    (is-file? s) s
    (str/starts-with? s "zipfile:///") (adjust-zipfile s)
    :else (-> (.getResource (clojure.lang.RT/baseLoader) s)
              .getPath)))

(defn read-jar
  [jar-file-name]
  (let [[jar path] (str/split jar-file-name #"!/" 2)
        jar (clojure.string/replace-first jar #"file:" "")
        jar-file (java.util.jar.JarFile. jar)
        ba (java.io.ByteArrayOutputStream.)
        is (.getInputStream jar-file (.getJarEntry jar-file path))]
    (clojure.java.io/copy is ba)
    (java.lang.String. (.toByteArray ba))))

(defn- symbol-replacements
  "Given a set of symbols return a map of original symbols as keys and
  generic replacements as values."
  [symbols]
  (into {} (map #(vector %1 (symbol (str "x_" %2))) symbols (iterate inc 0))))

(defn construct-replacement-form
  "Given a generic form, tag it with the original form."
  [nf of]
  (let [x (with-meta
            nf
            {:old of})]
    x))

(defn- make-generic-sub-form
  "Given a form and a map of replacements return a generic form."
  [f replacements]
  (let [variable? (set (keys replacements))]
    (if (variable? f)
      (construct-replacement-form (replacements f) f)
      f)))

;; --- Static symbol analysis ---

(def special-symbols
  #{'if 'let 'letfn 'fn 'loop 'recur 'throw 'try 'catch 'finally
    'do 'quote 'var 'def 'defn 'defn- 'defmacro
    'when 'when-not 'when-let 'if-let 'if-not
    'some-> 'some->> 'cond 'cond-> 'cond->> 'as->
    'for 'doseq '-> '->> 'require 'import 'use 'in-ns
    'first 'second 'rest 'next 'nth 'get 'assoc 'dissoc 'conj 'disj
    'concat 'map 'filter 'reduce 'remove 'count 'seq 'cons 'list 'vector
    'set 'hash-map 'hash-set 'str 'int 'long 'double 'float 'boolean
    'nil? 'true? 'false? 'number? 'string? 'symbol? 'keyword? 'fn?
    'map? 'vec? 'set? 'coll? 'seq? 'list? 'vector? 'integer?
    'print 'println 'prn 'pr 'printf 'format 'subs 'split-at 'split-with
    'apply 'partial 'comp 'juxt 'identity 'constantly 'complement
    'not= 'not 'and 'or 'inc 'dec 'max 'min 'abs 'zero? 'pos? 'neg?
    'even? 'odd? 'compare 'sort 'sort-by 'reverse 'distinct 'group-by
    'partition 'partition-all 'partition-by 'take 'take-while 'drop
    'drop-while 'interleave 'interpose 'flatten 'tree-seq 'into
    'not-empty 'empty 'range 'repeat 'iterate 'cycle
    'read-string 'slurp 'spit 'line-seq 'file-seq 'resultset-seq
    're-find 're-matches 're-seq 're-pattern 're-groups})

(def exclusion-words
  #{"if" "catch" "try" "throw" "finally" "do" "quote" "var" "recur"})

(defn- locals-qualified-symbol?
  [sym]
  (and (symbol? sym) (namespace sym)))

(defn- local-binding-symbols
  "Extract locally-bound symbols from a form's binding vectors.
  Best-effort static analysis — catches fn args, let/loop bindings, etc."
  [form]
  (->> (tree-seq coll? seq form)
       (filter vector?)
       (mapcat
        (fn [v]
          (->> v
               (filter symbol?)
               (remove #{'&}))))
       set))

(defn find-unbound-vars
  "Given a form and either a namespace or an analysis context map,
  returns a vector of symbols that appear unbound.
  Namespace: uses ns-resolve (legacy behavior).
  Map with :known-vars: uses static analysis."
  [f context]
  (let [syms (->> (flatten f) (filter symbol?) distinct)]
    (if (instance? clojure.lang.Namespace context)
      ;; Legacy path: use ns-resolve
      (->> syms
           (remove (fn [sym]
                     (let [sym-str (name sym)]
                       (or
                        (and (some #(= \. %) sym-str)
                             (not-any? #(= \/ %) sym-str))
                        (ns-resolve context sym)
                        (exclusion-words sym-str)
                        (= \. (first sym-str))
                        (= \. (last sym-str))))))
           (into []))
      ;; Static path: use known-vars
      (let [{:keys [known-vars]} context
            protected (into special-symbols known-vars)]
        (->> syms
             (remove (fn [sym]
                       (let [sym-str (name sym)]
                         (or
                          (locals-qualified-symbol? sym)
                          (protected sym)
                          (and (some #(= \. %) sym-str)
                               (not-any? #(= \/ %) sym-str))
                          (= \. (first sym-str))
                          (= \. (last sym-str))))))
             (into []))))))

(defn make-generic
  "Given a context (namespace or analysis map) and a form, returns a generic form"
  [context f]
  (let [replacements (symbol-replacements (find-unbound-vars f context))
        new-form (walk/postwalk #(make-generic-sub-form % replacements) f)]
    (if-some [m (meta f)]
      (with-meta new-form m)
      new-form)))

(defn- maybe-replace-with-old-form
  "Given a generic form, returns the original if it is in the tag."
  [f]
  (if-let [n (:old (meta f))] n f))

(defn- make-original
  "Given a generic form returns the original if possible."
  [generic-form]
  (walk/postwalk maybe-replace-with-old-form
                 generic-form))

(defn- expression-breaker
  "Given an expression break return a seq of expression parts.
  If the part doesn't have metadata keep the original expression
  metadata."
  [exp]
  (let [t (tree-seq coll? identity exp)
        m (fn [ex]
            (if-not (:line (meta ex))
              (try
                (with-meta ex (meta exp))
                (catch Exception _e
                  ex))
              ex))]
    (map m t)))

(defn- create-repetition-map
  "Given seqs of generic forms make one seq and find repetitions. Returns
   a map of original forms and measures of repetition and complexity."
  [exp exps]
  (->> (apply concat exp exps)
       (group-by identity)
       (map #(hash-map
              :complexity (count (flatten (first %)))
              :repetition (count (second %))
              :original (map (juxt meta make-original) (second %))))))

(defn- find-all-generic
  "Given a seq of forms and a context (namespace or analysis map),
  returns a seq of all generic subforms. Ignores forms of one element
  and forms in the namespace declaration."
  [exp context ns-name]
  (->> exp
       (mapcat expression-breaker)
       (filter #(and (coll? %) (> (count %) 1)))
       (map #(make-generic context %))
       (remove #(nil? (:line (meta %))))
       (map #(vary-meta % assoc :ns (name ns-name)))))

(defn- read-all
  "Given a readable (file path or reader) returns a seq of all forms
  using edamame."
  ([readable]
   (read-all readable {}))
  ([readable options]
   (let [path (if (string? readable) readable (.getPath readable))]
     (parse/read-forms path options))))

(defn ns->readable
  [ns-symbol]
  (let [file-name (-> ns-symbol ns->file adjust-file-name)]
    (if (str/starts-with? file-name "file:/")
      (char-array (read-jar file-name))
      file-name)))

(defn- sort-results
  "Given a sort order and a seq of results returns a sorted seq of results.
  If the sort order is nil, it defaults to :complexity."
  [sort-order r]
  (let [so (if (= :repetition sort-order)
             (juxt :repetition :complexity)
             (juxt :complexity :repetition))]
    (sort-by so r)))

(defn- filter-flat
  "Given results filter flat forms when pred is true."
  [pred r]
  (if pred
    (filter #(some coll? (second (first (:original %)))) r)
    r))

(defn- filter-results
  "Given results and filter options returns the filtered results."
  [f r]
  (->> r
       (filter #(and (>= (:repetition %) (or (:min-repetition f) 2))
                     (>= (:complexity %) (or (:min-complexity f) 1))))
       (filter-flat (:remove-flat f))))

;; --- Source-unit-based analysis (new path) ---

(defn analysis-context-for-source
  "Build an analysis context map from a source unit."
  [{:keys [dialect platform]}]
  {:dialect dialect
   :platform platform
   :known-vars special-symbols})

(defn find-all-generic-in-source
  "Given a source unit map, returns a seq of all generic subforms."
  [{:keys [forms ns-name] :as source-unit}]
  (find-all-generic forms (analysis-context-for-source source-unit) (or ns-name 'user)))

(defn results-from-source-units
  "Given a seq of source units and options, returns a seq of repetitions."
  [source-units {:keys [sort] :as options}]
  (let [files (map find-all-generic-in-source source-units)]
    (->> (create-repetition-map (first files) (rest files))
         (filter-results (:filter options))
         (sort-results sort))))

(defn results-from-paths
  "Given a seq of file paths and options, returns a seq of repetitions."
  [paths options]
  (results-from-source-units (source/source-units-from-paths paths options) options))

;; --- Legacy namespace-based API ---

(defn results
  "Given a seq of namespaces and a map of options returns a seq of
   repetitions in the corresponding file with options.
   Uses namespace-resolve for symbol detection."
  [nss {:keys [sort] :as options}]
  (let [files (for [n nss
                    :let [f (ns->readable n)]]
                (find-all-generic (read-all f options) n n))]
    (->> (create-repetition-map (first files) (rest files))
         (filter-results (:filter options))
         (sort-results sort))))

(defn print-results
  "Given a seq of results prints the formated results"
  [sr]
  (doseq [r sr]
    (println (str (:repetition r) " repetitions of complexity " (:complexity r)))
    (newline)
    (doseq [o (:original r)]
      (println (str "Line " (:line (first o)) " - " (:ns (first o)) ":"))
      (pprint/with-pprint-dispatch pprint/code-dispatch
        (pprint/pprint (second o)))
      (newline))
    (println "======================================================================")
    (newline)))

(defn check-file
  "Given a seq of namespaces and a map of options prints repetitions in the
  corresponding file with options."
  [nss options]
  (print-results (results nss options)))