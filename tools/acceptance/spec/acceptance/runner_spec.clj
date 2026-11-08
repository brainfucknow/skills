(ns acceptance.runner-spec
  (:require [acceptance.runner :as runner]
            [babashka.fs :as fs]
            [speclj.core :refer [describe it should= should-throw]]))

(describe "text-report"
  (it "prints the APS summary line and one line per result"
    (should= (str "total=2 killed=1 survived=1 errors=0\n"
                  "killed   $.a: x -> y\n"
                  "survived $.b: p -> q")
             (runner/text-report {"summary" {"Total" 2 "Killed" 1 "Survived" 1 "Errors" 0}
                                  "results" [{"Status" "killed" "Mutation" {"Description" "$.a: x -> y"}}
                                             {"Status" "survived" "Mutation" {"Description" "$.b: p -> q"}}]})))

  (it "adds skipped counts when scenarios were skipped"
    (should= "total=0 killed=0 survived=0 errors=0 skipped_scenarios=2 skipped_mutations=5"
             (runner/text-report {"summary" {"Total" 0 "Killed" 0 "Survived" 0 "Errors" 0
                                             "SkippedScenarios" 2 "SkippedMutations" 5}
                                  "results" []}))))

(defn- temp-dir []
  (str (fs/create-temp-dir {:dir (doto (fs/file "tmp") fs/create-dirs)})))

(describe "find-wrapper"
  (it "names the sf tool ensure command when no wrapper exists"
    (let [dir (temp-dir)]
      (try
        (should-throw clojure.lang.ExceptionInfo
                      "gherkin-parser is not installed: run `swarmforge/bin/sf tool ensure gherkin-parser`"
                      (runner/find-wrapper [dir] "gherkin-parser"))
        (finally (fs/delete-tree dir)))))

  (it "takes the first directory holding an executable wrapper"
    (let [empty-dir (temp-dir)
          dir (temp-dir)
          wrapper (fs/path dir "gherkin-parser")]
      (try
        (spit (fs/file wrapper) "#!/bin/sh\n")
        (fs/set-posix-file-permissions wrapper "rwxr-xr-x")
        (should= (str wrapper) (runner/find-wrapper [empty-dir dir] "gherkin-parser"))
        (finally (fs/delete-tree empty-dir) (fs/delete-tree dir))))))
