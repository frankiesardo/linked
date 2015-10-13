(set-env!
 :resource-paths #{"src"}
 :dependencies '[[adzerk/boot-cljs "0.0-3308-0" :scope "test"]
                 [adzerk/boot-cljs-repl "0.1.9" :scope "test"]
                 [crisptrutski/boot-cljs-test "0.1.0-SNAPSHOT" :scope "test"]
                 [collection-check "0.1.6" :scope "test"
                  :exclusions [org.clojure/clojure]]])

(require
 '[adzerk.boot-cljs      :refer [cljs]]
 '[adzerk.boot-cljs-repl :refer [cljs-repl start-repl]]
 '[crisptrutski.boot-cljs-test :refer [test-cljs]])

(require '[clojure.java.shell :as shell]
         '[clojure.string :as str])

(defn- current-tag []
  (let [{:keys [exit out]} (shell/sh "git" "describe" "--tags")]
    (when (zero? exit)
      (str/trim out))))

(def +version+
  (if-let [tag (current-tag)]
    (cond-> (second (re-find #"v(.*)" tag))
      (.contains tag "-") (str "-SNAPSHOT"))
    "0.1.0-SNAPSHOT"))

(task-options!
 pom  {:project        'frankiesardo/linked
       :version        +version+
       :description    "Efficient ordered map and set."
       :url            "https://github.com/frankiesardo/linked"
       :scm            {:url "https://github.com/frankiesardo/linked"}
       :license        {"Eclipse Public License"
                        "http://www.eclipse.org/legal/epl-v10.html"}})

(deftask deploy []
  (comp (pom) (jar) (install)
        (push :gpg-sign (not (.endsWith +version+ "-SNAPSHOT")))))

(deftask testing []
  (set-env! :source-paths #(conj % "test"))
  identity)

(ns-unmap 'boot.user 'test)

(require '[boot.pod  :as pod]
         '[boot.core :as core])

(def pod-deps
  '[[pjstadig/humane-test-output "0.6.0"  :exclusions [org.clojure/clojure]]])

(defn init [fresh-pod]
  (doto fresh-pod
    (pod/with-eval-in
      (require '[clojure.test :as t]
               '[clojure.java.io :as io]
               '[pjstadig.humane-test-output :refer [activate!]])
      (activate!)
      (defn test-ns* [ns]
        (binding [t/*report-counters* (ref t/*initial-report-counters*)]
          (let [ns-obj (the-ns ns)]
            (t/do-report {:type :begin-test-ns :ns ns-obj})
            (t/test-vars (vals (ns-publics ns)))
            (t/do-report {:type :end-test-ns :ns ns-obj}))
          @t/*report-counters*)))))

(deftask test-clj []
  (let [worker-pods (pod/pod-pool (update-in (core/get-env) [:dependencies] into pod-deps) :init init)]
    (core/cleanup (worker-pods :shutdown))
    (core/with-pre-wrap fileset
      (let [worker-pod (worker-pods :refresh)
            namespaces (core/fileset-namespaces fileset)]
        (if (seq namespaces)
          (let [summary (pod/with-eval-in worker-pod
                          (doseq [ns '~namespaces] (require ns))
                          (let [ns-results (map test-ns* '~namespaces)]
                            (-> (reduce (partial merge-with +) ns-results)
                                (assoc :type :summary)
                                (doto t/do-report))))]
            (when (> (apply + (map summary [:fail :error])) 0)
              (throw (ex-info "Some tests failed or errored" summary))))
          (println "No namespaces were tested."))
        fileset))))

(deftask test []
  (comp (testing)
        (test-clj)
        (test-cljs :js-env :phantom
                   :exit?  true)))

(deftask autotest []
  (comp (testing)
        (watch)
        (test-clj)
        (test-cljs :js-env :phantom)))
