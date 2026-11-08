(ns compile-skills.markdown
  "Text shaping for compiled skills: frontmatter, headings, anchors, fences,
  and rewriting links between files of one skill."
  (:require [clojure.string :as str]))

(defn normalize-newlines [text]
  (str/replace text "\r\n" "\n"))

(defn split-frontmatter
  "Splits SKILL.md text into its `---` fenced frontmatter (nil when absent)
  and the body after the closing fence."
  [text]
  (if-let [[_ frontmatter body] (re-matches #"(?s)(---\n.*?\n---)(?:\n(.*))?" text)]
    {:frontmatter frontmatter :body (or body "")}
    {:frontmatter nil :body text}))

(defn user-invoked? [frontmatter]
  (boolean (and frontmatter
                (re-find #"(?m)^disable-model-invocation:\s*true\s*$" frontmatter))))

(defn- blank-line? [line]
  (str/blank? line))

(defn trim-blank-lines
  "Drops leading and trailing blank lines, and the final newline."
  [text]
  (->> (str/split-lines text)
       (drop-while blank-line?)
       reverse
       (drop-while blank-line?)
       reverse
       (str/join "\n")))

(defn trim-trailing-newlines [text]
  (str/replace text #"\n+\z" ""))

(defn slug
  "The GitHub anchor for a heading: lowercase, only letters, digits, spaces,
  hyphens and underscores kept, spaces turned into hyphens."
  [heading]
  (-> (str/lower-case heading)
      (str/replace #"[^\p{L}\p{Nd} _-]" "")
      (str/replace " " "-")))

(defn skill-heading [skill-name]
  (str "# Skill: " skill-name))

(defn file-heading [skill-name relative-path]
  (str "## File: " skill-name "/" relative-path))

(defn heading-anchor [heading]
  (str "#" (slug (str/replace heading #"^#+ " ""))))

(defn extension
  "The file extension without its dot, or empty for an extensionless file."
  [relative-path]
  (let [file-name (last (str/split relative-path #"/"))
        dot (str/last-index-of file-name ".")]
    (if (and dot (pos? dot))
      (subs file-name (inc dot))
      "")))

(defn markdown-file? [relative-path]
  (= "md" (str/lower-case (extension relative-path))))

(defn fence
  "Wraps content in a backtick fence one longer than its longest backtick run,
  at least three, with the info string."
  [info content]
  (let [longest (reduce max 0 (map count (re-seq #"`+" content)))
        ticks (apply str (repeat (max 3 (inc longest)) \`))]
    (if (empty? content)
      (str ticks info "\n" ticks)
      (str ticks info "\n" content "\n" ticks))))

(defn- relative-target? [target]
  (not (or (str/starts-with? target "#")
           (str/starts-with? target "/")
           (re-find #"^[A-Za-z][A-Za-z0-9+.-]*:" target))))

(defn resolve-path
  "Resolves a relative path against a folder inside the skill. Nil when the
  path leaves the skill folder."
  [folder target]
  (loop [resolved (vec (remove str/blank? (str/split folder #"/")))
         [segment & more :as segments] (str/split target #"/")]
    (cond
      (empty? segments) (str/join "/" resolved)
      (contains? #{"" "."} segment) (recur resolved more)
      (= ".." segment) (when (seq resolved) (recur (pop resolved) more))
      :else (recur (conj resolved segment) more))))

(defn link-anchor
  "The anchor a link target becomes, or nil when the link stays as it is.
  `files` holds the relative paths of the skill's inlined files."
  [{:keys [skill-name files folder]} target]
  (when (relative-target? target)
    (let [path (-> target (str/replace #"^\./" "") (str/replace #"#.*\z" ""))
          resolved (resolve-path folder path)]
      (cond
        (= "SKILL.md" resolved) (heading-anchor (skill-heading skill-name))
        (contains? files resolved) (heading-anchor (file-heading skill-name resolved))))))

(def ^:private inline-link #"\[([^\]\n]*)\]\(([^()\s]+)\)")

(defn- rewrite-line [context line]
  (str/replace line inline-link
               (fn [[whole text target]]
                 (if-let [anchor (link-anchor context target)]
                   (str "[" text "](" anchor ")")
                   whole))))

(defn- fence-open [line]
  (when-let [[_ marker] (re-find #"^ {0,3}(`{3,}|~{3,})" line)]
    {:char (first marker) :length (count marker)}))

(defn- closes? [{:keys [char length]} line]
  (when-let [[_ marker] (re-matches #" {0,3}(`{3,}|~{3,})\s*" line)]
    (and (= char (first marker)) (<= length (count marker)))))

(defn- rewrite-step [{:keys [open lines]} context line]
  (cond
    open {:open (when-not (closes? open line) open) :lines (conj lines line)}
    (fence-open line) {:open (fence-open line) :lines (conj lines line)}
    :else {:open nil :lines (conj lines (rewrite-line context line))}))

(defn rewrite-links
  "Rewrites inline links to files of the same skill into heading anchors,
  leaving fenced code blocks alone."
  [context text]
  (->> (str/split text #"\n" -1)
       (reduce #(rewrite-step %1 context %2) {:open nil :lines []})
       :lines
       (str/join "\n")))
