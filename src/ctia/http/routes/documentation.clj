(ns ctia.http.routes.documentation
  (:require
   [compojure.api.sweet :refer :all]
   [ring.util.http-response :refer :all]
   [markdown.core :refer [md-to-html-string]]
   [hiccup.core :as h]
   [hiccup.page :as page]
   [ring.util.mime-type :refer [ext-mime-type]]
   [clojure.java.io :as io]
   [clojure.core.memoize :as memo]))

;; set request cache ttl
(def cache-ttl-ms (* 1000 5))

;; additional types for req content type mime detection
(def mime-overrides {"md" "text/markdown"})

;; additional page formatting css
(def page-style "width:980px;padding:45px;")

;; set page css class here
(def page-class "markdown-body")

;; additional css embeded in Head
(def additional-css ["css/github-markdown.css"
                     "css/github.css"])

;; additional js embeded in head
(def additional-js ["js/highlight.pack.js"])

;; head hiccup template
(def head-tpl [:head
               (map page/include-css additional-css)
               (map page/include-js additional-js)])

(defn get-file-content [path]
  "read a file from resources, returns nil on any failure"
  (try (slurp (io/resource
               path))
       (catch Throwable e nil)))

(defn decorate-markdown [html-body]
  "decorate an html converted markdown file with css and js"
  (page/html5
   head-tpl
   [:body
    [:script "hljs.initHighlightingOnLoad();"]
    [:div {:style page-style
           :class page-class}
     html-body]]))

(defn render-markdown [file]
  "render a mardown file into an html webpage"
  {:status 200
   :headers {"Content-Type" "text/html"}
   :body (-> file
             md-to-html-string
             decorate-markdown)})

(defn render-default [file type]
  "default render for unknown file types"
  {:status 200
   :headers {"Content-Type" type}
   :body file})

(defn render [file type]
  "render a file by type"
  (condp = type
    "text/markdown" (render-markdown file)
    (render-default file type)))

(defn render-request [path-info]
  "read the requested file from resources, render it if needed"
  (let [file-path (subs path-info 1)
        file-content (get-file-content file-path)
        file-type (ext-mime-type file-path mime-overrides)]

    (if file-content
      (render file-content file-type)
      {:status 404
       :body "The requested page couldn't be found."})))

(def render-request-with-cache
  "request cache wrapper"
  (memo/ttl
   render-request 
   :ttl/threshold cache-ttl-ms))

(defroutes documentation-routes
  (context "/doc" []
    (GET "/*.*" req
      :no-doc true
      (render-request-with-cache (:path-info req)))))
