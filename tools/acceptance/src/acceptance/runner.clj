(ns acceptance.runner
  "`bb acceptance` and `bb acceptance-mutate`: parse features with the APS
  gherkin-parser, generate entry points, and run them."
  (:require [acceptance.generator :as generator]
            [acceptance.runtime :as runtime]
            [babashka.fs :as fs]
            [babashka.process :as process]
            [cheshire.core :as json]
            [clojure.string :as str]))

(defn- infrastructure-error [message]
  (ex-info message {::exit 2}))

(defn- tool-dirs
  "Where `sf tool ensure` puts wrappers: this checkout's .swarmforge/bin, then
  the main checkout's when this is a task worktree."
  [repo]
  (let [{:keys [exit out]} @(process/process ["git" "rev-parse" "--git-common-dir"]
                                              {:dir repo :out :string :err :string})
        common (when (zero? exit) (fs/path repo (str/trim out)))]
    (distinct (cond-> [(str (fs/path repo ".swarmforge" "bin"))]
                common (conj (str (fs/normalize (fs/path (fs/parent (fs/absolutize common)) ".swarmforge" "bin"))))))))

(defn find-wrapper
  "The first executable wrapper for tool in dirs."
  [dirs tool]
  (or (first (filter fs/executable? (map #(str (fs/path % tool)) dirs)))
      (throw (infrastructure-error
              (str tool " is not installed: run `swarmforge/bin/sf tool ensure " tool "`")))))

(defn- tool-wrapper [repo tool]
  (find-wrapper (tool-dirs repo) tool))

(defn- parse-feature! [repo parser feature ir-path]
  (fs/create-dirs (fs/parent ir-path))
  (let [{:keys [exit err]} @(process/process [parser feature (str ir-path)]
                                             {:dir repo :out :string :err :string})]
    (when-not (zero? exit)
      (throw (infrastructure-error (str "gherkin-parser failed on " feature ": " (str/trim err)))))))

(defn- feature-stem [feature]
  (str/replace (str (fs/file-name feature)) #"\.feature\z" ""))

(defn prepare!
  "Parses the feature into <build>/ir/ and generates into <build>/generated/.
  Returns the IR path and the generated entry point."
  [repo parser feature build]
  (let [ir-path (str (fs/path build "ir" (str (feature-stem feature) ".json")))
        out-dir (str (fs/path build "generated"))]
    (parse-feature! repo parser feature ir-path)
    (generator/generate! ir-path out-dir)
    {:ir-path ir-path
     :entry (str (fs/path out-dir (generator/entry-file-name ir-path)))}))

(defn load-entry
  "Loads a generated entry point and returns its run-tests function."
  [entry]
  (try
    (load-file entry)
    (catch Exception error
      (throw (infrastructure-error (str "cannot load " entry ": " (ex-message error)))))))

(defn- run-feature! [repo parser feature]
  (let [{:keys [ir-path entry]} (prepare! repo parser feature "build/acceptance")
        run-tests (load-entry entry)]
    (try
      (run-tests ir-path #(println (runtime/result-line %)))
      (catch Exception error
        (throw (infrastructure-error (str "cannot run " entry ": " (ex-message error))))))))

(defn- features [args]
  (doseq [feature args]
    (when-not (fs/regular-file? feature)
      (throw (infrastructure-error (str "no feature file " feature)))))
  (or (seq args)
      (sort (map str (fs/glob "features" "*.feature")))))

(defn- exit-with-error [error]
  (binding [*out* *err*]
    (println (ex-message error)))
  (System/exit (or (::exit (ex-data error)) 1)))

(defn acceptance-main [args]
  (try
    (let [repo (runtime/repo-root)
          parser (tool-wrapper repo "gherkin-parser")
          selected (features args)]
      (doseq [dir ["ir" "generated" "work"]]
        (fs/delete-tree (fs/path "build" "acceptance" dir)))
      (let [results (doall (mapcat #(run-feature! repo parser %) selected))
            failed (count (remove :passed? results))]
        (println (str (- (count results) failed) " passed, " failed " failed"))
        (System/exit (if (zero? failed) 0 1))))
    (catch clojure.lang.ExceptionInfo error
      (exit-with-error error))))

(def ^:private mutation-build "build/acceptance-mutation")

(defn text-report
  "The APS text report for a gherkin-mutator JSON report."
  [{:strs [summary results]}]
  (let [skipped (contains? summary "SkippedScenarios")]
    (str/join "\n"
              (cons (str "total=" (get summary "Total") " killed=" (get summary "Killed")
                         " survived=" (get summary "Survived") " errors=" (get summary "Errors")
                         (when skipped
                           (str " skipped_scenarios=" (get summary "SkippedScenarios")
                                " skipped_mutations=" (get summary "SkippedMutations"))))
                    (for [result results]
                      (format "%-8s %s" (get result "Status")
                              (get-in result ["Mutation" "Description"])))))))

(defn- print-report [json-report]
  (if (str/blank? json-report)
    (throw (ex-info "gherkin-mutator printed no report" {}))
    (println (text-report (json/parse-string json-report)))))

(defn mutate-main [args]
  (try
    (when (not= 1 (count args))
      (throw (infrastructure-error "Usage: bb acceptance-mutate <feature>")))
    (let [repo (runtime/repo-root)
          feature (str/replace (first args) #"\A\./" "")
          parser (tool-wrapper repo "gherkin-parser")
          mutator (tool-wrapper repo "gherkin-mutator")]
      (when-not (fs/regular-file? feature)
        (throw (infrastructure-error (str "no feature file " feature))))
      (fs/delete-tree mutation-build)
      (prepare! repo parser feature mutation-build)
      (let [{:keys [exit out]} @(process/process
                             [mutator "--feature" feature
                              "--work-dir" mutation-build
                              "--generated-dir" (str mutation-build "/generated")
                              "--runner-worker" "bb acceptance-worker"
                              "--workers" "4"
                              "--level" "hard"
                              "--status-interval" "30s"
                              "--json"]
                             {:dir repo :in :inherit :out :string :err :inherit})]
        (print-report out)
        (System/exit exit)))
    (catch clojure.lang.ExceptionInfo error
      (exit-with-error error))))
