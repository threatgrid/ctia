(ns ctia.test-helpers.http
  (:require
   [clojure.string :as string]
   [clj-momo.test-helpers.http-assert-1 :as mthh]
   [ctia.lib.utils :refer [service-subgraph]]
   [ctia.schemas.core :refer [HTTPShowServices]]
   [ctia.test-helpers.core :as th]
   [puppetlabs.trapperkeeper.app :as app]
   [schema.core :as s]))

(def api-key "45c1f5e3f05d0")

(defn doc-id->rel-url
  "given a doc id (url) make a relative url for test queries"
  [doc-id]
  (when doc-id
    (string/replace doc-id #".*(?=ctia)" "")))

(defn assert-post [app & args]
  (apply (mthh/with-port-fn-and-api-key
           (partial th/get-http-port app)
           api-key
           mthh/assert-post)
         args))

(s/defn app->HTTPShowServices :- HTTPShowServices [app]
  (-> app
      app/service-graph
      (service-subgraph
        :CTIAHTTPServerService [:get-port]
        :ConfigService [:get-in-config])))

;; clj-http.fake API for isolated Trapperkeeper apps

(defmacro with-fake-routes-in-isolation
  "Makes all requests for the current app in the current thread first match against given routes.
  If no route matches, an exception is thrown."
  [app routes & body]
  `((-> ~app
        app/service-graph
        :CTIATestGlobalRoutesService
        :with-fake-routes-in-isolation)
    ~routes
    #(do ~@body)))

(defmacro with-fake-routes
  "Makes all requests for the current app in the current thread first match against given routes.
  The actual HTTP request will be sent only if no matches are found."
  [app routes & body]
  `((-> ~app
        app/service-graph
        :CTIATestGlobalRoutesService
        :with-fake-routes)
    ~routes
    #(do ~@body)))

(defmacro with-global-fake-routes-in-isolation
  "Makes all requests for the current app first match against given routes.
  If no route matches, an exception is thrown."
  [app routes & body]
  `((-> ~app
        app/service-graph
        :CTIATestGlobalRoutesService
        :with-global-fake-routes-in-isolation)
    ~routes
    #(do ~@body)))

(defmacro with-global-fake-routes
  "Makes all wrapped clj-http requests first match against given routes.
  The actual HTTP request will be sent only if no matches are found."
  [app routes & body]
  `((-> ~app
        app/service-graph
        :CTIATestGlobalRoutesService
        :with-global-fake-routes)
    ~routes
    #(do ~@body)))
