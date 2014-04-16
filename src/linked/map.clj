(ns linked.map
  (:import (clojure.lang Associative
                         Counted
                         IFn
                         ILookup
                         IPersistentCollection
                         IPersistentVector
                         IPersistentMap
                         MapEntry
                         MapEquivalence
                         Reversible
                         Seqable
                         SeqIterator)
           (java.util Map
                      Map$Entry)
           (java.lang Iterable)))

; TODO
; Transient support

(defrecord Node [key value left right])

(deftype LinkedMap [head-node tail-node delegate-map]
  IPersistentMap
  (assoc [this k v]
    (cond
     (nil? head-node) (let [new-head (Node. k v nil nil)]
                        (LinkedMap. new-head nil delegate-map))
     (= k (:key head-node)) (if (= v (:value head-node))
                              this
                              (let [new-head (assoc head-node :value v)]
                                (LinkedMap. new-head tail-node delegate-map)))
     (nil? tail-node) (let [new-head (assoc head-node :left k :right k)
                            new-tail (Node. k v (:key head-node) (:key head-node))]
                        (LinkedMap. new-head new-tail delegate-map))
     (= k (:key tail-node)) (if (= v (:value tail-node))
                              this
                              (let [new-tail (assoc tail-node :value v)]
                                (LinkedMap. head-node new-tail delegate-map)))
     (contains? delegate-map k) (let [old-node (delegate-map k)]
                                  (if (= v (:value old-node))
                                    this
                                    (let [new-node (assoc old-node :value v)
                                          new-delegate-map (assoc delegate-map k new-node)]
                                      (LinkedMap. head-node tail-node new-delegate-map))))
     :else (let [new-tail (Node. k v (:key tail-node) (:key head-node))
                 old-tail (assoc tail-node :right k)
                 new-head (assoc head-node :left k)]
             (LinkedMap. new-head new-tail (assoc delegate-map (:key old-tail) old-tail)))))
  (assocEx [this k v]
           (if (.containsKey this k)
             (throw (RuntimeException. "Key already present"))
             (assoc this k v)))
  (without [this k]
    (cond
     (contains? delegate-map k) (let [without-node (delegate-map k)
                                      left-key (:left without-node)
                                      is-left-head (= left-key (:key head-node))
                                      left-node (if is-left-head nil (delegate-map left-key))
                                      right-key (:right without-node)
                                      is-right-tail (= right-key (:key tail-node))
                                      right-node (if is-right-tail nil (delegate-map right-key))]
                                  (cond
                                   (and is-left-head is-right-tail) (let [new-head (assoc head-node :right right-key)
                                                                          new-tail (assoc tail-node :left left-key)
                                                                          new-delegate-map (dissoc delegate-map k)]
                                                                      (LinkedMap. new-head new-tail new-delegate-map))
                                   is-left-head (let [new-head (assoc head-node :right right-key)
                                                      new-right (assoc right-node :left left-key)
                                                      new-delegate-map (-> delegate-map
                                                                           (dissoc k)
                                                                           (assoc right-key new-right))]
                                                  (LinkedMap. new-head tail-node new-delegate-map))
                                   is-right-tail (let [new-tail (assoc tail-node :left left-key)
                                                       new-left (assoc left-node :right right-key)
                                                       new-delegate-map (-> delegate-map
                                                                            (dissoc k)
                                                                            (assoc left-key new-left))]
                                                   (LinkedMap. head-node new-tail new-delegate-map))
                                   :else (let [new-left (assoc left-node :right right-key)
                                               new-right (assoc right-node :left left-key)
                                               new-delegate-map (-> delegate-map
                                                                    (dissoc k)
                                                                    (assoc left-key new-left, right-key new-right))]
                                           (LinkedMap. head-node tail-node new-delegate-map))))

     (and
      head-node
      (= k (:key head-node))) (cond
                               (nil? (:right head-node)) (.empty this)
                               (= (:right head-node) (:key tail-node)) (let [new-head (assoc tail-node :left nil :right nil)]
                                                                         (LinkedMap. new-head nil delegate-map))
                               :else (let [new-head (assoc (delegate-map (:right head-node)) :left (:key tail-node))
                                           new-delegate-map (dissoc delegate-map (:right head-node))]
                                       (LinkedMap. new-head tail-node new-delegate-map)))

     (and
      tail-node
      (= k (:key tail-node))) (if (= (:left tail-node) (:key head-node))
                                (let [new-head (assoc head-node :left nil :right nil)]
                                  (LinkedMap. new-head nil delegate-map))
                                (let [new-tail (assoc (delegate-map (:left tail-node)) :right (:key head-node))
                                      new-delegate-map (dissoc delegate-map (:left tail-node))]
                                  (LinkedMap. head-node new-tail new-delegate-map)))
     :else this))

  MapEquivalence

  Map
  (get [this k]
       (.valAt this k))
  (containsValue [this v]
                 (boolean (seq (filter #(= % v) (.values this)))))
  (values [this]
          (map (comp val val) (.seq this)))
  (size [_]
        (cond
         (nil? head-node) 0
         (nil? tail-node) 1
         :else (+ 2 (.size delegate-map))))

  Counted

  IPersistentCollection
  (count [this]
         (.size this))
  (cons [this o]
        (condp instance? o
          Map$Entry (let [^Map$Entry e o]
                      (.assoc this (.getKey e) (.getValue e)))
          IPersistentVector (if (= 2 (count o))
                              (.assoc this (nth o 0) (nth o 1))
                              (throw (IllegalArgumentException. "Vector arg to map conj must be a pair")))
          ;; TODO support for transient to speed up multiple assoc?
          (reduce (fn [^IPersistentMap m ^Map$Entry e]
                    (.assoc m (.getKey e) (.getValue e)))
                  this
                  o)))
  (empty [_]
         (linked-map))
  (equiv [this o]
         (and (= (.count this) (count o))
              (every? (fn [^MapEntry e]
                        (= (.val e) (get o (.key e))))
                      (.seq this))))

  Seqable
  (seq [this]
       (defn visit-node [node-key]
         (let [entry (.entryAt this node-key)]
           (if (= node-key (:key tail-node))
             (list entry)
             (cons entry (lazy-seq (visit-node (:right (delegate-map node-key))))))))
       (cond
        (nil? head-node) nil
        (nil? tail-node) (list (.entryAt this (:key head-node)))
        (empty? delegate-map) (list (.entryAt this (:key head-node)) (.entryAt this (:key tail-node)))
        :else (cons (.entryAt this (:key head-node)) (lazy-seq (visit-node (:right head-node))))))

  Iterable
  (iterator [this]
            (SeqIterator. (.seq this)))

  Associative
  (containsKey [_ k]
               (condp = k
                 (:key head-node) true
                 (:key tail-node) true
                 (.containsKey delegate-map k)))
  (entryAt [this k]
           (when (.containsKey this k)
             (MapEntry. k (.valAt this k))))

  ILookup
  (valAt [this k]
         (.valAt this k nil))
  (valAt [_ k not-found]
         (condp = k
           (:key head-node) (:value head-node)
           (:key tail-node) (:value tail-node)
           (let [v (.valAt delegate-map k not-found)]
             (if (= v not-found) not-found (:value v)))))

  IFn
  (invoke [this k]
          (.valAt this k))
  (invoke [this k not-found]
          (.valAt this k not-found))

  Object
  (toString [this]
            (str "{" (clojure.string/join ", " (for [[k v] this] (str k " " v))) "}"))
  (equals [this other]
          (.equiv this other))
  (hashCode [this]
            (reduce (fn [acc ^MapEntry e]
                      (let [k (.key e), v (.val e)]
                        (unchecked-add ^Integer acc ^Integer (bit-xor (hash k) (hash v)))))
                    0 (.seq this)))
  Reversible
  (rseq [this]
        (defn visit-node [node-key]
         (let [entry (.entryAt this node-key)]
           (if (= node-key (:key head-node))
             (list entry)
             (cons entry (lazy-seq (visit-node (:left (delegate-map node-key))))))))
       (cond
        (nil? head-node) nil
        (nil? tail-node) (list (.entryAt this (:key head-node)))
        (empty? delegate-map) (list (.entryAt this (:key tail-node)) (.entryAt this (:key head-node)))
        :else (cons (.entryAt this (:key tail-node)) (lazy-seq (visit-node (:left tail-node))))))
  )

(defmethod print-method LinkedMap [o ^java.io.Writer w]
  (.write w "#linked/map ")
  (print-method (seq o) w))

(def ^{:private true,
       :tag LinkedMap} empty-linked-map (LinkedMap. nil nil (hash-map)))

(defn linked-map
  ([] empty-linked-map)
  ([coll] (into (linked-map) coll))
  ([k v & more] (apply assoc (linked-map) k v more)))
