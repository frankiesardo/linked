# Linked

[![Build Status](https://secure.travis-ci.org/frankiesardo/linked.png)](http://travis-ci.org/frankiesardo/linked)

Map and Set structures that rememeber the insertion order of its elements, even after multiple assoc and dissoc. For Clojure and ClojureScript.

## Download

[![Clojars Project](http://clojars.org/frankiesardo/linked/latest-version.svg)](http://clojars.org/frankiesardo/linked)

## Map

    (use 'linked.map)

    (linked-map :b 2 :a 1 :d 4)
    => #linked/map [[:b 2] [:a 1] [:d 4]]

    (assoc (linked-map :b 2 :a 1 :d 4) :c 3)
    => #linked/map [[:b 2] [:a 1] [:d 4] [:c 3]]

    (into (linked-map) [[:c 3] [:a 1] [:d 4]])
    => #linked/map [[:c 3] [:a 1] [:d 4]]

    (dissoc (linked-map :c 3 :a 1 :d 4) :a)
    => #linked/map [[:c 3] [:d 4]]

## Set

    (use 'linked.set)

    (linked-set 4 3 1 8 2)
    => #linked/set [4 3 1 8 2]

    (conj (linked-set 9 10) 1 2 3)
    => #linked/set [9 10 1 2 3]

    (into (linked-set) [7 6 1 5 6])
    => #linked/set [7 6 1 5]

    (disj (linked-set 8 1 7 2 6) 7)
    => #linked/set [8 1 2 6]

## Performance

These data structures wrap a normal `hash-map` but instead of feeding it a normal `[key value]` pair their remeber a `[key value left-key right-key]` record. When an item is removed from the data structure it is sufficient to update the left and right node to reference each others keys while removing the chosen node.

This yields the same Big O time and space complexity of a standard `hash-map` but it makes easy to walk throught the structure in sequential or reverse order following the left and right links.

Effective performance is about 2.5x slower than a `hash-map`.

## License

Copyright © 2014 Frankie Sardo

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
