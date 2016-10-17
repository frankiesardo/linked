(ns linked.map
  (:require [clojure.string :as string]
    #?(:cljs [cljs.reader :as reader]))
  #?(:clj
     (:import (clojure.lang Associative
                            Counted
                            IObj
                            IFn
                            IHashEq
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
              (java.lang Iterable))))

(declare empty-linked-map)

(defrecord Node [value left right])

(declare assoc*)
(declare dissoc*)
(declare seq*)
(declare rseq*)

(deftype LinkedMap [head delegate-map]
  #?@(:clj
      [IPersistentMap
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
         (map val (.seq this)))
       (size [_]
         (count delegate-map))

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
         (and (instance? Map o)
              (= (.count this) (count o))
              (every? (fn [[k v :as kv]]
                        (= kv (find o k)))
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
         (contains? delegate-map k))
       (entryAt [this k]
         (when (.containsKey this k)
           (MapEntry. k (.valAt this k))))

       ILookup
       (valAt [this k]
         (.valAt this k nil))
       (valAt [_ k not-found]
         (if-let [entry (find delegate-map k)]
           (-> entry val :value)
           not-found))

       IFn
       (invoke [this k]
         (.valAt this k))
       (invoke [this k not-found]
         (.valAt this k not-found))

       IObj
       (meta [this]
         (.meta ^IObj delegate-map))
       (withMeta [this m]
         (LinkedMap. head (.withMeta ^IObj delegate-map m)))

       ;; IEditableCollection

       IHashEq
       (hasheq [this] (.hasheq ^IHashEq (into {} this)))

       Object
       (toString [this]
         (str "{" (string/join ", " (for [[k v] this] (str k " " v))) "}"))
       (equals [this other]
         (.equiv this other))
       (hashCode [this]
         (.hashCode ^Object (into {} this)))]
      :cljs
      [Object
       (toString [coll]
                 (str "{" (string/join ", " (for [[k v] coll] (str k " " v))) "}"))
       (equiv [this other]
              (-equiv this other))

       ICloneable
       (-clone [_]
               (LinkedMap. head delegate-map))

       IWithMeta
       (-with-meta [coll meta]
                   (LinkedMap. head (with-meta delegate-map meta)))

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
       (-hash [coll] (hash (into {} coll)))

       ISequential

       ISeqable
       (-seq [coll] (seq* coll))

       IReversible
       (-rseq [coll] (rseq* coll))

       ICounted
       (-count [coll]
               (count delegate-map))

       ILookup
       (-lookup [coll k]
                (-lookup coll k nil))

       (-lookup [coll k not-found]
                (if-let [entry (find delegate-map k)]
                        (-> entry val :value)
                        not-found))

       IAssociative
       (-assoc [coll k v]
               (assoc* coll k v))

       (-contains-key? [coll k]
                       (contains? delegate-map k))

       IMap
       (-dissoc [coll k]
                (dissoc* coll k))

       IKVReduce
       (-kv-reduce [coll f init]
                   (reduce #(apply (partial f %1) %2) init (seq coll)))

       IFn
       (-invoke [coll k]
                (-lookup coll k))

       (-invoke [coll k not-found]
                (-lookup coll k not-found))

       ;; IEditableCollection

       IPrintWithWriter
       (-pr-writer [coll writer opts] (-write writer (str "#linked/map " (into [] coll))))]))

#?(:clj
   (defmethod print-method LinkedMap [o ^java.io.Writer w]
     (.write w "#linked/map ")
     (.write w (pr-str (into [] o)))))

(defn- assoc* [^LinkedMap this k v]
  (let [head (.-head this)
        delegate-map (.-delegate-map this)]
    (if-let [entry (find delegate-map k)]
      (LinkedMap. head (assoc-in delegate-map [k :value] v))
      (if (empty? delegate-map)
        (LinkedMap. k (assoc delegate-map k (Node. v k k)))
        (let [tail (get-in delegate-map [head :left])]
          (LinkedMap. head (-> delegate-map
                               (assoc k (Node. v tail head))
                               (assoc-in [head :left] k)
                               (assoc-in [tail :right] k))))))))

(defn- dissoc* [^LinkedMap this k]
  (let [head (.-head this)
        delegate-map (.-delegate-map this)]
    (if-let [entry (find delegate-map k)]
      (if (= 1 (count delegate-map))
        (empty this)
        (let [rk (-> entry val :right)
              lk (-> entry val :left)
              head (if (= k head) rk head)]
          (LinkedMap. head (-> delegate-map
                               (dissoc k)
                               (assoc-in [rk :left] lk)
                               (assoc-in [lk :right] rk)))))
      this)))


;;;; seq and rseq impl

(defn- map-entry [k v]
  #?(:clj  (MapEntry. k v)
     :cljs (vector k v)))

(defn- visit-node [delegate-map current last direction]
  (let [[k node] (find delegate-map current)
        entry (map-entry k (:value node))
        next (direction node)]
    (if (= current last)
      (list entry)
      (cons entry (lazy-seq (visit-node delegate-map next last direction))))))

(defn- seq* [^LinkedMap this]
  (let [delegate-map (.-delegate-map this)
        head (.-head this)
        tail (get-in delegate-map [head :left])]
    (when (seq delegate-map)
      (visit-node delegate-map head tail :right))))

(defn- rseq* [^LinkedMap this]
  (let [delegate-map (.-delegate-map this)
        head (.-head this)
        tail (get-in delegate-map [head :left])]
    (when (seq delegate-map)
      (visit-node delegate-map tail head :left))))

(def ^{:tag LinkedMap} empty-linked-map
  (LinkedMap. nil (hash-map)))

(def ->linked-map (partial into empty-linked-map))

#?(:cljs (reader/register-tag-parser! "linked/map" ->linked-map))
