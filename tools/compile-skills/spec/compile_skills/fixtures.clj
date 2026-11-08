(ns compile-skills.fixtures)

(defn skill
  "A skill as compile-skills reads it, with frontmatter and agents/openai.yaml."
  ([skill-name body] (skill skill-name "engineering" body {}))
  ([skill-name bucket body files]
   {:name skill-name
    :bucket bucket
    :files (merge {"SKILL.md" (str "---\nname: " skill-name "\ndescription: D.\n---\n\n" body "\n")
                   "agents/openai.yaml" "interface: {}\n"}
                  files)}))
