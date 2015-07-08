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
  []
  (for [f (file-seq (clojure.java.io/file ".")) 
        :let [hf (md-html  f)]
        :when (and (.endsWith (.getName f) ".md")
                   (> (.lastModified f) (.lastModified  (md-html  f) )))]
    (do 
      (println (.getAbsolutePath f))
      (render-md-file f)
      (.getName f))
))

(defn load-all-content []
  (->> ["content-template"
        "footer" "header" "CoreFeatures.md" "Configuration.md"
        "HISTORY.md" "Installation.md" "More.md" "QuickStart.md"
        "StaticFile.md" "UserfullLinks.md"] (map (fn [v] [v (slurp (str v ".html"))])) (into {})))

(defn gen-html [all-contents, content-key, out-file]
  (spit out-file (-> 
                   (all-contents "content-template")
                   (clojure.string/replace-first "#{header}" (all-contents "header"))
                   (clojure.string/replace-first "#{content}" (all-contents content-key))
                   (clojure.string/replace-first "#{footer}" (all-contents "footer"))
                   )))

(def md-file-map
  {"CoreFeatures.md"  "index.html"
   "Configuration.md" "configuration.html"
         "HISTORY.md" "downloads.html"
    "Installation.md" "installation.html"
        "More.md"     "more.html" 
      "QuickStart.md" "quickstart.html"
   "UserfullLinks.md" "userfullLinks.html"})

(defn need-re-gen [md]
  (let [mdf (clojure.java.io/file md)
        mhf (clojure.java.io/file (str md ".html"))
        tf (clojure.java.io/file "content-template.html")
        ff (clojure.java.io/file "footer.html")
        hf (clojure.java.io/file "header.html")
        rf (clojure.java.io/file (md-file-map md))
        tmdf (.lastModified mdf)
        tmhf (.lastModified mhf)
        tff (.lastModified ff)
        thf (.lastModified hf)
        ttf (.lastModified tf)
        trf (.lastModified rf)
        ]
    ;(println md "\n { " mdf tmdf "\n" mhf tmhf "\n" tf tff "\n" ff tff "\n" hf thf "\n" tf ttf "\n" rf trf "}")
    (or (< (.lastModified mhf) (.lastModified mdf))
        (< (.lastModified rf) (.lastModified ff))
        (< (.lastModified rf) (.lastModified hf))
        (< (.lastModified rf) (.lastModified tf) )
        (< (.lastModified rf) (.lastModified mhf)))))

(defn gen-all-updated-html []
  (let [updated-md-htmls (doall (render-all-updated))
        all-contents (load-all-content)]
    (doseq [md (keys md-file-map) 
            :when (need-re-gen md)]
      (println "gen " (md-file-map md))
      (gen-html all-contents md (md-file-map md)))))


