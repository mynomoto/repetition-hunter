(ns repetition.hunter.source-test
  (:require [clojure.test :refer [deftest is]]
            [repetition.hunter.source :as source]))

(deftest source-unit-from-path
  (let [unit (source/source-unit-from-path "test/repetition/hunter/sample.clj")]
    (is (= "test/repetition/hunter/sample.clj" (:path unit)))
    (is (= :clj (:dialect unit)))
    (is (= 'repetition.hunter.sample (:ns-name unit)))))

(deftest source-units-from-paths
  (let [units (source/source-units-from-paths ["test/repetition/hunter/sample.clj"
                                               "test/repetition/hunter/sample_cljs.cljs"])]
    (is (= 2 (count units)))
    (is (= #{:clj :cljs} (set (map :dialect units))))))

(deftest source-unit-from-cljc-path-requires-platform
  (is (thrown-with-msg?
        clojure.lang.ExceptionInfo
        #"CLJC analysis requires :platform"
        (source/source-unit-from-path "test/repetition/hunter/sample_cljc.cljc" {}))))

(deftest source-unit-from-cljc-path-preserves-platform
  (let [unit (source/source-unit-from-path "test/repetition/hunter/sample_cljc.cljc" {:platform :cljs})]
    (is (= :cljc (:dialect unit)))
    (is (= :cljs (:platform unit)))
    (is (re-find #"cljs-only-a" (pr-str (:forms unit))))))