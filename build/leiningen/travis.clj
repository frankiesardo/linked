(ns leiningen.travis
  (:require [clojure.java.shell :as sh]
            [clojure.string :as str]
            [leiningen.core [eval :as eval]]
            [leiningen [release :as release]
             [deploy :as deploy]
             [vcs :as vcs]])
  (:import [java.nio.file Files]
           [java.nio.file.attribute FileAttribute]))

(defn env [s]
  (System/getenv s))

(defn tmp-dir []
  (.toString (Files/createTempDirectory nil (into-array FileAttribute []))))

(defn- sync-branch [dir branch]
  (let [tmp-dir (tmp-dir)
        msg (with-out-str (eval/sh "git" "log" "-1" "--pretty=%B"))
        url (-> (eval/sh "git" "config" "--get" "remote.origin.url")
                with-out-str (str/replace "\n" ""))]
    (eval/sh "git" "clone" "-b" branch url tmp-dir)
    (eval/sh "rsync" "-a" "--exclude=checkouts" dir tmp-dir)
    (binding [eval/*dir* tmp-dir]
      (eval/sh "git" "add" "--all")
      (eval/sh "git" "commit" "-m" msg)
      (eval/sh "git" "push" "origin" branch "--quiet"))))

(defn ->gh-pages [project]
  (sync-branch "doc/" "gh-pages"))

(defn switch-master [project]
  (eval/sh "git" "reset" "--hard")
  (eval/sh "git" "fetch" "origin")
  (eval/sh "git" "checkout" "-b" "master" "origin/master")
  (eval/sh "git" "push" "origin" "--delete" (env "TRAVIS_BRANCH")))

(defn travis [project & args]
  (when (= "false" (env "TRAVIS_PULL_REQUEST"))
    (condp re-find (env "TRAVIS_BRANCH")
      #"master" (do
                  (deploy/deploy project "clojars")
                  (if-not (str/blank? (env "TRAVIS_TAG"))
                    (->gh-pages project)))

      #"(?i)release" (do
                       (switch-master project)
                       (release/release project)))))
