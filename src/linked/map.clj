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

(declare empty-linked-map)
(declare assoc*)
(declare dissoc*)
(declare seq*)
(declare rseq*)

(deftype LinkedMap [head-node tail-node delegate-map]
  IPersistentMap
  (assoc [this k v]
    (assoc* this k v))
  (assocEx [this k v]
    (if (.containsKey this k)
      (throw (RuntimeException. "Key already present"))
      (assoc this k v)))
  (without [this k]
    (dissoc* this k))

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
    empty-linked-map)
  (equiv [this o]
    (and (= (.count this) (count o))
         (every? (fn [^MapEntry e]
                   (= (.val e) (get o (.key e))))
                 (.seq this))))

  Seqable
  (seq [this]
    (seq* this))

  Reversible
  (rseq [this]
    (rseq* this))

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
            0 (.seq this))))

;;;; assoc and dissoc impl

(defn- assoc* [this k v]
  (let [{head-key :key head-value :value :as head-node } (.head-node this)
        {tail-key :key tail-value :value :as tail-node } (.tail-node this)
        delegate-map (.delegate-map this)]
    (cond
     (nil? head-node)
     (let [new-head (Node. k v nil nil)]
       (LinkedMap. new-head nil delegate-map))

     (= k head-key)
     (if (= v head-value)
       this
       (let [new-head (assoc head-node :value v)]
         (LinkedMap. new-head tail-node delegate-map)))

     (nil? tail-node)
     (let [new-head (assoc head-node :left k :right k)
           new-tail (Node. k v head-key head-key)]
       (LinkedMap. new-head new-tail delegate-map))

     (= k tail-key)
     (if (= v tail-value)
       this
       (let [new-tail (assoc tail-node :value v)]
         (LinkedMap. head-node new-tail delegate-map)))

     (contains? delegate-map k)
     (if (= v (:value (delegate-map k)))
       this
       (let [new-delegate-map (assoc-in delegate-map [k :value] v)]
         (LinkedMap. head-node tail-node new-delegate-map)))

     :else
     (let [new-tail (Node. k v tail-key head-key)
           new-head (assoc head-node :left k)
           old-tail (assoc tail-node :right k)
           new-delegate-map (assoc delegate-map tail-key old-tail)]
       (LinkedMap. new-head new-tail new-delegate-map)))))

(defn- dissoc-in-delegate-map [this [k {left-key :left right-key :right}]]
  (let [{head-key :key :as head-node} (.head-node this)
        {tail-key :key :as tail-node} (.tail-node this)
        delegate-map (dissoc (.delegate-map this) k)]
    (cond
     (empty? delegate-map)
     (let [new-head (assoc head-node :right tail-key)
           new-tail (assoc tail-node :left head-key)]
       (LinkedMap. new-head new-tail delegate-map))

     (= left-key head-key)
     (let [new-head (assoc head-node :right right-key)
           new-delegate-map (assoc-in delegate-map [right-key :left] head-key)]
       (LinkedMap. new-head tail-node new-delegate-map))

     (= right-key tail-key)
     (let [new-tail (assoc tail-node :left left-key)
           new-delegate-map (assoc-in delegate-map [left-key :right] tail-key)]
       (LinkedMap. head-node new-tail new-delegate-map))

     :else
     (let [new-delegate-map (-> delegate-map
                                (assoc-in [left-key :right] right-key)
                                (assoc-in [right-key :left] left-key))]
       (LinkedMap. head-node tail-node new-delegate-map)))))

(defn- dissoc-head-node [{head-right :right :as head-node}
                         {tail-key :key :as tail-node} delegate-map]
  (if-not head-right
    empty-linked-map

    (if (= head-right tail-key)
      (let [new-head (assoc tail-node :left nil :right nil)]
        (LinkedMap. new-head nil delegate-map))

      (let [new-head (assoc (delegate-map head-right) :left tail-key)
            new-delegate-map (dissoc delegate-map head-right)]
        (LinkedMap. new-head tail-node new-delegate-map)))))

(defn- dissoc-tail-node [{head-key :key :as head-node}
                         {tail-left :left :as tail-node} delegate-map]
  (if (= tail-left head-key)
    (let [new-head (assoc head-node :left nil :right nil)]
      (LinkedMap. new-head nil delegate-map))

    (let [new-tail (assoc (delegate-map tail-left) :right head-key)
          new-delegate-map (dissoc delegate-map tail-left)]
      (LinkedMap. head-node new-tail new-delegate-map))))

(defn- dissoc-at-boundaries [this k]
  (let [{head-key :key :as head-node} (.head-node this)
        {tail-key :key :as tail-node} (.tail-node this)
        delegate-map (.delegate-map this)]
    (cond
     (and head-node (= k head-key))
     (dissoc-head-node head-node tail-node delegate-map)

     (and tail-node (= k tail-key))
     (dissoc-tail-node head-node tail-node delegate-map)

     :else this)))

(defn- dissoc* [this k]
  (if-let [old-entry (find (.delegate-map this) k)]
    (dissoc-in-delegate-map this old-entry)
    (dissoc-at-boundaries this k)))

;;;; seq and rseq impl

(defn- seq* [this]
  (let [{head-key :key :as head-node} (.head-node this)
        {tail-key :key :as tail-node} (.tail-node this)
        delegate-map (.delegate-map this)]
    (letfn [(visit-node [node-key]
              (let [entry (.entryAt this node-key)]
                (if (= node-key (:key tail-node))
                  (list entry)
                  (cons entry (lazy-seq (visit-node (:right (delegate-map node-key))))))))]
      (cond
       (nil? head-node) nil
       (nil? tail-node) (list (.entryAt this (:key head-node)))
       (empty? delegate-map) (list (.entryAt this (:key head-node)) (.entryAt this (:key tail-node)))
       :else (cons (.entryAt this (:key head-node)) (lazy-seq (visit-node (:right head-node))))))))

(defn- rseq* [this]
  (let [{head-key :key :as head-node} (.head-node this)
        {tail-key :key :as tail-node} (.tail-node this)
        delegate-map (.delegate-map this)]
    (letfn [(visit-node [node-key]
              (let [entry (.entryAt this node-key)]
                (if (= node-key (:key head-node))
                  (list entry)
                  (cons entry (lazy-seq (visit-node (:left (delegate-map node-key))))))))]
      (cond
       (nil? head-node) nil
       (nil? tail-node) (list (.entryAt this (:key head-node)))
       (empty? delegate-map) (list (.entryAt this (:key tail-node)) (.entryAt this (:key head-node)))
       :else (cons (.entryAt this (:key tail-node)) (lazy-seq (visit-node (:left tail-node))))))))

;;;; apis

(defmethod print-method LinkedMap [o ^java.io.Writer w]
  (.write w "#linked/map ")
  (if-let [s (seq o)]
    (print-method (seq o) w)
    (print-method (list) w)))

(def ^{:private true :tag LinkedMap} empty-linked-map
  (LinkedMap. nil nil (hash-map)))

(defn linked-map
  ([] empty-linked-map)
  ([coll] (into empty-linked-map coll))
  ([k v & more] (apply assoc empty-linked-map k v more)))
