(ns tool
  (:require [clj-http.client :as client]
            [clojure.data.json :as json])
  )

(defn render-md-file 
  [md-file]
 (let [text (slurp (clojure.java.io/file md-file))
       resp (client/post "https://api.github.com/markdown" 
                         {:form-params {:text text}
                               :content-type :json})]
   (spit (clojure.java.io/file (str md-file ".html"))  (:body resp))) )
