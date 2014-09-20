(ns linked.set
  (:use [linked.map :only [linked-map]])
  (:import (clojure.lang Counted
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


(deftype LinkedSet [linked-map]
  IPersistentSet
  (disjoin [_ k]
           (LinkedSet. (.without linked-map k)))
  (contains [_ k]
            (.containsKey linked-map k))
  (get [_ k]
       (.valAt linked-map k))

  Set
  (size [_]
        (.size linked-map))

  Iterable
  (iterator [this]
            (SeqIterator. (.seq this)))

  Counted

  IPersistentCollection
  (count [_]
         (.count linked-map))
  (cons [_ o]
        (LinkedSet. (.cons linked-map [o o])))
  (empty [_]
         (linked-set))
  (equiv [this o]
         (and (= (.count this) (count o))
              (every? (fn [e] (contains? o e))
                      (.seq this))))

  Seqable
  (seq [_]
       (if-let [s (.seq linked-map)]
             (map first s)))

  Reversible
  (rseq [_]
        (if-let [s (.rseq linked-map)]
             (map first s)))

  IFn
  (invoke [_ k]
          (.valAt linked-map k))

  IObj
  (meta [this]
    (.meta ^IObj linked-map))
  (withMeta [this m]
    (LinkedSet. (.withMeta ^IObj linked-map m)))

  Object
  (toString [this]
    (str "#{" (clojure.string/join " " (map str this)) "}"))
  (hashCode [this]
    (reduce + (map hash (.seq this))))
  (equals [this other]
    (or (identical? this other)
        (and (instance? Set other)
             (let [^Set s other]
               (and (= (.size this) (.size s))
                    (every? #(.contains s %) (.seq this))))))))

(defmethod print-method LinkedSet [o ^java.io.Writer w]
  (.write w "#linked/set ")
  (if-let [s (seq o)]
    (print-method (seq o) w)
    (print-method (list) w)))

(def ^{:private true,
       :tag LinkedSet} empty-linked-set (LinkedSet. (linked-map)))

(defn linked-set
  ([] empty-linked-set)
  ([x] (if (coll? x) (into empty-linked-set x) (linked-set [x])))
  ([x & more] (apply conj empty-linked-set x more)))
