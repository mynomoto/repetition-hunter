(ns repetition.hunter.sample-cljc)

(defn shared-a [x]
  (-> x first second))

(defn shared-b [y]
  (-> y first second))

#?(:clj
   (do
     (defn clj-only-a [x]
       (-> x rest next))

     (defn clj-only-b [y]
       (-> y rest next)))
   :cljs
   (do
     (defn cljs-only-a [x]
       (-> x last butlast))

     (defn cljs-only-b [y]
       (-> y last butlast))))