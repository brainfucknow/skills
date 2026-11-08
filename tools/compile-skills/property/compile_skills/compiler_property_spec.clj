(ns compile-skills.compiler-property-spec
  (:require [clojure.string :as str]
            [clojure.test.check.generators :as gen]
            [clojure.test.check.properties :refer [for-all]]
            [compile-skills.commands :as commands]
            [compile-skills.compiler :as compiler]
            [compile-skills.fixtures :refer [skill]]
            [compile-skills.property :refer [holds]]
            [speclj.core :refer [describe it]]))

(def ^:private names ["alpha" "beta" "gamma" "delta" "epsilon" "zeta"])

(defn- calls [targets]
  (str/join " " (map #(str "Call the Skill tool with \"" % "\".") targets)))

(defn- user-invoked [s]
  (update-in s [:files "SKILL.md"] str/replace "description: D.\n" "description: D.\ndisable-model-invocation: true\n"))

(def ^:private skills
  "A repository of uniquely named skills calling each other, some user-invoked."
  (gen/let [chosen (gen/fmap #(vec (distinct %)) (gen/vector (gen/elements names) 1 6))
            buckets (gen/vector (gen/elements compiler/compiled-buckets) (count chosen))
            targets (gen/vector (gen/vector (gen/elements names) 0 4) (count chosen))
            user? (gen/vector gen/boolean (count chosen))]
    (mapv (fn [skill-name bucket called user-invoked?]
            (cond-> (skill skill-name bucket (str "# " skill-name "\n\n" (calls called)) {})
              user-invoked? user-invoked))
          chosen buckets targets user?)))

(defn- user-invoked? [s]
  (str/includes? (get-in s [:files "SKILL.md"]) "disable-model-invocation: true"))

(def ^:private repository
  (gen/let [all skills
            root (gen/elements all)]
    [all root]))

(describe "compiler properties"
  (it "inlines each dependency once, never the root, never a user-invoked skill"
    (holds (for-all [[all root] repository]
             (let [order (map :name (compiler/dependency-order all root))]
               (and (= order (distinct order))
                    (not-any? #{(:name root)} order)
                    (not-any? #(user-invoked? (first (filter (comp #{%} :name) all))) order))))))

  (it "gives each inlined skill exactly one section in the compiled file"
    (holds (for-all [[all root] repository]
             (let [compiled (compiler/compile-skill all root)]
               (every? #(= 1 (count (re-seq (re-pattern (str "(?m)^# Skill: " % "$")) compiled)))
                       (cons (:name root) (map :name (compiler/dependency-order all root))))))))

  (it "compiles exactly one file per skill"
    (holds (for-all [all skills]
             (= (set (map #(compiler/compiled-path (:name %)) all))
                (set (keys (compiler/compile-all all))))))))

(defn- apply-plan [existing {:keys [write delete]}]
  (apply dissoc (merge existing write) delete))

(def ^:private stale-compiled
  (gen/map (gen/fmap #(str "compiled/" % ".md") (gen/elements (conj names "orphan")))
           (gen/elements ["" "old" "# Skill: alpha\n"])
           {:max-elements 4}))

(describe "compile-skills command properties"
  (it "leaves a compiled directory that --check accepts and a rerun does not touch"
    (holds (for-all [all skills existing stale-compiled]
             (let [compiled (apply-plan existing (commands/compile-skills [] all existing))]
               (and (= {:exit 0} (commands/compile-skills ["--check"] all compiled))
                    (= {:exit 0 :write {} :delete []} (commands/compile-skills [] all compiled))))))))
