(ns acceptance.runtime-spec
  (:require [acceptance.runtime :as runtime]
            [speclj.core :refer [describe it should= should should-not should-contain should-throw]]))

(def ir
  {"background" [{"text" "a counter"}]
   "scenarios" [{"name" "S 1 adds"
                 "steps" [{"text" "add <n>" "parameters" ["n"]}
                          {"text" "the total is <total>" "parameters" ["total"]}]
                 "examples" [{"n" "2" "total" "2"} {"n" "3" "total" "4"}]}
                {"name" "S 2 starts at zero"
                 "steps" [{"text" "the total is 0"}]}]})

(def worlds (atom []))

(def suite
  {:handlers [{:pattern #"a counter" :run (fn [_] {:total 0})}
              {:pattern #"add (\S+)" :run (fn [world n] (update world :total + (parse-long n)))}
              {:pattern #"the total is (\S+)"
               :run (fn [world total]
                      (if (= (parse-long total) (:total world))
                        world
                        (runtime/fail! "expected " total ", got " (:total world))))}]
   :new-world (fn [] {:fresh true})
   :finish (fn [world passed?] (swap! worlds conj [world passed?]))})

(describe "executions"
  (it "makes one execution per example row, and one for a scenario without examples"
    (should= ["S 1 adds/example_1" "S 1 adds/example_2" "S 2 starts at zero/example_1"]
             (map :name (runtime/executions ir))))
  (it "puts the background steps first"
    (should= ["a counter" "add <n>" "the total is <total>"]
             (map #(get % "text") (:steps (first (runtime/executions ir)))))))

(describe "decode"
  (it "reads \\n as a newline and \\\\ as a backslash"
    (should= "a\nb\\nc\\x" (runtime/decode "a\\nb\\\\nc\\x"))))

(describe "resolve-capture"
  (it "fills in the step's placeholders from the example"
    (should= "skills/in-progress/alpha" (runtime/resolve-capture "skills/<bucket>/alpha" ["bucket"] {"bucket" "in-progress"})))
  (it "leaves angle text that is not a parameter alone"
    (should= "<topic>" (runtime/resolve-capture "<topic>" [] {})))
  (it "fails a missing example value"
    (should-throw clojure.lang.ExceptionInfo "missing example value for <n>"
                  (runtime/resolve-capture "<n>" ["n"] {}))))

(describe "run-feature"
  (it "passes and fails executions with a diagnostic, each in a fresh world"
    (reset! worlds [])
    (let [reported (atom [])
          results (runtime/run-feature ir ["S 1 adds" "S 2 starts at zero"] suite #(swap! reported conj %))]
      (should= [true false true] (map :passed? results))
      (should= "step \"the total is <total>\": expected 4, got 3" (:diagnostic (second results)))
      (should= results @reported)
      (should= [[{:total 2} true] [{:total 3} false] [{:total 0} true]] @worlds)))

  (it "fails unsupported and ambiguous steps"
    (let [ir {"scenarios" [{"name" "X" "steps" [{"text" "jump"}]}
                           {"name" "Y" "steps" [{"text" "add 1"}]}]}
          doubled (update suite :handlers conj {:pattern #"add 1" :run identity})
          [unsupported ambiguous] (runtime/run-feature ir ["X" "Y"] doubled identity)]
      (should-contain "unsupported step: jump" (:diagnostic unsupported))
      (should-contain "ambiguous step: add 1" (:diagnostic ambiguous))))

  (it "refuses an IR whose scenarios differ from the generated ones"
    (should-throw clojure.lang.ExceptionInfo (runtime/run-feature ir ["S 1 adds"] suite identity))))

(describe "result-line"
  (it "prints PASS or FAIL with the diagnostic on one line"
    (should= "PASS a/example_1" (runtime/result-line {:name "a/example_1" :passed? true}))
    (should= "FAIL a/example_1: x\\ny" (runtime/result-line {:name "a/example_1" :passed? false :diagnostic "x\ny"}))))
