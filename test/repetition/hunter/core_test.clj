(ns repetition.hunter.core-test
  (:require
   [clojure.java.io :as io]
   [clojure.test :refer [deftest is testing]]
   [repetition.hunter.core :as rep]
   [repetition.hunter :as hunter]))

(deftest symbol->ns
  (is (= (the-ns 'repetition.hunter.core)
         (rep/symbol->ns 'repetition.hunter.core)))
  (is (thrown-with-msg? java.io.FileNotFoundException #"Could not locate abc"
                        (rep/symbol->ns 'abc))))

(deftest ns->file
  (is (re-find #"repetition/hunter/core.clj"
               (rep/ns->file 'repetition.hunter.core)))
  (is (= "clojure/string.clj"
         (rep/ns->file 'clojure.string)))
  (is (re-find #"clojure/java/io.clj"
               (rep/ns->file 'clojure.java.io))))

(deftest is-file?
  (is (= true
         (rep/is-file? "/home/y/code/repetition-hunter/src/repetition/hunter/core.clj")))
  (is (= false
         (rep/is-file? "clojure/string.clj")))
  (is (= false
         (rep/is-file? "zipfile:///home/y/.m2/repository/org/clojure/clojure/1.10.3/clojure-1.10.3.jar::clojure/java/io.clj"))))

(deftest adjust-file-name
  (is (= "src/repetition/hunter.clj"
         (rep/adjust-file-name "src/repetition/hunter.clj")))
  (is (= "file:/home/y/.m2/repository/org/clojure/clojure/1.10.3/clojure-1.10.3.jar!/clojure/string.clj"
         (rep/adjust-file-name "clojure/string.clj")))
  (is (= "file:/home/y/.m2/repository/org/clojure/clojure/1.10.3/clojure-1.10.3.jar!/clojure/java/io.clj"
         (rep/adjust-file-name "zipfile:///home/y/.m2/repository/org/clojure/clojure/1.10.3/clojure-1.10.3.jar::clojure/java/io.clj"))))

(deftest read-jar
  (is (string? (rep/read-jar
                (rep/adjust-file-name (rep/ns->file 'clojure.string)))))
  (is (string? (rep/read-jar
                (rep/adjust-file-name (rep/ns->file 'clojure.java.io))))))

(deftest readable-from-ns
  (is (seq (line-seq (io/reader (rep/ns->readable 'repetition.hunter.sample)))))
  (is (seq (line-seq (io/reader (rep/ns->readable 'clojure.string)))))
  (is (seq (line-seq (io/reader (rep/ns->readable 'clojure.java.io))))))

(deftest find-unbound-vars
  (is (= ['y]
         (rep/find-unbound-vars
          '(defn a
             [y]
             (-> y first second))
          (the-ns 'repetition.hunter.sample)))))

(deftest results
  (testing "namespace-based results find thread-expression repetitions"
    (let [r (rep/results '[repetition.hunter.sample] {})]
      (is (>= (count r) 1))
      (is (some #(= 4 (:complexity %)) r))
      (is (some #(= 2 (:repetition %)) r)))))

(deftest results-from-paths
  (testing "path-based results find thread-expression repetitions"
    (let [r (rep/results-from-paths ["test/repetition/hunter/sample.clj"] {})]
      (is (>= (count r) 1))
      (is (some #(= 4 (:complexity %)) r))
      (is (some #(= 2 (:repetition %)) r)))))

(deftest results-from-cljs-path
  (testing "CLJS file analysis finds repetitions"
    (let [results (rep/results-from-paths ["test/repetition/hunter/sample_cljs.cljs"] {})]
      (is (>= (count results) 1))
      (is (some #(= 2 (:repetition %)) results))
      (is (some #(= 4 (:complexity %)) results)))))

(deftest find-unbound-vars-static-analysis
  (testing "static analysis excludes special symbols and known vars"
    (let [result (rep/find-unbound-vars
                  '(-> y first second)
                  {:known-vars rep/special-symbols})]
      (is (not (some #{'first 'second '->} result)))
      (is (some #{'y} result)))))

(deftest hunt-path-returns-nil-on-success
  (is (nil? (hunter/hunt-path "test/repetition/hunter/sample_cljs.cljs"))))

(deftest results-from-cljc-path-requires-platform
  (is (thrown-with-msg?
       clojure.lang.ExceptionInfo
       #"CLJC analysis requires :platform"
       (rep/results-from-paths ["test/repetition/hunter/sample_cljc.cljc"] {}))))

(deftest results-from-cljc-path-differ-by-platform
  (let [clj-results  (rep/results-from-paths ["test/repetition/hunter/sample_cljc.cljc"] {:platform :clj})
        cljs-results (rep/results-from-paths ["test/repetition/hunter/sample_cljc.cljc"] {:platform :cljs})
        clj-printed  (pr-str clj-results)
        cljs-printed (pr-str cljs-results)]
    (is (re-find #"rest" clj-printed))
    (is (re-find #"last" cljs-printed))
    (is (not= clj-printed cljs-printed))))

(deftest results-from-cljc-namespace-with-platform
  (require 'repetition.hunter.sample-cljc)
  (let [results (rep/results '[repetition.hunter.sample-cljc] {:platform :clj})
        printed (pr-str results)]
    (is (seq results))
    (is (re-find #"rest" printed))))

(deftest results-from-cljc-namespace-requires-platform
  (require 'repetition.hunter.sample-cljc)
  (is (thrown-with-msg?
       clojure.lang.ExceptionInfo
       #"CLJC analysis requires :platform"
       (rep/results '[repetition.hunter.sample-cljc] {}))))