(ns compile-skills.boundaries-spec
  (:require [clojure.edn :as edn]
            [speclj.core :refer [describe it should=]]))

(defn requires
  "The namespaces a source file's ns form requires."
  [path]
  (let [[_ _ & clauses] (edn/read-string (slurp path))]
    (set (for [clause clauses
               :when (and (seq? clause) (= :require (first clause)))
               spec (rest clause)]
           (if (vector? spec) (first spec) spec)))))

(def ^:private src "tools/compile-skills/src/compile_skills/")

(def ^:private io '#{babashka.fs babashka.process})

(describe "compile-skills boundaries"
  (it "keeps file system access in main, the adapter"
    (should= {"commands.clj" #{} "compiler.clj" #{} "markdown.clj" #{}}
             (into {} (for [file ["commands.clj" "compiler.clj" "markdown.clj"]]
                        [file (set (filter io (requires (str src file))))]))))

  (it "points dependencies inward: main, commands, compiler, markdown"
    (should= {"commands.clj" '#{compile-skills.compiler}
              "compiler.clj" '#{compile-skills.markdown}
              "markdown.clj" #{}}
             (into {} (for [file ["commands.clj" "compiler.clj" "markdown.clj"]]
                        [file (set (filter #(re-find #"^compile-skills\." (str %))
                                           (requires (str src file))))])))))
