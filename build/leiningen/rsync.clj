(ns leiningen.rsync
  (:require [clojure.string :as str]
            [leiningen.core [eval :as eval]])
  (:import [java.nio.file Files]
           [java.nio.file.attribute FileAttribute]))

(defn tmp-dir []
  (.toString (Files/createTempDirectory nil (into-array FileAttribute []))))

(defn rsync [project dir branch]
  (let [tmp-dir (tmp-dir)
        msg (with-out-str (eval/sh "git" "log" "-1" "--pretty=%B"))
        url (-> (eval/sh "git" "config" "--get" "remote.origin.url")
                with-out-str (str/replace "\n" ""))]
    (eval/sh "git" "clone" "-b" branch url tmp-dir)
    (eval/sh "rsync" "-a" "--exclude=checkouts" dir tmp-dir)
    (binding [eval/*dir* tmp-dir]
      (eval/sh "git" "add" "--all")
      (eval/sh "git" "commit" "-m" msg)
      (eval/sh "git" "push" "origin" branch))))
