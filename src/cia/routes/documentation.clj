(ns cia.routes.documentation
  (:require
   [compojure.api.sweet :refer :all]
   [ring.util.http-response :refer :all]
   [markdown.core :refer [md-to-html-string]]
   [hiccup.core :as h]
   [hiccup.page :as page]
   [ring.util.mime-type :refer [ext-mime-type]]
   [clojure.java.io :as io]))

(def mime-overrides {"md" "text/markdown"})
(def page-style "width:980px;padding:45px;")
(def page-class "markdown-body")
(def additional-css ["css/github-markdown.css"])

(defn get-file-content [path]
  (slurp (io/resource
          path)))

(def head-tpl [:head (map page/include-css additional-css)])

(defn decorate [html-body]
  "decorate an html converted markdown file"
  (page/html5
   head-tpl
   [:body
    [:div {:style page-style
           :class page-class}
     html-body]]))

(defn render-markdown [file]
  "render a mardown file into an html webpage"
  (let [response  (-> file
                      md-to-html-string
                      decorate
                      )]
    {:status 200
     :headers {"Content-Type" "text/html"}
     :body response}))

(defn render-css [file type]
  "render a css file"
  {:status 200
   :headers {"Content-Type" type}
   :body file})

(defn render-default [file type]
  "default render for unknown file types"
  {:status 200
   :headers {"Content-Type" type}
   :body file})

(defn render [file type]
  "render a file by type"
  (condp = type
    "text/markdown" (render-markdown file)
    "text/css" (render-css file type)
    (render-default file type)))

(defroutes documentation-routes
  (context "/doc" []
    (GET "/*.*" req
      :no-doc true
      (let [file-path (subs (:path-info req) 1)
            file-content (get-file-content file-path)
            file-type (ext-mime-type file-path mime-overrides)]
        (render file-content file-type)))))
