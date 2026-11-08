(ns compile-skills.markdown-spec
  (:require [compile-skills.markdown :as md]
            [speclj.core :refer [describe it should should-not should= should-be-nil]]))

(def alpha {:skill-name "alpha" :files #{"notes.md" "docs/deep.md"} :folder ""})

(describe "split-frontmatter"
  (it "separates the fenced frontmatter from the body"
    (should= {:frontmatter "---\nname: a\n---" :body "\n# A\n"}
             (md/split-frontmatter "---\nname: a\n---\n\n# A\n")))
  (it "treats text without frontmatter as all body"
    (should= {:frontmatter nil :body "# A\n"} (md/split-frontmatter "# A\n")))
  (it "accepts frontmatter at the end of the text"
    (should= {:frontmatter "---\nname: a\n---" :body ""} (md/split-frontmatter "---\nname: a\n---"))))

(describe "user-invoked?"
  (it "is true when model invocation is disabled"
    (should (md/user-invoked? "---\nname: a\ndisable-model-invocation: true\n---")))
  (it "is false otherwise"
    (should-not (md/user-invoked? "---\nname: a\ndisable-model-invocation: false\n---"))
    (should-not (md/user-invoked? nil))))

(describe "trim-blank-lines"
  (it "drops leading and trailing blank lines but keeps inner ones"
    (should= "  # A\n\nb" (md/trim-blank-lines "\n \n  # A\n\nb\n\n\n"))))

(describe "slug"
  (it "follows the GitHub heading rules"
    (should= "file-tddtestsmd" (md/slug "File: tdd/tests.md"))
    (should= "skill-dep-a" (md/slug "Skill: dep-a"))
    (should= "a_b-c" (md/slug "A_b C!"))))

(describe "extension"
  (it "is the text after the last dot of the file name"
    (should= "sh" (md/extension "scripts/hitl-loop.template.sh"))
    (should= "" (md/extension "Makefile"))
    (should= "" (md/extension "dir.d/.hidden"))))

(describe "fence"
  (it "uses three backticks and the info string"
    (should= "```sh\necho hi\n```" (md/fence "sh" "echo hi")))
  (it "uses one more backtick than the longest run in the content"
    (should= "````txt\na\n```\nb\n````" (md/fence "txt" "a\n```\nb")))
  (it "fences empty content"
    (should= "```\n```" (md/fence "" ""))))

(describe "resolve-path"
  (it "resolves against the folder"
    (should= "notes.md" (md/resolve-path "docs" "../notes.md"))
    (should= "docs/deep.md" (md/resolve-path "" "./docs/./deep.md")))
  (it "is nil when the path leaves the skill folder"
    (should-be-nil (md/resolve-path "" "../beta/SKILL.md"))))

(describe "link-anchor"
  (it "points SKILL.md at the skill heading"
    (should= "#skill-alpha" (md/link-anchor alpha "./SKILL.md")))
  (it "points an inlined file at its file heading, dropping the fragment"
    (should= "#file-alphanotesmd" (md/link-anchor alpha "notes.md#part")))
  (it "leaves absolute, anchor, external, missing and escaping targets alone"
    (doseq [target ["https://example.com/notes.md" "#assets" "/notes.md" "missing.md" "../notes.md" "mailto:a@b.c"]]
      (should-be-nil (md/link-anchor alpha target)))))

(describe "rewrite-links"
  (it "rewrites links to files of the same skill and keeps the text"
    (should= "See [n](#file-alphanotesmd) and [w](https://x.y/notes.md)."
             (md/rewrite-links alpha "See [n](notes.md) and [w](https://x.y/notes.md).")))
  (it "resolves against the linking file's folder"
    (should= "Up [n](#file-alphanotesmd)."
             (md/rewrite-links (assoc alpha :folder "docs") "Up [n](../notes.md).")))
  (it "leaves fenced code blocks alone"
    (should= "```\n[n](notes.md)\n```\n[n](#file-alphanotesmd)"
             (md/rewrite-links alpha "```\n[n](notes.md)\n```\n[n](notes.md)")))
  (it "keeps a fence open until a long enough closing fence of the same character"
    (should= "````\n```\n[n](notes.md)\n~~~\n````\n[n](#file-alphanotesmd)"
             (md/rewrite-links alpha "````\n```\n[n](notes.md)\n~~~\n````\n[n](notes.md)"))))
