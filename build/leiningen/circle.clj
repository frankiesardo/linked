(ns leiningen.circle
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

(defn checkout-master [project]
  (eval/sh "git" "config" "remote.origin.fetch" "+refs/heads/*:refs/remotes/origin/*")
  (eval/sh "git" "fetch" "origin")
  (eval/sh "git" "checkout" "master")
  (eval/sh "git" "push" "origin" "--quiet" "--delete" (env "CIRCLE_BRANCH")))

(defn circle [project & args]
  (condp re-find (env "CIRCLE_BRANCH")
    #"master"
    (deploy/deploy project "clojars")

    #"(?i)release"
    (do
      (checkout-master project)
      (release/release project)
      (->gh-pages project))))
