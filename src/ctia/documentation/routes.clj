(ns ctia.documentation.routes
  (:require
   [ctia.lib.compojure.api.core :refer [context GET]]
   [clojure.core.memoize :as memo]
   [hiccup.page :as page]
   [markdown.core :refer [md-to-html-string]]
   [ring.util.mime-type :as mime-type]
   [ring.util.response :refer [resource-response] :as response]
   [ring.middleware.content-type :as content-type]))

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

(def doc-resource-prefix "ctia/public/doc")

(defn decorate-markdown
  "decorate an html converted markdown file with css and js"
  [html-body]
  (page/html5
   head-tpl
   [:body
    [:script "hljs.initHighlightingOnLoad();"]
    [:div {:style page-style
           :class page-class}
     html-body]]))

(defn render-request
  "read the requested file from resources, render it if needed"
  [path-info]
  (resource-response (str doc-resource-prefix path-info)
                     {:allow-symlinks? false}))

(def render-request-with-cache
  "request cache wrapper"
  (memo/ttl
   render-request
   :ttl/threshold cache-ttl-ms))

(defn markdown->html [resp]
  (let [body (-> resp
                 (get :body)
                 (slurp)
                 (md-to-html-string)
                 (decorate-markdown))]
    (-> resp
        (assoc :body body)
        (response/content-type "text/html")
        (response/charset "UTF-8")
        (response/header "Content-Length" (count (.getBytes body "UTF-8"))))))

(defn render-resource-file [handler]
  (letfn [(render [resp]
            (case (response/get-header resp "Content-Type")
              "text/markdown" (markdown->html resp)
              resp))]
    (fn
      ([req]
       (some-> req (handler) (render)))
      ([req respond raise]
       (handler req
                (fn [resp]
                  (some-> resp (render) (respond)))
                raise)))))

(defn documentation-routes []
  (context "/doc" []
    (GET "/*" req
      :no-doc true
      :middleware [[render-resource-file]
                   [content-type/wrap-content-type
                    {:mime-types (merge mime-type/default-mime-types
                                        mime-overrides)}]]
      (render-request-with-cache (:path-info req)))))
