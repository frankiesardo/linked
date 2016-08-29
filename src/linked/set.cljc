(ns linked.set
  (:require [linked.map :refer [empty-linked-map]]
            [clojure.string :as string]
    #?(:cljs [cljs.reader :as reader]))
  #?(:clj
     (:import (clojure.lang Counted
                            IObj
                            IFn
                            IHashEq
                            ILookup
                            IPersistentCollection
                            IPersistentSet
                            IPersistentVector
                            Reversible
                            Seqable
                            SeqIterator)
              (java.util Set)
              (java.lang Iterable))))

(declare empty-linked-set)

(deftype LinkedSet [linked-map]
  #?@(:clj
      [IPersistentSet
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
         empty-linked-set)
       (equiv [this other]
         (or (identical? this other)
             (and (instance? Set other)
                  (let [^Set s other]
                    (and (= (.size this) (.size s))
                         (every? #(.contains s %) (.seq this)))))))
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

       IHashEq
       (hasheq [this] (.hasheq ^IHashEq (into #{} this)))

       Object
       (toString [this]
         (str "[" (string/join " " (map str this)) "]"))
       (hashCode [this]
         (.hashCode ^Object (into #{} this)))
       (equals [this other]
         (.equiv this other))]
      :cljs
      [Object
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
       (-hash [coll] (hash (into #{} coll)))

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
                   (-write writer (str "#linked/set " (into [] coll))))]))

#?(:clj
   (defmethod print-method LinkedSet [o ^java.io.Writer w]
     (.write w "#linked/set ")
     (print-method (into [] o) w)))

(def ^{:tag LinkedSet} empty-linked-set
  (LinkedSet. empty-linked-map))

(def ->linked-set (partial into empty-linked-set))

#?(:cljs (reader/register-tag-parser! "linked/set" ->linked-set))
