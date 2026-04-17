(ns repetition.hunter.source
  (:require [repetition.hunter.parse :as parse]))

(defn ns-name-from-forms [forms]
  (when-let [ns-form (first (filter #(and (seq? %) (= 'ns (first %))) forms))]
    (second ns-form)))

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
