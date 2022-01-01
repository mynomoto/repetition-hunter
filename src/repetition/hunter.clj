(ns repetition.hunter
  (:require
    [repetition.hunter.core :as rep]))


(defn hunt
  "Given a namespace or seq of namespaces prints the repetitions found.
  You should require the namespace(s) before using hunt. Accepts sort and
  filter options.

  :sort accepts either :complexity or :repetition and it defaults to
  :complexity.

  :filter accepts a map. Possible key-vals: :min-repetition n,
  :min-complexity n :remove-flat true. :min-repetition defaults to 2,
  :min-complexity to 3, :remove-flat to false.

  (hunt 'your.namespace)

  (hunt '(your.namespace1 your.namespace2)
        :sort :repetition :filter {:min-complexity 5 :remove-flat true})"
  [nss & {:keys [sort filter]
          :or {sort :complexity}}]
  (let [nss (if (list? nss)
              nss
              (list nss))]
    (try
      (rep/check-file nss {:sort sort :filter filter})
      (catch Exception e
        (println "Hunt failed")
        (println (.getMessage e))))))
