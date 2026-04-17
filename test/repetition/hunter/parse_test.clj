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