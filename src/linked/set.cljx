(ns linked.set
  (:require [linked.map :refer [linked-map]]
            [clojure.string :as string]
            #+cljs [cljs.reader :as reader])
  #+clj (:import (clojure.lang Counted
                               IObj
                               IFn
                               ILookup
                               IPersistentCollection
                               IPersistentSet
                               IPersistentVector
                               Reversible
                               Seqable
                               SeqIterator)
                 (java.util Set)
                 (java.lang Iterable)))

(declare empty-linked-set)

(defn linked-set
  ([] empty-linked-set)
  ([x] (if (coll? x) (into empty-linked-set x) (linked-set [x])))
  ([x & more] (apply conj empty-linked-set x more)))

#+clj
(deftype LinkedSet [linked-map]
  IPersistentSet
  (disjoin [_ k]
    (LinkedSet. (dissoc linked-map k)))
  (contains [_ k]
    (contains? linked-map k))
  (get [this k]
    (when (.contains this k) k))

  Set
  (size [this]
    (.count this))

  Iterable
  (iterator [this]
    (SeqIterator. (.seq this)))

  Counted

  IPersistentCollection
  (count [_]
    (count linked-map))
  (cons [this o]
    (if (contains? linked-map o)
      this
      (LinkedSet. (assoc linked-map o nil))))
  (empty [_]
    (linked-set))
  (equiv [this o]
    (and (= (.count this) (count o))
         (every? (fn [e] (contains? o e))
                 (.seq this))))

  Seqable
  (seq [_]
    (when-let [s (seq linked-map)] (map key s)))

  Reversible
  (rseq [_]
    (when-let [s (rseq linked-map)] (map key s)))

  IFn
  (invoke [this k]
    (get this k))

  IObj
  (meta [this]
    (.meta ^IObj linked-map))
  (withMeta [this m]
    (LinkedSet. (.withMeta ^IObj linked-map m)))

  Object
  (toString [this]
    (str "[" (string/join " " (map str this)) "]"))
  (hashCode [this]
    (reduce + (map hash (.seq this))))
  (equals [this other]
    (or (identical? this other)
        (and (instance? Set other)
             (let [^Set s other]
               (and (= (.size this) (.size s))
                    (every? #(.contains s %) (.seq this))))))))

#+clj
(defmethod print-method LinkedSet [o ^java.io.Writer w]
  (.write w "#linked/set ")
  (print-method (into [] (seq o)) w))


#+cljs
(deftype LinkedSet [linked-map]
  Object
  (toString [this]
    (str "[" (string/join " " (map str this)) "]"))
  (equiv [this other]
    (-equiv this other))

  ICloneable
  (-clone [_] (LinkedSet. linked-map))

  IWithMeta
  (-with-meta [coll meta] (LinkedSet. (with-meta linked-map meta)))

  IMeta
  (-meta [coll] (meta linked-map))

  ICollection
  (-conj [coll o]
    (LinkedSet. (assoc linked-map o nil)))

  IEmptyableCollection
  (-empty [coll] (with-meta empty-linked-set meta))

  IEquiv
  (-equiv [coll other]
    (and
     (set? other)
     (== (count coll) (count other))
     (every? #(contains? coll %)
             other)))

  IHash
  (-hash [coll] (hash-unordered-coll coll))

  ISeqable
  (-seq [coll] (when-let [s (seq linked-map)] (map key s)))

  IReversible
  (-rseq [coll] (when-let [s (rseq linked-map)] (map key s)))

  ISequential

  ICounted
  (-count [coll] (-count linked-map))

  ILookup
  (-lookup [coll v]
    (-lookup coll v nil))
  (-lookup [coll v not-found]
    (if (-contains-key? linked-map v)
      v
      not-found))

  ISet
  (-disjoin [coll v]
    (LinkedSet. (-dissoc linked-map v)))

  IFn
  (-invoke [coll k]
    (-lookup coll k))
  (-invoke [coll k not-found]
    (-lookup coll k not-found))

  ;; IEditableCollection

  IPrintWithWriter
  (-pr-writer [coll writer opts]
    (-write writer (str "#linked/set " (into [] (seq coll))))))

#+cljs (reader/register-tag-parser! "linked/set" linked-set)

(def ^{:private true,:tag LinkedSet} empty-linked-set
  (LinkedSet. (linked-map)))
