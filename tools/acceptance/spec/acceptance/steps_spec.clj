(ns acceptance.steps-spec
  (:require [acceptance.steps :as steps]
            [speclj.core :refer [describe it should=]]))

(def vocabulary
  ["a fixture repository"
   "the project repository"
   "a skill <name> in bucket <bucket>"
   "the skill <name> in bucket <bucket> has body <body>"
   "the skill <name> in bucket <bucket> is user-invoked"
   "the file <path> has content <content>"
   "the file <path> is deleted"
   "I run <command>"
   "I run compile-skill"
   "the exit code is <code>"
   "stdout is empty"
   "stderr is empty"
   "stdout includes <text>"
   "stderr includes <text>"
   "stdout is exactly the file <path>"
   "the file <path> is exactly <content>"
   "the file <path> includes <text>"
   "the file <path> has <count> occurrences of <text>"
   "the file <path> does not exist"
   "in the file <path>, <first> comes before <second>"
   "the compiled file for <name> is <presence>"
   "the directory compiled lists <names>"
   "the compiled directory is unchanged since the last compile-skills run"
   "the file <path> is unchanged since the last compile-skills run"
   "running compile-skills again changes nothing"])

(defn- matching [text]
  (count (filter #(re-matches (:pattern %) text) (:handlers steps/suite))))

(describe "step vocabulary"
  (it "matches every step shape with exactly one handler"
    (should= (repeat (count vocabulary) 1) (map matching vocabulary)))

  (it "matches no unknown step"
    (should= 0 (matching "a wobbly step"))))
