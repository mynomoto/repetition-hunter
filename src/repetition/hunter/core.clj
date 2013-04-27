(ns repetition.hunter.core
  (:require [clojure.walk :as walk]
            [clojure.java.io :as io]
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
       (remove #(or (ns-resolve ns %) (exclusion-words (name %)) (= \. (first (name %))) (= \. (last (name %)))))
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

(defn- unmake-generic [generic-form]
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

(defn- code-repetition
  "Given a seq of forms returns a vector of repeated forms. It ignores
  forms of one element. It also ignores forms in the namespace declaration."
  [exp ns]
  (->> exp
       (filter #(and (coll? %) (> (count %) 1)))
       (remove #(or (= (first %) :require) (= (first %) :use) (= (first %) :import)))
       (map expression-breaker)
       (apply concat)
       (filter #(and (coll? %) (> (count %) 1)))
       (map (partial make-generic ns))
       (group-by identity)
       (map #(hash-map
              :generic (first %)
              :complexity (count (flatten (first %)))
              :count (count (second %))
              :original (map (juxt meta unmake-generic) (second %))))
       (filter #(and (> (count (:original %)) 1)
                     (> (count (:generic %)) 2)
                     (some :line (map first (:original %)))
                     #_(some coll? (:generic %))))
       (map #(dissoc % :generic))))

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
  (when (resolve '*default-data-reader-fn*)
    {(resolve '*default-data-reader-fn*) (fn [tag val] val)}))

(defn- read-all
  "Given a reader returns a seq of all forms in the reader."
  [reader]
  (apply concat
         (for [f (read-file (LineNumberingPushbackReader. reader))]
           f)))

(defn- sort-results
  "Given a sort order and a seq of results returns a sorted seq of results.
  If the sort order is nil, it defaults to :complexity."
  [sort-order r]
  (sort-by (or (first sort-order) :complexity) r))

(defn- print-results
  "Given a seq of results prints the formated results"
  [sr]
  (doseq [r sr]
    (println (str (:count r) " repetitions of complexity " (:complexity r)))
    (newline)
    (doseq [o (:original r)]
      (println (str "On line " (:line (first o)) ":"))
      (pprint/pprint (second o))
      (newline))
    (println "======================================================================")
    (newline)))

(defn check-file
  "Given a file and a namespace prints repetitions in the file. May also
  receive a optional sort order to print sorted results."
  [source-file ns & sort-order]
  (with-open [reader (io/reader source-file)]
    (with-bindings default-data-reader-binding
      (print-results
       (sort-results sort-order
        (code-repetition (read-all reader) ns))))))

