(ns repetition.hunter.source
  (:require [repetition.hunter.parse :as parse]))

(defn ns-name-from-forms [forms]
  (when-let [ns-form (first (filter #(and (seq? %) (= 'ns (first %))) forms))]
    (second ns-form)))

(defn source-unit-from-path [path]
  (let [forms (parse/read-forms path)]
    {:path path
     :dialect (parse/detect-dialect path)
     :forms forms
     :ns-name (ns-name-from-forms forms)}))

(defn source-units-from-paths [paths]
  (mapv source-unit-from-path paths))