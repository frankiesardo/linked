(ns linked.map
  (:import (clojure.lang IPersistentMap
                         IPersistentCollection
                         IPersistentVector
                         IFn
                         ILookup
                         Associative
                         Seqable
                         SeqIterator
                         MapEquivalence
                         MapEntry)
           (java.util Map
                      Map$Entry)
           (java.lang Iterable)))

; That's how 'head' evolves:
; nil -> (node a _ nil nil) -> (node a _ b b), (node b _ a a) -> (node a _ c b), (node b _ a c), (node c _ b a)

; TODO
; Use only key as head
; Reverible Indexed Sorted interfaces
; Transient support

; Questions
; Map methods
; cons conj reader?

(defn node [key value left right] {:key key, :value value, :left left, :right right})

(deftype LinkedMap [head delegate-map]
  IPersistentMap
  (assoc [_ k v]
    (cond
     (contains? delegate-map k) (let [new-node (assoc (delegate-map k) :value v)]
                                  (if (= k (head :key))
                                    (LinkedMap. new-node (assoc delegate-map k new-node))
                                    (LinkedMap. head (assoc delegate-map k new-node))))
     (empty? delegate-map) (let [new-node (node k v nil nil )]
                             (LinkedMap. new-node (assoc delegate-map k new-node)))
     (= 1 (count delegate-map)) (let [head-key (head :key)
                                      new-node (node k v head-key head-key)
                                      new-head (node head-key (head :value) k k)]
                                  (LinkedMap. new-head (assoc delegate-map k new-node, head-key new-head)))
     :else (let [tail (delegate-map (head :left))
                 new-node (node k v (tail :key) (head :key))
                 new-head (assoc head :left k)
                 new-tail (assoc tail :right k)]
             (LinkedMap. new-head (assoc delegate-map k new-node, (head :key) new-head, (tail :key) new-tail)))))
  (assocEx [this k v]
           (if (contains? delegate-map k)
             (throw (RuntimeException. "Key already present"))
             (assoc this k v)))
  (without [this k]
    (if (not (contains? delegate-map k))
      this
      (cond
       (= 1 (count delegate-map)) (.empty this)
       (= 2 (count delegate-map)) (let [el (delegate-map k)
                                        other (delegate-map (el :right))
                                        new-head (assoc other :left nil :right nil)]
                                    (LinkedMap. new-head {(other :key) new-head}))
       :else (let [el (delegate-map k)
                   left (delegate-map (el :left))
                   right (delegate-map (el :right))
                   new-left (assoc left :right (right :key))
                   new-right (assoc right :left (left :key))
                   new-delegate-map (-> delegate-map (dissoc k) (assoc (left :key) new-left, (right :key) new-right))
                   new-head (condp = (head :key)
                              k new-right
                              (right :key) new-right
                              (left :key) new-left)]
               (LinkedMap. new-head new-delegate-map)))))

  MapEquivalence

  Map
  (get [this k]
       (.valAt this k))
  (containsValue [this v]
                 (boolean (seq (filter #(= % v) (.values this)))))
  (values [this]
          (map (comp val val) (.seq this)))
  (size [_]
        (.size delegate-map))

  IPersistentCollection
  (count [_]
         (.count delegate-map))
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
         (.LinkedMap nil (hash-map)))
  (equiv [this o]
         (and (= (.count this) (count o))
              (every? (fn [^MapEntry e]
                        (= (.val e) (get o (.key e))))
                      (.seq this))))

  Seqable
  (seq [_]
       (defn visit-node [{k :key v :value right-key :right}]
         (let [rest (cond
                     (nil? right-key) []
                     (= right-key (head :key)) []
                     :else (lazy-seq (visit-node (delegate-map right-key))))]
           (cons (MapEntry. k v) rest)))

       (if (empty? delegate-map)
         (list)
         (lazy-seq (visit-node head))))

  Iterable
  (iterator [this]
            (SeqIterator. (.seq this)))

  Associative
  (containsKey [_ k]
               (.containsKey delegate-map k))
  (entryAt [this k]
           (if-let [node (.valAt delegate-map k)]
             (MapEntry. k (node :value))
             nil))

  ILookup
  (valAt [_ k]
         (if-let [node (.valAt delegate-map k)]
           (node :value)
           nil))
  (valAt [this k not-found]
         (if (.containsKey this k)
           (.valAt this k)
           not-found))

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


;; TODO this prints a seq, should it be printed like a normal ordered map plus tag?
(defmethod print-method LinkedMap [o ^java.io.Writer w]
  (.write w "#linked/map ")
  (print-method (seq o) w))

(defn linked-map
  ([] (LinkedMap. nil (hash-map)))
  ([coll] (into (linked-map) coll))
  ([k v & more] (apply assoc (linked-map) k v more)))
