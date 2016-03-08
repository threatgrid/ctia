(ns cia.routes.docs
  (:require
   [compojure.api.sweet :refer :all]
   [ring.util.http-response :refer :all]
   [markdown.core :refer [md-to-html-string]]))

(def repos ["../doc" "doc"])

(defn file-exists? [f]
  (.exists
   (clojure.java.io/as-file f)))

(def active-repo (first
                  (keep #(if (file-exists? %) %) repos)))

(defn get-file [path]
  (slurp (str active-repo path)))

(defn render-markdown [file]
  (md-to-html-string file))

(defroutes documentation-routes
  (context "/doc" []
    (GET "/*.*" req
      :no-doc true
      (let [path (:path-info req)
            file (get-file path)]

        {:status 200
         :headers {"Content-Type" "text/html"}
         :body (render-markdown file)}))))
