(defproject nginx-clojure-site  "0.5.0"
  :description "the source of nginx-clojure website"
  :url "https://github.com/nginx-clojure/nginx-clojure.github.io"
  :license {:name "BSD 3-Clause license"
            :url "http://opensource.org/licenses/BSD-3-Clause"}
  :dependencies [
                 [org.clojure/clojure "1.9.0"]
                 ]
  :plugins [[lein-codox "0.9.0"]]
  ;; CLJ source code path
  :source-paths ["src/clojure"]
  :target-path "target/"
  :global-vars {*warn-on-reflection* true
                *assert* false}
;  :java-source-paths ["src/java"]
  :javac-options ["-target" "1.8" "-source" "1.8" "-g" "-nowarn"]
  ;; Directory in which to place AOT-compiled files. Including %s will
  ;; splice the :target-path into this value.
  :compile-path "target/classes"
  ;; Leave the contents of :source-paths out of jars (for AOT projects).
  :omit-source false
  :codox {
          :project {:name "nginx-clojure", :version "0.5.0", :description "Nginx module for clojure/groovy/java programming"}
          :source-paths ["../nginx-clojure/src/clojure"
                         "../nginx-clojure/nginx-clojure-embed/src/clojure"]
          :output-path "api"
          ;:metadata {:doc/format :markdown}
          :namespaces ["nginx.clojure.core" "nginx.clojure.session" 
                       "nginx.clojure.embed"]
          :source-uri {
                          #".*nginx-clojure-embed.*" "https://github.com/nginx-clojure/nginx-clojure/tree/master/nginx-clojure-embed/src/clojure/{classpath}#L{line}"
                          #".*" "https://github.com/nginx-clojure/nginx-clojure/blob/master/src/clojure/{classpath}#L{line}"
                      }}
  :profiles {
             :dev {:dependencies [
                                  [clj-http "0.7.8"]
                                  [org.clojure/data.json "0.2.2"]
                                  [nginx-clojure "0.5.0"]
                                  [nginx-clojure/nginx-clojure-embed "0.5.0"]
                                  [ring/ring-core "1.2.1"]
                                  ]}
             })