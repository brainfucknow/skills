(ns acceptance.generator-spec
  (:require [acceptance.generator :as generator]
            [cheshire.core :as json]
            [clojure.string :as str]
            [speclj.core :refer [describe it should= should should-contain should-not-contain]]))

(def ir {"name" "Links" "scenarios" [{"name" "Links 1 \"quoted\"" "steps" [] "examples" [{"x" "secret-value"}]}]})

(def generated (generator/generation "build/acceptance/ir/compiled_skill_links.json" "build/out" ir))

(def metadata
  (json/parse-string (generated "metadata/features-compiled-skill-links-feature.json")))

(describe "metadata-name"
  (it "follows the APS lowercase-and-hyphen mapping"
    (should= "features-hunt-the-wumpus-feature.json" (generator/metadata-name "features/Hunt The Wumpus.feature"))
    (should= "features-api-v2-happy-path-feature.json" (generator/metadata-name "Features/API v2/Happy Path.feature"))))

(describe "generation"
  (it "writes one entry point and one metadata file"
    (should= #{"compiled_skill_links_acceptance_test.clj" "metadata/features-compiled-skill-links-feature.json"}
             (set (keys generated))))

  (it "keeps scenario structure in the entry point and leaves example values to the IR"
    (let [entry (generated "compiled_skill_links_acceptance_test.clj")]
      (should-contain (pr-str "Links 1 \"quoted\"") entry)
      (should-not-contain "secret-value" entry)
      (should-contain "runtime/run-feature" entry)))

  (it "describes the feature, the IR and the generated files"
    (should= {"schema_version" 1
              "feature_path" "features/compiled_skill_links.feature"
              "ir_path" "build/acceptance/ir/compiled_skill_links.json"
              "hash_scope" "generated_files"
              "generated_files" ["build/out/compiled_skill_links_acceptance_test.clj"]}
             (dissoc metadata "implementation_hash")))

  (it "hashes only the generated files"
    (should= (generator/implementation-hash {"compiled_skill_links_acceptance_test.clj"
                                             (generated "compiled_skill_links_acceptance_test.clj")})
             (metadata "implementation_hash"))
    (should (str/starts-with? (metadata "implementation_hash") "sha256:")))

  (it "is deterministic and ignores example values"
    (should= generated (generator/generation "build/acceptance/ir/compiled_skill_links.json" "build/out"
                                             (assoc-in ir ["scenarios" 0 "examples" 0 "x"] "other")))))

(describe "implementation-hash"
  (it "changes when a generated file changes"
    (should-not-contain (generator/implementation-hash {"a" "1"}) [(generator/implementation-hash {"a" "2"})])))
