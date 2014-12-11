(ns tool
  (:require [clj-http.client :as client]
            [clojure.data.json :as json])
  )

(defn md-html [file]
  (-> file (.getAbsolutePath) (str ".html") (clojure.java.io/file)))

(defn render-md-file 
  [md-file]
 (let [text (slurp md-file)
       resp (client/post "https://api.github.com/markdown" 
                         {:form-params {:text text}
                               :content-type :json})]
   (spit (md-html  md-file)  (:body resp))) )

(defn render-all-updated
  [dir]
  (doseq [f (file-seq (clojure.java.io/file dir)) 
          :let [hf (md-html  f)]
          :when (and (.endsWith (.getName f) ".md")
                     (> (.lastModified f) (.lastModified  (md-html  f) )))]
      (println (.getAbsolutePath f))
      (render-md-file f)))