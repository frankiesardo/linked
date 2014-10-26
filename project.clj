(defproject frankiesardo/linked "1.0.5"
  :description "Efficient ordered map and set"
  :url "http://github.com/frankiesardo/linked"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :profiles {:dev
             {:dependencies [[org.clojure/clojure "1.6.0"]
                             [org.clojure/clojurescript "0.0-2371"]]
              :plugins [[com.keminglabs/cljx "0.3.1"]
                        [lein-cljsbuild "1.0.3"]
                        [com.cemerick/clojurescript.test "0.3.1"]]
              :aliases {"cleantest" ["do" "clean," "cljx" "once," "test,"
                                     "cljsbuild" "test"]
                        "cleancheck" ["do" "clean," "cljx" "once," "check"]}}}

  :auto-clean false
  :jar-exclusions [#"\.cljx"]

  :cljx {:builds [{:source-paths ["src"]
                   :output-path "target/classes"
                   :rules :clj}
                  {:source-paths ["src"]
                   :output-path "target/classes"
                   :rules :cljs}
                  {:source-paths ["test"]
                   :output-path "target/test-classes"
                   :rules :clj}
                  {:source-paths ["test"]
                   :output-path "target/test-classes"
                   :rules :cljs}]}

  :cljsbuild {:test-commands {"phantom" ["phantomjs" :runner "target/testable.js"]}
              :builds [{:source-paths ["target/classes" "target/test-classes"]
                        :compiler {:output-to "target/testable.js"
                                   :optimizations :whitespace
                                   :pretty-print true}}]}


  :source-paths ["src" "target/classes"]
  :test-paths ["test" "target/test-classes"])
