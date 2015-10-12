# Linked

[![Build status](https://circleci.com/gh/frankiesardo/linked.svg?style=shield)](https://circleci.com/gh/frankiesardo/linked)

Map and Set structures that rememeber the insertion order of its elements, even after multiple assoc and dissoc. For Clojure and ClojureScript.

## Download

[![Clojars Project](http://clojars.org/frankiesardo/linked/latest-version.svg)](http://clojars.org/frankiesardo/linked)

## Map

```clj
(require '[linked.core :as linked])

(linked/map :b 2 :a 1 :d 4)
;=> #linked/map [[:b 2] [:a 1] [:d 4]]

(assoc (linked/map :b 2 :a 1 :d 4) :c 3)
;=> #linked/map [[:b 2] [:a 1] [:d 4] [:c 3]]

(into (linked/map) [[:c 3] [:a 1] [:d 4]])
;=> #linked/map [[:c 3] [:a 1] [:d 4]]

(dissoc (linked/map :c 3 :a 1 :d 4) :a)
;=> #linked/map [[:c 3] [:d 4]]
```

## Set

```clj
(require '[linked.core :as linked])

(linked/set 4 3 1 8 2)
;=> #linked/set [4 3 1 8 2]

(conj (linked/set 9 10) 1 2 3)
;=> #linked/set [9 10 1 2 3]

(into (linked/set) [7 6 1 5 6])
;=> #linked/set [7 6 1 5]

(disj (linked/set 8 1 7 2 6) 7)
;=> #linked/set [8 1 2 6]
```

## Performance

These data structures wrap a normal `hash-map` but instead of feeding it a normal `[key value]` pair their remeber a `[key value left-key right-key]` record. When an item is removed from the data structure it is sufficient to update the left and right node to reference each others keys while removing the chosen node. This implementation yields the same Big O time and space complexity of a standard `hash-map` (altought effective performance will be slower by a constant factor).

## Comparison with [ordered](https://github.com/amalloy/ordered)

- Ordered will keep on allocating memory space until you explicitly call [compact](https://github.com/amalloy/ordered/blob/develop/src/flatland/ordered/common.clj#L7) to clean up the garbage. Linked doesn't keep a pointer to old elements
- Ordered has transient support for faster allocation of a large number of items
- Linked works with ClojureScript

## License

Copyright © 2014 Frankie Sardo

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
