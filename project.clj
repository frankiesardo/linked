(defproject frankiesardo/linked "1.2.2-SNAPSHOT"
  :description "Efficient ordered map and set"
  :url "http://github.com/frankiesardo/linked"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :profiles {:dev {:dependencies [[org.clojure/clojure "1.6.0"]
                                  [org.clojure/clojurescript "0.0-2371"]]
                   :plugins [[com.keminglabs/cljx "0.6.0"
                              :exclusions [org.clojure/clojure]]
                             [lein-cljsbuild "1.0.5"]
                             [com.cemerick/clojurescript.test "0.3.3"]
                             [codox "0.8.10"]]
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
                                    :rules :cljs}]}}}
  :codox {:src-dir-uri "http://github.com/frankiesardo/linked/blob/master/"
          :src-uri-mapping {#"target/classes" #(str "src/" % "x")}
          :src-linenum-anchor-prefix "L"}
  :aliases {"test" ["do" "clean," "cljx" "once," "test," "cljsbuild" "test"]
            "check" ["do" "clean," "cljx" "once," "check"]}
  :release-tasks [["vcs" "assert-committed"]
                  ["change" "version" "leiningen.release/bump-version" "release"]
                  ["cljx" "once"]
                  ["doc"]
                  ["vcs" "commit"]
                  ["vcs" "tag" "v"]
                  ["deploy" "clojars"]
                  ["rsync" "doc/" "gh-pages"]
                  ["change" "version" "leiningen.release/bump-version"]
                  ["vcs" "commit"]
                  ["vcs" "push"]]
  :auto-clean false
  :jar-exclusions [#"\.cljx"]
  :cljsbuild {:test-commands
              {"phantom" ["phantomjs" :runner "target/testable.js"]}
              :builds
              [{:source-paths ["target/classes" "target/test-classes"]
                :compiler {:output-to "target/testable.js"
                           :optimizations :whitespace
                           :pretty-print true}}]}
  :source-paths ["src" "target/classes"]
  :test-paths ["test" "target/test-classes"])
