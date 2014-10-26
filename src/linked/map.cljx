(ns linked.map
  (:require [clojure.string :as string]
            #+cljs [cljs.reader :as reader])
  #+clj (:import (clojure.lang Associative
                               Counted
                               IObj
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


(declare empty-linked-map)

(defn linked-map
  ([] empty-linked-map)
  ([coll] (into empty-linked-map coll))
  ([k v & more] (apply assoc empty-linked-map k v more)))


(defrecord Node [key value left right])

(declare assoc*)
(declare dissoc*)
(declare seq*)
(declare rseq*)

#+clj
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
    (with-meta empty-linked-map (meta delegate-map)))
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

  IObj
  (meta [this]
    (.meta ^IObj delegate-map))
  (withMeta [this m]
    (LinkedMap. head-node tail-node (.withMeta ^IObj delegate-map m)))

  ;; IEditableCollection

  Object
  (toString [this]
    (str "{" (string/join ", " (for [[k v] this] (str k " " v))) "}"))
  (equals [this other]
    (.equiv this other))
  (hashCode [this]
    (hash (into {} this))))

#+clj
(defmethod print-method LinkedMap [o ^java.io.Writer w]
  (.write w "#linked/map ")
  (.write w (.toString o)))

#+cljs
(deftype LinkedMap [head-node tail-node delegate-map]
  Object
  (toString [coll]
    (str "{" (string/join ", " (for [[k v] coll] (str k " " v))) "}"))
  (equiv [this other]
    (-equiv this other))

  ICloneable
  (-clone [_]
    (LinkedMap. head-node tail-node delegate-map))

  IWithMeta
  (-with-meta [coll meta]
    (LinkedMap. head-node tail-node (with-meta delegate-map meta)))

  IMeta
  (-meta [coll] (meta delegate-map))

  ICollection
  (-conj [coll entry]
    (if (vector? entry)
      (-assoc coll (-nth entry 0) (-nth entry 1))
      (loop [ret coll es (seq entry)]
        (if (nil? es)
          ret
          (let [e (first es)]
            (if (vector? e)
              (recur (-assoc ret (-nth e 0) (-nth e 1))
                     (next es))
              (throw (js/Error. "conj on a map takes map entries or seqables of map entries"))))))))

  IEmptyableCollection
  (-empty [coll] (-with-meta empty-linked-map (meta delegate-map)))

  IEquiv
  (-equiv [coll other] (equiv-map coll other))

  IHash
  (-hash [coll] (hash-unordered-coll coll))

  ISequential

  ISeqable
  (-seq [coll] (seq* coll))

  IReversible
  (-rseq [coll] (rseq* coll))

  ICounted
  (-count [coll]
    (cond
     (nil? head-node) 0
     (nil? tail-node) 1
     :else (+ 2 (count delegate-map))))

  ILookup
  (-lookup [coll k]
    (-lookup coll k nil))

  (-lookup [coll k not-found]
    (condp = k
      (:key head-node) (:value head-node)
      (:key tail-node) (:value tail-node)
      (let [v (-lookup delegate-map k not-found)]
        (if (= v not-found) not-found (:value v)))))

  IAssociative
  (-assoc [coll k v]
    (assoc* coll k v))

  (-contains-key? [coll k]
    (condp = k
      (:key head-node) true
      (:key tail-node) true
      (-contains-key? delegate-map k)))

  IMap
  (-dissoc [coll k]
    (dissoc* coll k))

  IKVReduce
  (-kv-reduce [coll f init]
    (reduce (seq coll) f init))

  IFn
  (-invoke [coll k]
    (-lookup coll k))

  (-invoke [coll k not-found]
    (-lookup coll k not-found))

  ;; IEditableCollection

  IPrintWithWriter
  (-pr-writer [coll writer opts] (-write writer (str "#linked/map " coll))))

#+cljs (reader/register-tag-parser! "linked/map" linked-map)

;;;; assoc and dissoc impl

(defn- assoc* [this k v]
  (let [{head-key :key head-value :value :as head-node } (.-head-node this)
        {tail-key :key tail-value :value :as tail-node } (.-tail-node this)
        delegate-map (.-delegate-map this)]
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
  (let [{head-key :key :as head-node} (.-head-node this)
        {tail-key :key :as tail-node} (.-tail-node this)
        delegate-map (dissoc (.-delegate-map this) k)]
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

(defn- dissoc-head-node
  [{head-right :right :as head-node} {tail-key :key :as tail-node} delegate-map]
  (if-not head-right
    (with-meta empty-linked-map (meta delegate-map))

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
  (let [{head-key :key :as head-node} (.-head-node this)
        {tail-key :key :as tail-node} (.-tail-node this)
        delegate-map (.-delegate-map this)]
    (cond
     (and head-node (= k head-key))
     (dissoc-head-node head-node tail-node delegate-map)

     (and tail-node (= k tail-key))
     (dissoc-tail-node head-node tail-node delegate-map)

     :else this)))

(defn- dissoc* [this k]
  (if-let [old-entry (find (.-delegate-map this) k)]
    (dissoc-in-delegate-map this old-entry)
    (dissoc-at-boundaries this k)))

;;;; seq and rseq impl

(defn- seq* [this]
  (let [{head-key :key :as head-node} (.-head-node this)
        {tail-key :key :as tail-node} (.-tail-node this)
        delegate-map (.-delegate-map this)]
    (letfn [(visit-node [node-key]
              (let [entry (find this node-key)]
                (if (= node-key (:key tail-node))
                  (list entry)
                  (cons entry
                        (lazy-seq (visit-node (:right (delegate-map node-key))))))))]
      (cond
       (nil? head-node) nil
       (nil? tail-node) (list (find this (:key head-node)))
       (empty? delegate-map) (list (find this (:key head-node))
                                   (find this (:key tail-node)))
       :else (cons (find this (:key head-node))
                   (lazy-seq (visit-node (:right head-node))))))))

(defn- rseq* [this]
  (let [{head-key :key :as head-node} (.-head-node this)
        {tail-key :key :as tail-node} (.-tail-node this)
        delegate-map (.-delegate-map this)]
    (letfn [(visit-node [node-key]
              (let [entry (find this node-key)]
                (if (= node-key (:key head-node))
                  (list entry)
                  (cons entry
                        (lazy-seq (visit-node (:left (delegate-map node-key))))))))]
      (cond
       (nil? head-node) nil
       (nil? tail-node) (list (find this (:key head-node)))
       (empty? delegate-map) (list
                              (find this (:key tail-node))
                              (find this (:key head-node)))
       :else (cons (find this (:key tail-node))
                   (lazy-seq (visit-node (:left tail-node))))))))

(def ^{:private true :tag LinkedMap} empty-linked-map
  (LinkedMap. nil nil (hash-map)))
