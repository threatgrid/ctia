(ns cia.routes.docs
  (:require
   [compojure.api.sweet :refer :all]
   [ring.util.http-response :refer :all]
   [markdown.core :refer [md-to-html-string]]
   [hiccup.core :as h]
   [hiccup.page :as page]
   [hiccup-bridge.core :as hicv]
   [ring.util.mime-type :refer [ext-mime-type]]))

(def repos ["../doc" "doc"])
(def mime-overrides {"md" "text/markdown"})
(def page-style "width:980px;padding:45px;")
(def page-class "markdown-body")

(defn file-exists? [f]
  (.exists
   (clojure.java.io/as-file f)))

(def active-repo (first
                  (keep #(if (file-exists? %) %) repos)))

(defn get-file [path]
  (slurp (str active-repo path)))

(def head
  [:head (page/include-css "css/github-markdown.css")])

(defn decorate [html-body]
  (page/html5
   head
   [:body
    [:div {:style page-style
           :class page-class}
     (-> (hicv/html->hiccup html-body)
         first
         last
         rest)]]))

(defn render-markdown [file]
  (let [response  (-> file
                      md-to-html-string
                      decorate)]
    {:status 200
     :headers {"Content-Type" "text/html"}
     :body response}))

(defn render-css [file]
  {:status 200
   :headers {"Content-Type" "text/css"}
   :body file})

(defn render [file type]
  (condp = type
    "text/markdown" (render-markdown file)
    "text/css" (render-css file)))

(defroutes documentation-routes
  (context "/doc" []
    (GET "/*.*" req
      :no-doc true
      (let [path (:path-info req)
            file (get-file path)
            file-name (-> path
                          (clojure.string/split #"/")
                          last)
            type (ext-mime-type file-name mime-overrides)]
        (render file type)))))
