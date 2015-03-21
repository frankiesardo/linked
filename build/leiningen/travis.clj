(ns leiningen.travis
  (:require [clojure.java.shell :as sh]
            [clojure.string :as str]
            [leiningen.core [eval :as eval]]
            [leiningen [release :as release]
             [deploy :as deploy]
             [vcs :as vcs]]))

(defn env [s]
  (System/getenv s))

(defn- sync-branch [project dir branch]
  (let [msg (with-out-str (eval/sh "git" "log" "-1" "--pretty=%B"))
        url (-> (eval/sh "git" "config" "--get" "remote.origin.url")
                with-out-str (str/replace "\n" ""))]
    (eval/sh "git" "clone" "-b" branch url branch)
    (eval/sh "rsync" "-a" "--exclude=checkouts" dir branch)
    (binding [eval/*dir* (str (:root project) "/" branch)]
      (eval/sh "git" "add" "--all")
      (eval/sh "git" "commit" "-m" msg)
      (eval/sh "git" "push" "origin" branch "--quiet"))))

(defn ->gh-pages [project]
  (sync-branch project "doc/" "gh-pages"))

(defn switch-master []
  (eval/sh "git" "checkout" "master")
  (eval/sh "git" "push" "origin" "--delete" (env "TRAVIS_BRANCH")))

(defn travis [project & args]
  (when (= "false" (env "TRAVIS_PULL_REQUEST"))
    (condp re-find (env "TRAVIS_BRANCH")
      #"master" (do
                  (deploy/deploy project "clojars")
                  (if (env "TRAVIS_TAG")
                    (->gh-pages project)))

      #"(?i)release" (do
                       (switch-master project)
                       (release/release project)))))
