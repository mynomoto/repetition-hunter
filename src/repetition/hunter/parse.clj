(ns repetition.hunter.parse
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [edamame.core :as edamame]))

(defn detect-dialect [path]
  (cond
    (str/ends-with? path ".cljs") :cljs
    (str/ends-with? path ".cljc") :cljc
    :else :clj))

(defn parser-options [path]
  {:all true
   :read-cond :preserve
   :row-key :line
   :col-key :column
   :end-location false
   :features (case (detect-dialect path)
               :cljs #{:cljs}
               :cljc #{:clj :cljs}
               #{:clj})})

(defn read-forms [path]
  (with-open [r (io/reader path)]
    (edamame/parse-string-all (slurp r) (parser-options path))))