(ns repetition.hunter
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [repetition.hunter.core :as rep]))

(defn hunt
  "Given a namespace prints the repetitions found. You should require
  the namespace before using hunt."
  [ns]
  (let [file (str/replace (name ns) "." "/")
        file (str/replace file "-" "_")
        file (str "src/" file ".clj")
        file (io/file file)] 
    (try
      (rep/check-file file ns)
        (catch Exception e
          (println "Hunt failed")
          (println (.getMessage e))))))
