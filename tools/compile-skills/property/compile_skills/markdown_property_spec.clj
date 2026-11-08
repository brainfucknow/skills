(ns compile-skills.markdown-property-spec
  (:require [clojure.string :as str]
            [clojure.test.check.generators :as gen]
            [clojure.test.check.properties :refer [for-all]]
            [compile-skills.markdown :as md]
            [compile-skills.property :refer [holds]]
            [speclj.core :refer [describe it]]))

(def ^:private markdown-char
  (gen/frequency [[8 gen/char-alphanumeric]
                  [2 (gen/elements [\space \newline \` \~ \# \[ \] \( \) \. \/ \- \_ \! \:])]
                  [1 (gen/elements [\é \ß \Ж \r])]]))

(def ^:private text (gen/fmap str/join (gen/vector markdown-char 0 120)))

(def ^:private segment (gen/fmap str/join (gen/vector gen/char-alphanumeric 1 6)))

(def ^:private folder (gen/fmap #(str/join "/" %) (gen/vector segment 0 3)))

(def ^:private link-target
  (gen/fmap #(str/join "/" %)
            (gen/vector (gen/frequency [[5 segment] [1 (gen/return "..")] [1 (gen/return ".")]]) 1 4)))

(def ^:private skill-files (gen/fmap set (gen/vector (gen/fmap #(str % ".md") link-target) 0 4)))

(def ^:private linked-text
  (gen/fmap str/join
            (gen/vector (gen/one-of [text
                                     (gen/fmap (fn [[label target]] (str "[" label "](" target ")"))
                                               (gen/tuple segment (gen/one-of [link-target (gen/fmap #(str % ".md") link-target)])))
                                     (gen/fmap #(str "\n```\n" % "\n```\n") text)])
                        0 6)))

(def ^:private link-context
  (gen/fmap (fn [[skill-name files folder]] {:skill-name skill-name :files files :folder folder})
            (gen/tuple segment skill-files folder)))

(describe "markdown properties"
  (it "fences any content so the fence's first and last lines enclose it unchanged"
    (holds (for-all [content text info segment]
             (let [lines (str/split (md/fence info content) #"\n" -1)
                   ticks (str/replace (first lines) info "")]
               (and (= ticks (last lines))
                    (< (reduce max 0 (map count (re-seq #"`+" content))) (count ticks))
                    (= content (str/join "\n" (butlast (rest lines)))))))))

  (it "normalizes newlines idempotently, leaving no CRLF"
    (holds (for-all [t (gen/fmap #(str/replace % "r" "\r\n") text)]
             (let [once (md/normalize-newlines t)]
               (and (not (str/includes? once "\r\n"))
                    (= once (md/normalize-newlines once)))))))

  (it "trims blank lines idempotently"
    (holds (for-all [t text]
             (let [once (md/trim-blank-lines t)]
               (= once (md/trim-blank-lines once))))))

  (it "slugs headings into anchor characters only, idempotently"
    (holds (for-all [heading text]
             (let [slug (md/slug heading)]
               (and (re-matches #"[\p{L}\p{Nd}_-]*" slug)
                    (= slug (md/slug slug)))))))

  (it "splits frontmatter it was given back out, with the body"
    (holds (for-all [front (gen/fmap #(str/replace % "-" "") text)
                     body text]
             (= {:frontmatter (str "---\n" front "\n---") :body body}
                (md/split-frontmatter (str "---\n" front "\n---\n" body))))))

  (it "resolves paths inside the skill folder, never above it"
    (holds (for-all [base folder target link-target]
             (let [resolved (md/resolve-path base target)]
               (or (nil? resolved)
                   (not-any? #{".." "."} (str/split resolved #"/")))))))

  (it "resolves plain segments by appending them to the folder"
    (holds (for-all [base folder segments (gen/vector segment 1 3)]
             (= (str/join "/" (concat (remove str/blank? (str/split base #"/")) segments))
                (md/resolve-path base (str/join "/" segments))))))

  (it "rewrites links idempotently and leaves the rest of the text alone"
    (holds (for-all [context link-context t linked-text]
             (let [once (md/rewrite-links context t)
                   strip #(str/replace % #"\]\([^()\s]+\)" "]()")]
               (and (= once (md/rewrite-links context once))
                    (= (strip t) (strip once)))))))

  (it "leaves text without inline links unchanged"
    (holds (for-all [context link-context t (gen/fmap #(str/replace % "](" "] (") text)]
             (= t (md/rewrite-links context t))))))
