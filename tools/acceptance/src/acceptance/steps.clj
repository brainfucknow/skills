(ns acceptance.steps
  "Step handlers for the compiled-skills features. They drive the public
  interface only: every command step runs `bb --config <repo>/bb.edn` in the
  execution's fixture directory, a fresh build/acceptance/work/<n>/."
  (:require [acceptance.runtime :as runtime :refer [fail!]]
            [babashka.fs :as fs]
            [babashka.process :as process]
            [clojure.string :as str])
  (:import [java.nio.file FileAlreadyExistsException Files]
           [java.nio.file.attribute FileAttribute]))

(defn repo-root
  "The directory of the bb.edn this process runs under."
  []
  (str (fs/canonicalize
        (if-let [config (System/getProperty "babashka.config")]
          (fs/parent config)
          (fs/cwd)))))

(defn- create-fresh-dir [parent]
  (fs/create-dirs parent)
  (loop [n 1]
    (let [dir (fs/path parent (str n))]
      (if (try (Files/createDirectory dir (make-array FileAttribute 0)) true
               (catch FileAlreadyExistsException _ false))
        (str dir)
        (recur (inc n))))))

(defn new-world []
  {:repo (repo-root)})

(defn finish [world passed?]
  (when (and passed? (:fixture world))
    (fs/delete-tree (:fixture world))))

(defn- cwd [world]
  (or (:cwd world) (fail! "no repository set up: start with a fixture repository")))

(defn- writable-path [world path]
  (when (:project? world)
    (fail! "the project repository is read-only"))
  (fs/path (cwd world) path))

(defn- write-file [world path content]
  (let [target (writable-path world path)]
    (fs/create-dirs (fs/parent target))
    (spit (fs/file target) content)
    world))

(defn- read-file [world path]
  (let [file (fs/file (cwd world) path)]
    (if (fs/regular-file? file)
      (slurp file)
      (fail! "the file " path " does not exist"))))

(defn- frontmatter [skill-name extra]
  (str "---\nname: " skill-name "\ndescription: " skill-name " fixture.\n" extra "---\n"))

(defn- write-skill [world skill-name bucket extra body]
  (let [dir (str "skills/" bucket "/" skill-name)]
    (-> world
        (write-file (str dir "/SKILL.md") (str (frontmatter skill-name extra) "\n" body "\n"))
        (write-file (str dir "/agents/openai.yaml")
                    (str "interface:\n  display_name: \"" skill-name "\"\n")))))

(defn- snapshot [world]
  (let [dir (fs/file (cwd world) "compiled")]
    (into (sorted-map)
          (for [path (when (fs/directory? dir) (fs/glob dir "**" {:hidden true}))
                :when (fs/regular-file? path)]
            [(str (fs/relativize dir path)) (slurp (fs/file path))]))))

(defn- run-command [world args]
  (let [{:keys [exit out err]}
        @(process/process (into ["bb" "--config" (str (fs/path (:repo world) "bb.edn"))] args)
                          {:dir (cwd world) :out :string :err :string :in ""})]
    {:exit exit :out out :err err}))

