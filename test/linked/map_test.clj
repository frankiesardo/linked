(ns linked.map-test
  (:use clojure.test
        [linked.map :only [linked-map]])
  (:import linked.map.LinkedMap))

(deftest equality
  (let [empty-map (linked-map)
        one-item (assoc empty-map 1 2)]
    (testing "Basic symmetric equality"
      (is (= {} empty-map))
      (is (= empty-map {}))
      (is (= {1 2} one-item))
      (is (= one-item {1 2})))
    (testing "Order-insensitive comparisons"
      (let [one-way (into empty-map {1 2 3 4})
            other-way (into empty-map {3 4 1 2})
            unsorted {1 2 3 4}]
        (is (= one-way other-way))
        (is (= one-way unsorted))
        (is (= other-way unsorted))))
    (testing "Hash code sanity"
      (is (integer? (hash one-item)))
      (is (= #{one-item} (into #{} [one-item {1 2}]))))))

(deftest ordering
  (let [values [[:first 10]
                [:second 20]
                [:third 30]]
        m (linked-map values)]
    (testing "Seq behaves like on a seq of vectors"
      (is (= (seq values) (seq m))))
    (testing "New values get added at the end"
      (let [entry [:fourth 40]]
        (is (= (seq (conj values entry))
               (seq (conj m entry))))))
    (testing "Changing old mappings leaves them at the same location"
      (let [vec-index [1]
            vec-key (conj vec-index 1)
            map-key (get-in values (conj vec-index 0))
            new-value 5]
        (is (= (seq (assoc-in values vec-key new-value))
               (seq (assoc m map-key new-value))))))
    (testing "Large number of keys still sorted"
      (let [kvs (for [n (range 5000)]
                  [(str n) n])
            ordered (into m kvs)]
        (= (seq kvs) (seq ordered))))))

(deftest map-features
  (let [m (linked-map :a 1 :b 2 :c 3)]
    (testing "Keyword lookup"
      (is (= 1 (:a m))))
    (testing "Sequence views"
      (is (= [:a :b :c] (keys m)))
      (is (= [1 2 3] (vals m))))
    (testing "IFn support"
      (is (= 2 (m :b)))
      (is (= 'not-here (m :nothing 'not-here)))
      (is (= nil ((linked-map :x nil) :x 'not-here))))
    (testing "Get out Map.Entry"
      (is (= [:a 1] (find m :a))))
    (testing "Get out Map.Entry with falsy value"
      (is (= [:a nil] (find (linked-map :a nil) :a))))
    (testing "Ordered dissoc"
      (let [m (dissoc m :b)]
        (is (= [:a :c] (keys m)))
        (is (= [1 3] (vals m)))))
    (testing "Empty equality"
      (let [m (dissoc m :b :a :c)]
        (is (= (linked-map) m))))
    (testing "Can conj a map"
      (is (= {:a 1 :b 2 :c 3 :d 4} (conj m {:d 4}))))
    (testing "(conj m nil) returns m"
      (are [x] (= m x)
           (conj m nil)
           (merge m ())
           (into m ())))))
