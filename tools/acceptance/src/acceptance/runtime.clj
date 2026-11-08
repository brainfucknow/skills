(ns acceptance.runtime
  "Runs the scenario executions of a parsed feature (APS JSON IR) through a
  suite of step handlers. A suite is {:handlers :new-world :finish}: each
  handler is {:pattern regex :run (fn [world & captures] world')}, :new-world
  makes a fresh world per execution, and :finish cleans one up."
  (:require [cheshire.core :as json]
            [clojure.string :as str]))

(defn fail!
  "Fails the current execution with a diagnostic."
  [& parts]
  (throw (ex-info (apply str parts) {::failure true})))

(defn load-ir [path]
  (json/parse-string (slurp path)))

(defn executions
  "One execution per example row, or one with an empty example, each with
  the background steps first."
  [ir]
  (for [scenario (get ir "scenarios")
        [index example] (map-indexed vector (or (not-empty (get scenario "examples")) [{}]))]
    {:name (str (get scenario "name") "/example_" (inc index))
     :steps (concat (get ir "background") (get scenario "steps"))
     :example example}))

(defn decode
  "Reads `\\n` as a newline and `\\\\` as a backslash."
  [value]
  (str/replace value #"\\(\\|n)" (fn [[_ escaped]] (if (= "n" escaped) "\n" "\\"))))

(defn resolve-capture
  "Replaces each `<placeholder>` of the step's parameters with its example
  value, then decodes escapes."
  [capture parameters example]
  (decode
   (str/replace capture #"<([A-Za-z0-9_]+)>"
                (fn [[whole key]]
                  (cond
                    (not (some #{key} parameters)) whole
                    (contains? example key) (get example key)
                    :else (fail! "missing example value for <" key ">"))))))

(defn- matches [handlers text]
  (keep (fn [handler]
          (when-let [found (re-matches (:pattern handler) text)]
            [handler (if (string? found) [] (rest found))]))
        handlers))

(defn run-step [handlers world example step]
  (let [text (get step "text")
        found (matches handlers text)]
    (case (count found)
      0 (fail! "unsupported step: " text)
      1 (let [[handler captures] (first found)
              values (map #(resolve-capture % (get step "parameters") example) captures)]
          (apply (:run handler) world values))
      (fail! "ambiguous step: " text))))

(defn- step-failure [step error]
  (str "step \"" (get step "text") "\": "
       (if (::failure (ex-data error))
         (ex-message error)
         (str (.getName (class error)) ": " (ex-message error)))))

(defn run-execution
  "Runs one execution in a fresh world: {:name :passed? :diagnostic}."
  [{:keys [handlers new-world finish]} {:keys [name steps example]}]
  (let [outcome (reduce (fn [{:keys [world]} step]
                          (try
                            {:world (run-step handlers world example step)}
                            (catch Exception error
                              (reduced {:world world :diagnostic (step-failure step error)}))))
                        {:world (new-world)}
                        steps)
        passed? (not (:diagnostic outcome))]
    (when finish
      (finish (:world outcome) passed?))
    (cond-> {:name name :passed? passed?}
      (not passed?) (assoc :diagnostic (:diagnostic outcome)))))

(defn check-structure!
  "Refuses an IR whose scenarios differ from those the entry point was
  generated for."
  [ir scenario-names]
  (let [actual (mapv #(get % "name") (get ir "scenarios"))]
    (when (not= actual (vec scenario-names))
      (throw (ex-info (str "IR scenarios " (pr-str actual)
                           " do not match the generated scenarios " (pr-str scenario-names))
                      {})))))

(defn run-feature
  "Runs every execution of the IR, calling report with each result as it
  finishes. Returns the results."
  [ir scenario-names suite report]
  (check-structure! ir scenario-names)
  (mapv (fn [execution]
          (doto (run-execution suite execution) report))
        (executions ir)))

(defn result-line [{:keys [name passed? diagnostic]}]
  (if passed?
    (str "PASS " name)
    (str "FAIL " name ": " (str/replace diagnostic "\n" "\\n"))))
