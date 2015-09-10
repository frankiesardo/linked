(ns linked.core
  (:refer-clojure :exclude [map set])
  (:require [linked.map :as m]
            [linked.set :as s]))

(defn map
  ([] m/empty-linked-map)
  ([& keyvals] (apply assoc m/empty-linked-map keyvals)))

(defn set
  ([] s/empty-linked-set)
  ([& keys] (apply conj s/empty-linked-set keys)))
