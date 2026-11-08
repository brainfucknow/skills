(ns acceptance.worker
  "APS persistent runner worker: one JSON job per stdin line, one JSON
  response per stdout line. Diagnostics go to stderr only."
  (:require [acceptance.runtime :as runtime]
            [babashka.fs :as fs]
            [cheshire.core :as json]
            [clojure.string :as str]))

(defn entry-points
  "The generated entry points named by the metadata in generated-dir."
  [generated-dir]
  (sort (for [metadata (fs/glob (fs/path generated-dir "metadata") "*.json")
              file (get (json/parse-string (slurp (fs/file metadata))) "generated_files")]
          (str (fs/path generated-dir (fs/file-name file))))))

(defn outcome
  "The response for a job, given a function that runs the generated tests
  against an IR path and returns their results."
  [job run-entries]
  (let [started (System/nanoTime)
        response (try
                   (let [results (run-entries (get job "feature_json") (get job "generated_dir"))
                         lines (map runtime/result-line results)]
                     {"outcome" (if (every? :passed? results) "test_success" "test_failure")
                      "output" (str/join "\n" lines)
                      "error" ""})
                   (catch Exception error
                     {"outcome" "infrastructure_error"
                      "output" ""
                      "error" (str (ex-message error))}))]
    (merge {"id" (get job "id")}
           response
           {"duration" (- (System/nanoTime) started)})))

(defn- run-entries [load-entry]
  (fn [ir-path generated-dir]
    (let [entries (entry-points generated-dir)]
      (when (empty? entries)
        (throw (ex-info (str "no generated entry points in " generated-dir) {})))
      (vec (mapcat #((load-entry %) ir-path (fn [_])) entries)))))

(defn serve
  "Answers every job line from reader until end of input."
  [reader write-line run]
  (doseq [line (line-seq reader)
          :when (not (str/blank? line))]
    (let [job (try (json/parse-string line)
                   (catch Exception _ {"id" nil "unparsable" line}))]
      (write-line (json/generate-string
                   (if (contains? job "unparsable")
                     {"id" nil "outcome" "infrastructure_error" "output" ""
                      "error" (str "unparsable job: " line) "duration" 0}
                     (outcome job run)))))))

(defn worker-main []
  (let [protocol-out *out*
        load-entry (memoize (requiring-resolve 'acceptance.runner/load-entry))]
    (binding [*out* *err*]
      (serve (java.io.BufferedReader. *in*)
             (fn [line]
               (binding [*out* protocol-out]
                 (println line)
                 (flush)))
             (run-entries load-entry)))))
