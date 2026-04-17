(ns repetition.hunter.parse
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [edamame.core :as edamame]))

(defn detect-dialect [path]
  (cond
    (str/ends-with? path ".cljs") :cljs
    (str/ends-with? path ".cljc") :cljc
    :else :clj))

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

(defn read-forms
  ([path]
   (read-forms path {}))
  ([path options]
   (let [target (resolve-analysis-target path options)]
     (with-open [r (io/reader path)]
       (edamame/parse-string-all (slurp r) (parser-options target))))))