(defn- writes-compiled? [args]
  (and (= "compile-skills" (first args)) (not (some #{"--check"} args))))

(defn- run [world command]
  (let [args (remove str/blank? (str/split command #" "))]
    (when (and (:project? world) (writes-compiled? args))
      (fail! "the project repository is read-only: refusing to run " command))
    (let [result (run-command world args)]
      (cond-> (assoc world :last result)
        (and (writes-compiled? args) (zero? (:exit result))) (assoc :snapshot (snapshot world))))))

(defn- last-run [world]
  (or (:last world) (fail! "no command has run")))

(defn- describe-run [{:keys [exit out err]}]
  (str "exit " exit ", stdout " (pr-str out) ", stderr " (pr-str err)))

(defn- parse-count [text]
  (if (re-matches #"\d+" text)
    (parse-long text)
    (fail! "expected a whole number, got " (pr-str text))))

(defn- occurrences [text part]
  (when (empty? part)
    (fail! "cannot count occurrences of empty text"))
  (loop [from 0 found 0]
    (let [at (str/index-of text part from)]
      (if at (recur (+ at (count part)) (inc found)) found))))

(defn- expect [world ok? & message]
  (if ok? world (apply fail! message)))

(defn- stream [world which]
  (get (last-run world) (if (= "stdout" which) :out :err)))

(defn- listing [world]
  (let [dir (fs/file (cwd world) "compiled")
        names (when (fs/directory? dir) (sort (map #(str (fs/file-name %)) (fs/list-dir dir))))]
    (if (seq names) (str/join ", " names) "none")))

(defn- normalize-listing [text]
  (str/join ", " (map str/trim (str/split text #","))))

(defn- last-snapshot [world]
  (or (:snapshot world) (fail! "compile-skills has not run successfully")))

(def ^:private handlers
  [{:pattern #"a fixture repository"
    :run (fn [world]
           (let [dir (create-fresh-dir (fs/path (:repo world) "build" "acceptance" "work"))]
             (assoc world :fixture dir :cwd dir :project? false)))}
   {:pattern #"the project repository"
    :run (fn [world] (assoc world :cwd (:repo world) :project? true))}
   {:pattern #"a skill (\S+) in bucket (\S+)"
    :run (fn [world skill-name bucket] (write-skill world skill-name bucket "" (str "# " skill-name)))}
   {:pattern #"the skill (\S+) in bucket (\S+) has body (.+)"
    :run (fn [world skill-name bucket body] (write-skill world skill-name bucket "" body))}
   {:pattern #"the skill (\S+) in bucket (\S+) is user-invoked"
    :run (fn [world skill-name bucket]
           (write-skill world skill-name bucket "disable-model-invocation: true\n" (str "# " skill-name)))}
   {:pattern #"the file (\S+) has content (.+)"
    :run write-file}
   {:pattern #"the file (\S+) is deleted"
    :run (fn [world path] (fs/delete-if-exists (writable-path world path)) world)}
   {:pattern #"I run (.+)"
    :run run}
   {:pattern #"the exit code is (\S+)"
    :run (fn [world code]
           (expect world (= (parse-count code) (:exit (last-run world)))
                   "expected exit code " code "; " (describe-run (last-run world))))}
   {:pattern #"(stdout|stderr) is empty"
    :run (fn [world which]
           (expect world (empty? (stream world which))
                   "expected empty " which "; " (describe-run (last-run world))))}
   {:pattern #"(stdout|stderr) includes (.+)"
    :run (fn [world which text]
           (expect world (str/includes? (stream world which) text)
                   "expected " which " to include " (pr-str text) "; " (describe-run (last-run world))))}
   {:pattern #"stdout is exactly the file (\S+)"
    :run (fn [world path]
           (expect world (= (read-file world path) (:out (last-run world)))
                   "expected stdout to equal " path "; " (describe-run (last-run world))))}
   {:pattern #"the file (\S+) is exactly (.+)"
    :run (fn [world path content]
           (let [actual (read-file world path)]
             (expect world (= content actual) "expected " path " to be " (pr-str content) ", got " (pr-str actual))))}
   {:pattern #"the file (\S+) includes (.+)"
    :run (fn [world path text]
           (let [actual (read-file world path)]
             (expect world (str/includes? actual text)
                     "expected " path " to include " (pr-str text) ", got " (pr-str actual))))}
   {:pattern #"the file (\S+) has (\S+) occurrences of (.+)"
    :run (fn [world path expected text]
           (let [actual (occurrences (read-file world path) text)]
             (expect world (= (parse-count expected) actual)
                     "expected " expected " occurrences of " (pr-str text) " in " path ", found " actual)))}
   {:pattern #"the file (\S+) does not exist"
    :run (fn [world path]
           (expect world (not (fs/exists? (fs/path (cwd world) path))) "expected " path " not to exist"))}
   {:pattern #"in the file (\S+), (.+?) comes before (.+)"
    :run (fn [world path first-text second-text]
           (let [actual (read-file world path)
                 first-at (str/index-of actual first-text)
                 second-at (str/index-of actual second-text)]
             (expect world (and first-at second-at (< first-at second-at))
                     "expected " (pr-str first-text) " before " (pr-str second-text) " in " path
                     " (positions " first-at " and " second-at ")")))}
   {:pattern #"the compiled file for (\S+) is (\S+)"
    :run (fn [world skill-name presence]
           (let [exists? (fs/exists? (fs/path (cwd world) "compiled" (str skill-name ".md")))]
             (case presence
               "present" (expect world exists? "expected compiled/" skill-name ".md to exist")
               "absent" (expect world (not exists?) "expected compiled/" skill-name ".md not to exist")
               (fail! "expected present or absent, got " (pr-str presence)))))}
   {:pattern #"the directory compiled lists (.+)"
    :run (fn [world expected]
           (let [actual (listing world)]
             (expect world (= (normalize-listing expected) actual)
                     "expected compiled to list " (pr-str expected) ", got " (pr-str actual))))}
   {:pattern #"the compiled directory is unchanged since the last compile-skills run"
    :run (fn [world]
           (expect world (= (last-snapshot world) (snapshot world))
                   "compiled changed since the last compile-skills run"))}
   {:pattern #"the file (\S+) is unchanged since the last compile-skills run"
    :run (fn [world path]
           (let [before (get (last-snapshot world) (str/replace path #"^compiled/" ""))]
             (expect world (and (str/starts-with? path "compiled/") before (= before (read-file world path)))
                     path " changed since the last compile-skills run")))}
   {:pattern #"running compile-skills again changes nothing"
    :run (fn [world]
           (let [before (snapshot world)
                 after-run (run world "compile-skills")]
             (expect after-run (and (zero? (:exit (:last after-run))) (= before (snapshot after-run)))
                     "a second compile-skills run changed compiled; " (describe-run (:last after-run)))))}])

(def suite
  {:handlers handlers
   :new-world new-world
   :finish finish})
