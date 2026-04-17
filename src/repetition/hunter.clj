(ns repetition.hunter
  (:require
   [repetition.hunter.core :as rep]))

(defn hunt
  "Given a namespace or seq of namespaces prints the repetitions found.
  You should require the namespace(s) before using hunt. Accepts sort,
  filter, and platform options.

  :sort accepts either :complexity or :repetition and it defaults to
  :complexity.

  :filter accepts a map. Possible key-vals: :min-repetition n,
  :min-complexity n :remove-flat true. :min-repetition defaults to 2,
  :min-complexity to 3, :remove-flat to false.

  :platform accepts :clj or :cljs. Required when analyzing .cljc files.

  (hunt 'your.namespace)

  (hunt '(your.namespace1 your.namespace2)
        :sort :repetition :filter {:min-complexity 5 :remove-flat true})

  (hunt '(your.namespace-cljc)
        :platform :clj)"
  [nss & {:keys [sort filter platform]
          :or {sort :complexity}}]
  (let [nss (if (list? nss)
              nss
              (list nss))]
    (try
      (rep/check-file nss {:sort sort :filter filter :platform platform})
      (catch Exception e
        (println "Hunt failed")
        (println (.getMessage e))))))

(defn hunt-path
  "Given a file path, prints the repetitions found. Supports .clj, .cljs,
  and .cljc files. For .cljc files, :platform (:clj or :cljs) is required.
  Accepts the same sort and filter options as hunt.

  (hunt-path \"src/my_app/core.cljs\")

  (hunt-path \"src/my_app/shared.cljc\" :platform :clj)

  (hunt-path \"src/my_app/core.clj\"
             :sort :repetition :filter {:min-complexity 5 :remove-flat true})"
  [path & {:keys [sort filter platform]
           :or {sort :complexity}}]
  (try
    (rep/print-results (rep/results-from-paths [path] {:sort sort :filter filter :platform platform}))
    (catch Exception e
      (println "Hunt failed")
      (println (.getMessage e)))))

(defn hunt-paths
  "Given a seq of file paths, prints the repetitions found. Supports .clj,
  .cljs, and .cljc files. For .cljc files, :platform (:clj or :cljs) is
  required. Accepts the same sort and filter options as hunt.

  (hunt-paths [\"src/my_app/core.clj\" \"src/my_app/ui.cljs\"])

  (hunt-paths [\"src/my_app/shared.cljc\"]
              :platform :cljs)"
  [paths & {:keys [sort filter platform]
            :or {sort :complexity}}]
  (try
    (rep/print-results (rep/results-from-paths paths {:sort sort :filter filter :platform platform}))
    (catch Exception e
      (println "Hunt failed")
      (println (.getMessage e)))))
