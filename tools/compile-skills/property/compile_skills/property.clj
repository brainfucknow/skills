(ns compile-skills.property
  "Runs a test.check property inside a Speclj example."
  (:require [clojure.test.check :as tc]
            [speclj.core :refer [should=]]))

(def ^:private trials 200)

(defn holds
  "Fails the example with the shrunk counterexample when prop does not hold."
  [prop]
  (let [result (tc/quick-check trials prop)]
    (should= nil (when-not (:pass? result)
                   {:seed (:seed result) :shrunk (get-in result [:shrunk :smallest])}))))
