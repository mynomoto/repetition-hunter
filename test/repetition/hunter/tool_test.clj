(ns repetition.hunter.tool-test
  (:require [clojure.string :as str]
            [clojure.test :refer [deftest is testing]]
            [repetition.hunter.tool :as tool]))

(deftest hunt-accepts-single-path
  (let [output (with-out-str
                 (tool/hunt :path "test/repetition/hunter/sample_cljs.cljs"))]
    (is (str/includes? output "repetitions of complexity"))
    (is (str/includes? output "repetition.hunter.sample-cljs"))))

(deftest hunt-accepts-multiple-paths
  (let [output (with-out-str
                 (tool/hunt :paths ["test/repetition/hunter/sample.clj"
                                    "test/repetition/hunter/sample_cljs.cljs"]))]
    (is (str/includes? output "repetitions of complexity"))
    (is (or (str/includes? output "repetition.hunter.sample")
            (str/includes? output "repetition.hunter.sample-cljs")))))

(deftest hunt-requires-exactly-one-of-path-or-paths
  (is (thrown-with-msg?
        clojure.lang.ExceptionInfo
        #"Provide exactly one of :path or :paths"
        (tool/hunt)))
  (is (thrown-with-msg?
        clojure.lang.ExceptionInfo
        #"Provide exactly one of :path or :paths"
        (tool/hunt :path "a.cljs" :paths ["b.cljs"]))))

(deftest hunt-rejects-empty-paths
  (is (thrown-with-msg?
        clojure.lang.ExceptionInfo
        #":paths must be a non-empty vector of strings"
        (tool/hunt :paths []))))

(deftest hunt-rejects-invalid-sort
  (is (thrown-with-msg?
        clojure.lang.ExceptionInfo
        #"Invalid :sort :bogus"
        (tool/hunt :path "test/repetition/hunter/sample_cljs.cljs" :sort :bogus))))

(deftest hunt-passes-platform-through-for-cljc
  (let [output (with-out-str
                 (tool/hunt :path "test/repetition/hunter/sample_cljc.cljc"
                            :platform :cljs))]
    (is (str/includes? output "repetition.hunter.sample-cljc"))
    (is (str/includes? output "last"))))

(deftest hunt-fails-for-cljc-without-platform
  (is (thrown-with-msg?
        clojure.lang.ExceptionInfo
        #"CLJC analysis requires :platform"
        (tool/hunt :path "test/repetition/hunter/sample_cljc.cljc"))))