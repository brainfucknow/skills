(ns compile-skills.commands-spec
  (:require [compile-skills.commands :as commands]
            [compile-skills.compiler :as compiler]
            [compile-skills.fixtures :refer [skill]]
            [speclj.core :refer [describe it should= should-contain should-be-nil]]))

(def alpha (skill "alpha" "# Alpha"))
(def beta (skill "beta" "productivity" "# Beta" {}))
(def current (compiler/compile-all [alpha beta]))

(describe "compile-skills"
  (it "writes only changed files and deletes orphans"
    (should= {:exit 0
              :write {"compiled/beta.md" (current "compiled/beta.md")}
              :delete ["compiled/gone.md"]}
             (commands/compile-skills [] [alpha beta]
                                      {"compiled/alpha.md" (current "compiled/alpha.md")
                                       "compiled/beta.md" "old"
                                       "compiled/gone.md" "x"})))

  (it "refuses duplicate names, naming both folders"
    (let [result (commands/compile-skills [] [alpha (skill "alpha" "in-progress" "" {})] {})]
      (should= 1 (:exit result))
      (should-be-nil (:write result))
      (should= ["Duplicate skill name alpha: skills/engineering/alpha, skills/in-progress/alpha"]
               (:err result))))

  (it "refuses unknown arguments"
    (should= {:exit 1 :err ["Usage: bb compile-skills [--check]"]}
             (commands/compile-skills ["--force"] [alpha] {}))))

(describe "compile-skills --check"
  (it "passes when compiled output is current"
    (should= {:exit 0} (commands/compile-skills ["--check"] [alpha beta] current)))

  (it "names stale, missing and extra files and writes nothing"
    (should= {:exit 1 :err ["compiled/alpha.md is stale"
                            "compiled/beta.md is missing"
                            "compiled/bogus.md has no skill"]}
             (commands/compile-skills ["--check"] [alpha beta]
                                      {"compiled/alpha.md" "old" "compiled/bogus.md" "x"}))))

(describe "compile-skill"
  (it "returns what compile-skills writes for that skill"
    (should= {:exit 0 :out (current "compiled/beta.md")}
             (commands/compile-skill ["beta"] [alpha beta])))

  (it "refuses an unknown name, naming it"
    (let [result (commands/compile-skill ["nope"] [alpha])]
      (should= 1 (:exit result))
      (should-be-nil (:out result))
      (should-contain "nope" (first (:err result)))))

  (it "prints usage without exactly one name"
    (should= {:exit 1 :err ["Usage: bb compile-skill <name>"]} (commands/compile-skill [] [alpha]))
    (should= 1 (:exit (commands/compile-skill ["a" "b"] [alpha])))))
