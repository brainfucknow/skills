(ns compile-skills.main
  "File system adapter for the compile commands. Paths resolve against the
  current working directory."
  (:require [babashka.fs :as fs]
            [clojure.string :as str]
            [compile-skills.commands :as commands]
            [compile-skills.compiler :as compiler]))

(defn- relative-path [root path]
  (str/join "/" (map str (fs/relativize root path))))

(defn- read-files [dir]
  (into {}
        (for [path (fs/glob dir "**" {:hidden true})
              :when (fs/regular-file? path)]
          [(relative-path dir path) (slurp (fs/file path))])))

(defn- read-skill [bucket dir]
  {:name (str (fs/file-name dir))
   :bucket bucket
   :files (read-files dir)})

(defn read-skills []
  (vec (for [bucket compiler/compiled-buckets
             :let [bucket-dir (fs/path "skills" bucket)]
             :when (fs/directory? bucket-dir)
             dir (sort (fs/list-dir bucket-dir))
             :when (fs/regular-file? (fs/path dir "SKILL.md"))]
         (read-skill bucket dir))))

(defn read-compiled []
  (into {}
        (for [path (when (fs/directory? "compiled") (fs/list-dir "compiled"))
              :when (and (fs/regular-file? path) (str/ends-with? (str path) ".md"))]
          [(str "compiled/" (fs/file-name path)) (slurp (fs/file path))])))

(defn- apply-changes! [{:keys [write delete]}]
  (doseq [[path content] write]
    (fs/create-dirs (fs/parent path))
    (spit path content))
  (doseq [path delete]
    (fs/delete-if-exists path)))

(defn- finish! [{:keys [exit out err] :as result}]
  (apply-changes! result)
  (when out
    (print out)
    (flush))
  (binding [*out* *err*]
    (doseq [line err]
      (println line)))
  (System/exit exit))

(defn- with-skills-dir [f]
  (if (fs/directory? "skills")
    (f)
    {:exit 1 :err ["No skills/ directory in the current directory"]}))

(defn compile-skills-main [args]
  (finish! (with-skills-dir #(commands/compile-skills args (read-skills) (read-compiled)))))

(defn compile-skill-main [args]
  (finish! (with-skills-dir #(commands/compile-skill args (read-skills)))))
