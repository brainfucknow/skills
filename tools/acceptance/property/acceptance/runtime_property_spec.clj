(ns acceptance.runtime-property-spec
  (:require [acceptance.generator :as generator]
            [acceptance.runtime :as runtime]
            [clojure.string :as str]
            [clojure.test.check.generators :as gen]
            [clojure.test.check.properties :refer [for-all]]
            [compile-skills.property :refer [holds]]
            [speclj.core :refer [describe it]]))

(defn- encode [value]
  (-> value (str/replace "\\" "\\\\") (str/replace "\n" "\\n")))

(def ^:private value
  (gen/fmap str/join (gen/vector (gen/frequency [[6 gen/char-ascii] [1 (gen/elements [\\ \newline \n])]]) 0 40)))

(describe "acceptance runtime properties"
  (it "decodes every encoded step value back to itself"
    (holds (for-all [v value]
             (= v (runtime/decode (encode v))))))

  (it "fills placeholders with example values taken literally"
    (holds (for-all [v value]
             (= v (runtime/resolve-capture "<x>" ["x"] {"x" (encode v)})))))

  (it "maps any feature path to a metadata file name of lowercase alphanumeric runs"
    (holds (for-all [path value]
             (re-matches #"([a-z0-9]+(-[a-z0-9]+)*)?\.json" (generator/metadata-name path))))))
