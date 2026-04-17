(ns repetition.hunter.parse-test
  (:require [clojure.test :refer [deftest is]]
            [repetition.hunter.parse :as parse]))

(deftest parse-clj-file-returns-forms-with-location
  (let [forms (parse/read-forms "test/repetition/hunter/sample.clj")]
    (is (seq forms))
    (is (= 'ns (first (first forms))))
    (is (integer? (:line (meta (first forms)))))))

(deftest parse-cljs-file-returns-forms-with-location
  (let [forms (parse/read-forms "test/repetition/hunter/sample_cljs.cljs")]
    (is (seq forms))
    (is (= 'ns (first (first forms))))
    (is (integer? (:line (meta (second forms)))))))

(deftest detect-dialect-from-extension
  (is (= :clj (parse/detect-dialect "src/repetition/hunter/core.clj")))
  (is (= :cljs (parse/detect-dialect "test/repetition/hunter/sample_cljs.cljs")))
  (is (= :cljc (parse/detect-dialect "test/repetition/hunter/sample_cljc.cljc"))))

(deftest parse-cljc-file-requires-platform
  (is (thrown-with-msg?
        clojure.lang.ExceptionInfo
        #"CLJC analysis requires :platform"
        (parse/read-forms "test/repetition/hunter/sample_cljc.cljc" {}))))

(deftest parse-cljc-file-selects-clj-branch
  (let [forms (parse/read-forms "test/repetition/hunter/sample_cljc.cljc" {:platform :clj})
        printed (pr-str forms)]
    (is (re-find #"clj-only-a" printed))
    (is (re-find #"clj-only-b" printed))
    (is (not (re-find #"cljs-only-a" printed)))))

(deftest parse-cljc-file-selects-cljs-branch
  (let [forms (parse/read-forms "test/repetition/hunter/sample_cljc.cljc" {:platform :cljs})
        printed (pr-str forms)]
    (is (re-find #"cljs-only-a" printed))
    (is (re-find #"cljs-only-b" printed))
    (is (not (re-find #"clj-only-a" printed)))))

(deftest parse-rejects-invalid-platform
  (is (thrown-with-msg?
        clojure.lang.ExceptionInfo
        #"Invalid :platform :foo"
        (parse/read-forms "test/repetition/hunter/sample_cljc.cljc" {:platform :foo}))))