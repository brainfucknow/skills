(ns acceptance.boundaries-spec
  (:require [compile-skills.boundaries-spec :refer [requires]]
            [speclj.core :refer [describe it should=]]))

(def ^:private src "tools/acceptance/src/acceptance/")

(describe "acceptance pipeline boundaries"
  (it "keeps the project's step library out of the generic pipeline"
    (should= {"runtime.clj" false "generator.clj" false "runner.clj" false "worker.clj" false}
             (into {} (for [file ["runtime.clj" "generator.clj" "runner.clj" "worker.clj"]]
                        [file (contains? (requires (str src file)) 'acceptance.steps)]))))

  (it "binds steps to the runtime only through generated entry points"
    (should= '#{acceptance.runtime}
             (set (filter #(re-find #"^acceptance\." (str %))
                          (requires (str src "steps.clj")))))))
