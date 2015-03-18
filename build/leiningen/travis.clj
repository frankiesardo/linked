(ns leiningen.travis
  (:require [clojure.java.shell :as sh]
            [leiningen [release :as release]
             [deploy :as deploy]
             [vcs :as vcs]]
            [leiningen.core [eval :as eval]]))

(defn env [s]
  (System/getenv s))

(defn- sync-branch [project dir branch]
  (let [msg (with-out-str (eval/sh "git" "log" "-1" "--pretty=%B"))]
    (eval/sh "rsync" "-a" (str eval/*dir* "/.git") branch)
    (binding [eval/*dir* (str (:root project) "/" branch)]
      (eval/sh "git" "fetch")
      (eval/sh "git" "checkout" branch))
    (eval/sh "rsync" "-a" dir branch)
    (binding [eval/*dir* (str (:root project) "/" branch)]
      (eval/sh "git" "add" ".")
      (eval/sh "git" "commit" "-m" msg)
      (eval/sh "git" "push" "origin" branch "--quiet"))))

(defn ->gh-pages [project]
  (sync-branch project "doc/" "gh-pages"))

(defn switch-master []
  (eval/sh "git" "checkout" "master"))

(defn travis [project & args]
  (when (= "false" (env "TRAVIS_PULL_REQUEST"))
    (condp re-find (env "TRAVIS_BRANCH")
      #"master" (do
                  (deploy/deploy project)
                  (if (env "TRAVIS_TAG")
                    (->gh-pages project)))

      #"(?i)release" (do
                       (switch-master project)
                       (release/relase project)))))
