(ns compile-skills.commands
  "The compile-skills and compile-skill commands as functions from the
  repository's skills and compiled files to a result: what to print, which
  files to write or delete, and the exit code."
  (:require [clojure.string :as str]
            [compile-skills.compiler :as compiler]))

(def ^:private compile-skills-usage "Usage: bb compile-skills [--check]")

(def ^:private compile-skill-usage "Usage: bb compile-skill <name>")

(defn- failure [& lines]
  {:exit 1 :err (vec lines)})

(defn- duplicate-failure [skills]
  (when-let [duplicates (seq (compiler/duplicate-names skills))]
    (apply failure
           (for [[skill-name folders] duplicates]
             (str "Duplicate skill name " skill-name ": " (str/join ", " folders))))))

(defn- differences
  "One line per compiled path whose content does not match the expected."
  [expected existing]
  (concat
   (for [[path content] expected
         :when (not= content (get existing path))]
     (if (contains? existing path)
       (str path " is stale")
       (str path " is missing")))
   (for [path (sort (keys existing))
         :when (not (contains? expected path))]
     (str path " has no skill"))))

(defn- write-plan [expected existing]
  {:exit 0
   :write (into (sorted-map) (remove (fn [[path content]] (= content (get existing path)))) expected)
   :delete (vec (sort (remove #(contains? expected %) (keys existing))))})

(defn compile-skills
  "skills: every compiled-bucket skill. existing: compiled/*.md path to content."
  [args skills existing]
  (or (when-not (contains? #{[] ["--check"]} (vec args))
        (failure compile-skills-usage))
      (duplicate-failure skills)
      (let [expected (compiler/compile-all skills)]
        (if (= ["--check"] (vec args))
          (if-let [lines (seq (differences expected existing))]
            {:exit 1 :err (vec lines)}
            {:exit 0})
          (write-plan expected existing)))))

(defn compile-skill [args skills]
  (or (when-not (= 1 (count args))
        (failure compile-skill-usage))
      (duplicate-failure skills)
      (let [skill-name (first args)]
        (if-let [skill (first (filter #(= skill-name (:name %)) skills))]
          {:exit 0 :out (compiler/compile-skill skills skill)}
          (failure (str "Unknown skill " skill-name ": no skill folder named " skill-name
                        " under skills/" (str/join ", skills/" compiler/compiled-buckets)))))))
