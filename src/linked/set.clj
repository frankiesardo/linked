(ns linked.set
  (:use [linked.map :only [linked-map]])
  (:import (clojure.lang IPersistentSet
                         IPersistentCollection
                         IPersistentVector
                         IFn
                         ILookup
                         Associative
                         Counted
                         Seqable
                         SeqIterator
                         MapEquivalence
                         MapEntry)
           (java.util Set
                      Map$Entry)
           (java.lang Iterable)))


(deftype LinkedSet [l-map]
  IPersistentSet
  (disjoin [_ k]
           (LinkedSet. (.without l-map k)))
  (contains [_ k]
            (.containsKey l-map k))
  (get [_ k]
       (.valAt l-map k))

  Set

  Counted

  IPersistentCollection
  (count [_]
         (.count l-map))
  (cons [_ o]
        (LinkedSet. (.cons l-map [o o])))
  (empty [_]
         (linked-set))
  (equiv [this o]
         (and (= (.count this) (count o))
              (every? (fn [e] (contains? o e))
                      (.seq this))))

  Seqable
  (seq [_]
       (map first (.seq l-map)))

  IFn
  (invoke [_ k]
          (.valAt l-map k))
  )

(defmethod print-method LinkedSet [o ^java.io.Writer w]
  (.write w "#linked/set ")
  (print-method (seq o) w))

(def ^{:private true,
       :tag LinkedSet} empty-linked-set (LinkedSet. (linked-map)))

(defn linked-set
  ([] empty-linked-set)
  ([& xs] (into (linked-set) xs)))


