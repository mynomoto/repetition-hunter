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

(defn- find-unbound-vars
  "Given a form and a namespace returns a set of symbols that are not bound
  in the namespace."
  [f ns]
  (->> (flatten f)
       (filter symbol?)
       (filter (complement #(ns-resolve ns %)))
       (into #{})))

(defn- make-generic
  "Given a namespace and a form returns a generic form"
  [ns f]
  (let [replacements (symbol-replacements (find-unbound-vars f ns))]
    (walk/postwalk #(make-generic-sub-form % replacements)
                   f)))

(defn- maybe-replace-with-old-form [f]
  "Given a generic form, returns the original if it is in the tag."
  (if-let [n (:old (meta f))] n f))

(defn- unmake-generic [generic-form]
  "Given a generic form returns the original if possible."
  (walk/postwalk maybe-replace-with-old-form
                 generic-form))

(defn- code-repetition
  "Given a seq of forms returns a vector of repeated forms. It ignores
  forms of one element. It also ignores forms in the namespace declaration."
  [exp ns]
  (->> exp
       (filter coll?)
       (remove #(or (= (first %) :require) (= (first %) :use) (= (first %) :import)))
       (tree-seq coll? identity)
       (filter coll?)
       (map (partial make-generic ns))
       (frequencies)
       (filter #(and (> (second %) 1) (> (count (first %)) 2)))
       (sort-by second)))
       
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

(defn check-file
  "Given a file and a namespace prints repetitions in the file."
  [source-file ns]
  (with-open [reader (io/reader source-file)]
    (with-bindings default-data-reader-binding
      (pprint/pprint
        (code-repetition
          (apply concat
                 (for [f (read-file (LineNumberingPushbackReader. reader))]
                   f)) ns)))))
