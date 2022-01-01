(ns repetition.hunter.core
  (:require
    [clojure.java.io :as io]
    [clojure.pprint :as pprint]
    [clojure.string :as str]
    [clojure.walk :as walk])
  (:import
    (clojure.lang
      LineNumberingPushbackReader)
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


(def exclusion-words
  #{"if" "catch" "try" "throw" "finally" "do" "quote" "var" "recur"})


(defn find-unbound-vars
  "Given a form and a namespace returns a set of symbols that are not bound
  in the namespace."
  [f ns]
  (->> (flatten f)
      (filter symbol?)
      distinct
      (remove (fn [sym]
                (let [sym-str (name sym)]
                  (or
                    (and (some #(= \. %) sym-str)
                      (not-any? #(= \/ %) sym-str))
                    (ns-resolve ns sym)
                    (exclusion-words sym-str)
                    (= \. (first sym-str))
                    (= \. (last sym-str))))))
      (into [])))

(defn make-generic
  "Given a namespace and a form returns a generic form"
  [ns f]
  (let [replacements (symbol-replacements (find-unbound-vars f ns))
        new-form (walk/postwalk #(make-generic-sub-form % replacements) f)]
    (if-some  [m (meta f)]
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
  "Given a seq of forms returns a seq of all generic subforms. It ignores
  forms of one element. It also ignores forms in the namespace declaration."
  [exp ns]
  (->> exp
    (mapcat expression-breaker)
    (filter #(and (coll? %) (> (count %) 1)))
    (map #(make-generic ns %))
    (remove #(nil? (:line (meta %))))
    (map #(vary-meta % assoc :ns (name ns)))))


(def ^:private eof (Object.))


(defn- read-file
  "Generate a lazy sequence of top level forms from a
   LineNumberingPushbackReader."
  [^LineNumberingPushbackReader r]
  (let [do-read (fn do-read
                  []
                  (lazy-seq
                    (let [form (read r false eof)]
                      (when-not (= form eof)
                        (cons form (do-read))))))]
    (do-read)))


(def ^:private default-data-reader-binding
  (if-let [dr (resolve '*default-data-reader-fn*)]
    {dr (fn [_tag val] val)}
    {}))


(defn- read-all
  "Given a reader returns a seq of all forms in the reader."
  [readable]
  (with-open [reader (io/reader readable)]
    (with-bindings default-data-reader-binding
        (doall
          (for [f (read-file (LineNumberingPushbackReader. reader))]
            f)))))


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


(defn results
  "Given a seq of namespaces and a map of options returns a seq of
   repetitions in the corresponding file with options."
  [nss {:keys [sort] :as options}]
  (let  [files (for [n nss
                     :let [f (ns->readable n)]]
                 (find-all-generic (read-all f) n))]
    (->> (create-repetition-map (first files) (rest files))
      (filter-results (:filter options))
      (sort-results sort))))


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


(defn check-file
  "Given a seq of namespaces and a map of options prints repetitions in the
  corresponding file with options."
  [nss options]
  (print-results (results nss options)))
