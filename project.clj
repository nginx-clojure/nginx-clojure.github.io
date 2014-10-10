(defproject nginx-clojure-site  "0.2.6"
  :description "Nginx module for clojure & java programming"
  :url "https://github.com/nginx-clojure/nginx-clojure"
  :license {:name "BSD 3-Clause license"
            :url "http://opensource.org/licenses/BSD-3-Clause"}
  :dependencies [
                 [org.clojure/clojure "1.5.1"]
                 ]
  :plugins [[lein-junit "1.1.7"]]
  ;; CLJ source code path
  :source-paths ["src/clojure"]
  :target-path "target/"
  :global-vars {*warn-on-reflection* true
                *assert* false}
;  :java-source-paths ["src/java"]
  :javac-options ["-target" "1.6" "-source" "1.6" "-g" "-nowarn"]
  ;; Directory in which to place AOT-compiled files. Including %s will
  ;; splice the :target-path into this value.
  :compile-path "target/classes"
  ;; Leave the contents of :source-paths out of jars (for AOT projects).
  :omit-source false
  :profiles {
             :dev {:dependencies [
                                  [clj-http "0.7.8"]
                                   [org.clojure/data.json "0.2.2"]
                                  ]}
             })