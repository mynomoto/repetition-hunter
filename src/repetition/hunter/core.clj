(ns repetition.hunter.core
  (:require [clojure.walk :as walk]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.pprint :as pprint])
  (:import [clojure.lang LineNumberingPushbackReader]))

(defn- symbol-replacements [symbols]
  "Given a set of symbols return a map of original symbols as keys and
  generic replacements as values."
  (into {} (map #(vector %1 (symbol (str "x_" %2))) symbols (iterate inc 0))))

(defn- construct-replacement-form
  "Given a generic form, tag it with the original form."
  [nf of]
  (with-meta
    nf
    {:old of}))

(defn- make-generic-sub-form
  "Given a form and a map of replacements return a generic form."
  [f replacements]
  (let [variable? (into #{} (keys replacements))]
    (if (variable? f)
      (construct-replacement-form (replacements f) f)
      f)))

(def exclusion-words
  #{"if" "catch" "try" "throw" "finally" "do" "quote" "var" "recur"})

(defn- find-unbound-vars
  "Given a form and a namespace returns a set of symbols that are not bound
  in the namespace."
  [f ns]
  (->> (flatten f)
       (filter symbol?)
       (remove #(or
                 (and (some (partial = \.) (name %)) (not-any? (partial = \/) (name %)))
                 (ns-resolve ns %)
                 (exclusion-words (name %))
                 (= \. (first (name %)))
                 (= \. (last (name %)))))
       (into #{})))

(defn- make-generic
  "Given a namespace and a form returns a generic form"
  [ns f]
  (let [replacements (symbol-replacements (find-unbound-vars f ns))]
    (with-meta
      (walk/postwalk #(make-generic-sub-form % replacements) f)
      (meta f))))

(defn- maybe-replace-with-old-form [f]
  "Given a generic form, returns the original if it is in the tag."
  (if-let [n (:old (meta f))] n f))

(defn- make-original [generic-form]
  "Given a generic form returns the original if possible."
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
                (catch Exception e
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
  "Given a seq of forms returns a seq of all generic subforms. It ignores
  forms of one element. It also ignores forms in the namespace declaration."
  [exp ns]
  (->> exp
       (filter #(and (coll? %) (> (count %) 1)))
       (remove #(or (= (first %) :require)
                    (= (first %) :use)
                    (= (first %) :import)
                    (= (first %) :refer-clojure)
                    (= (first %) :load)
                    (= (first %) :gen-class)))
       (map expression-breaker)
       (apply concat)
       (filter #(and (coll? %) (> (count %) 1)))
       (map (partial make-generic ns))
       (remove #(nil? (:line (meta %))))
       (map #(vary-meta % assoc :ns (name ns)))))

(def ^:private eof (Object.))

(defn- read-file
  "Generate a lazy sequence of top level forms from a
   LineNumberingPushbackReader."
  [^LineNumberingPushbackReader r]
  (let [do-read (fn do-read []
                  (lazy-seq
                    (let [form (read r false eof)]
                      (when-not (= form eof)
                        (cons form (do-read))))))]
    (do-read)))

(def ^:private default-data-reader-binding
  (if (resolve '*default-data-reader-fn*)
    {(resolve '*default-data-reader-fn*) (fn [tag val] val)}
    {}))

(defn- read-all
  "Given a reader returns a seq of all forms in the reader."
  [source-file]
  (with-open [reader (io/reader source-file)]
      (with-bindings default-data-reader-binding
        (apply concat
               (doall
                  (for [f (read-file (LineNumberingPushbackReader. reader))]
                    f))))))

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
                     (>= (:complexity %) (or (:min-complexity f) 3))))
       (filter-flat (:remove-flat f))))

(defn- print-results
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

(defn- file-from-ns
  "Given a ns returns the corresponding file."
  [ns]
  (let [f (-> ns
              name
              (str/replace "." "/")
              (str/replace "-" "_")
              (#(str % ".clj")))
        cp (filter #(or (.endsWith % "/") (.endsWith % "\\")) (map (memfn getPath) (.getURLs (java.lang.ClassLoader/getSystemClassLoader))))
        cp (map #(str/replace % "\\" "/") cp)]
    (loop [l (map #(str % f) cp)]
      (if (seq l)
        (if (.exists (io/file (first l)))
          (io/file (first l))
          (recur (rest l)))))))

(defn results
  "Given a seq of namespaces and a map of options returns a seq of
   repetitions in the corresponding file with options."
  [nss {:keys [sort] :as options}]
  (let  [files (for [n nss
                     :let [f (file-from-ns n)]]
                 (find-all-generic (read-all f) n))]
    (->> (create-repetition-map (first files) (rest files))
         (filter-results (:filter options))
         (sort-results sort))))

(defn check-file
  "Given a seq of namespaces and a map of options prints repetitions in the
  corresponding file with options."
  [nss options]
  (print-results (results nss options)))

