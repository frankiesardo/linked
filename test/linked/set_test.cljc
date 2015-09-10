(ns linked.set-test
  (:require [linked.core :as linked]
            #?(:clj  [clojure.test :refer :all]
               :cljs [cljs.test :refer-macros [is are testing deftest run-tests]])
            #?(:cljs [cljs.reader :refer [read-string]])))

#?(:clj
   (deftest implementations
     (let [s (linked/set)]
       (testing "Interfaces marked as implemented"
         (are [class] (instance? class s)
           clojure.lang.IPersistentSet
           clojure.lang.IPersistentCollection
           clojure.lang.Counted
           java.util.Set))
       (testing "Behavior smoke testing"
         (testing "Most operations don't change type"
           (are [object] (= (class object) (class s))
             (conj s 1 2)
             (disj s 1)
             (into s #{1 2})))
         (testing "Seq-oriented operations return nil when empty"
           (are [object] (nil? object)
             (seq s)
           (rseq s)))))))

(deftest equality
  (let [empty (linked/set)
        one-item (conj empty 1)]
    (testing "Basic symmetric equality"
      (is (= #{} empty))
      (is (= empty #{}))
      (is (= #{1} one-item))
      (is (= one-item #{1})))
    (testing "Order-insensitive comparisons"
      (let [one-way (into empty [1 2 3 4])
            other-way (into empty [3 4 1 2])
            unsorted #{1 2 3 4}]
        (is (= one-way other-way))
        (is (= one-way unsorted))
        (is (= other-way unsorted))))
    (testing "Does not blow up when given something random"
      (is (not= one-item 'baz))
      (is (not= 'baz one-item)))))

(deftest ordering
  (let [values [[:first 10]
                [:second 20]
                [:third 30]]
        s (into (linked/set) values)]
    (testing "Seq behaves like seq of a vector"
      (is (= (seq values) (seq s))))
    (testing "New values get added at the end"
      (let [entry [:fourth 40]]
        (is (= (seq (conj values entry))
               (seq (conj s entry))))))
    (testing "Re-adding keys leaves them in the same place"
      (is (= (seq s)
             (seq (conj s [:second 20])))))
    (testing "Large number of keys still sorted"
      (let [ints (range 5000)
            ordered (into s ints)]
        (= (seq ints) (seq ordered))))))

(deftest reversing
  (let [source (vec (range 1000))
        s (into (sorted-set) source)]
    (is (= (rseq s) (rseq source)))))

(deftest set-features
  (let [s (linked/set :a 1 :b 2 :c 3)]
    (testing "Keyword lookup"
      (is (= :a (:a s))))
    (testing "IFn support"
      (is (= :b (s :b))))
    (testing "Falsy lookup support"
      (is (= false (#{false 1} false))))
    (testing "Ordered disj"
      (is (= #{:a 1 2 3} (disj s :b :c))))
    (testing "meta support"
      (is (= {'a 'b} (meta (with-meta s {'a 'b})))))
    (testing "cons yields a list with element prepended"
      (is (= '(:a :a 1 :b 2 :c 3) (cons :a s))))))

(deftest object-features
  (let [s (linked/set 'a 1 :b 2)]
    (is (= "[a 1 :b 2]" (str s)))))

(deftest print-and-read-ordered
  (let [s (linked/set 1 2 9 8 7 5)]
    (is (= "#linked/set [1 2 9 8 7 5]"
           (pr-str s)))
    (let [o (read-string (pr-str s))]
      #?(:clj (is (= linked.set.LinkedSet (type o))))
      (is (= '(1 2 9 8 7 5)
             (seq o))))))
