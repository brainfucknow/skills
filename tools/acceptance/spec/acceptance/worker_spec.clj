(ns acceptance.worker-spec
  (:require [acceptance.worker :as worker]
            [cheshire.core :as json]
            [clojure.string :as str]
            [speclj.core :refer [describe it should= should]]))

(def job {"id" "m1" "feature_json" "m1/feature.json" "generated_dir" "gen" "work_dir" "m1"})

(defn- answer [run]
  (dissoc (worker/outcome job run) "duration"))

(describe "outcome"
  (it "reports test_success when every execution passes"
    (should= {"id" "m1" "outcome" "test_success" "output" "PASS a/example_1" "error" ""}
             (answer (fn [ir-path generated-dir]
                       (should= ["m1/feature.json" "gen"] [ir-path generated-dir])
                       [{:name "a/example_1" :passed? true}]))))

  (it "reports test_failure when any execution fails"
    (should= "test_failure"
             (get (answer (constantly [{:name "a/example_1" :passed? true}
                                       {:name "a/example_2" :passed? false :diagnostic "no"}]))
                  "outcome")))

  (it "reports infrastructure_error when the tests cannot run"
    (should= {"id" "m1" "outcome" "infrastructure_error" "output" "" "error" "boom"}
             (answer (fn [_ _] (throw (ex-info "boom" {}))))))

  (it "reports a duration in nanoseconds"
    (should (integer? (get (worker/outcome job (constantly [])) "duration")))))

(describe "serve"
  (it "answers each job line with one JSON line and skips blank lines"
    (let [lines (atom [])]
      (worker/serve (java.io.BufferedReader. (java.io.StringReader. (str (json/generate-string job) "\n\nnot json\n")))
                    #(swap! lines conj %)
                    (constantly [{:name "a/example_1" :passed? true}]))
      (should= [["m1" "test_success"] [nil "infrastructure_error"]]
               (map #(let [response (json/parse-string %)] [(response "id") (response "outcome")]) @lines))
      (should (every? #(not (str/includes? % "\n")) @lines)))))
