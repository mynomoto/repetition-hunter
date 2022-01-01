(ns repetition.hunter.core-test
  (:require
    [clojure.java.io :as io]
    [clojure.test :refer [deftest is]]
    [repetition.hunter.core :as rep]))


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
  (is (= [{:complexity 4
           :repetition 2
           :original [[{:line 6 :column 3 :ns "repetition.hunter.sample"}
                       '(-> y first second)]
                      [{:line 11 :column 3 :ns "repetition.hunter.sample"}
                       '(-> x first second)]]}]  
        (rep/results '[repetition.hunter.sample] {}))))
