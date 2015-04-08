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

(defn checkout-master [project]
  (eval/sh "git" "config" "remote.origin.fetch" "+refs/heads/*:refs/remotes/origin/*")
  (eval/sh "git" "fetch" "origin")
  (eval/sh "git" "checkout" "master")
  (eval/sh "git" "push" "origin" "--delete" (env "CIRCLE_BRANCH")))

(defn circle [project & args]
  (condp re-find (env "CIRCLE_BRANCH")
    #"master"
    (deploy/deploy project "clojars")

    #"(?i)release"
    (do
      (checkout-master project)
      (release/release project))))
