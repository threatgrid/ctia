(ns ctia.test-helpers.http
  (:refer-clojure :exclude [get])
  (:require
   [clojure.string :as string]
   [cheshire.core :as json]
   [ctia.schemas.core :refer [APIHandlerServices HTTPShowServices]]
   [ctia.schemas.utils :as csu]
   [clj-http.client :as client]
   [puppetlabs.trapperkeeper.app :as app]
   [schema.core :as s])
  (:import java.io.ByteArrayInputStream))

(def api-key "45c1f5e3f05d0")

(defn local-url [path port]
  (format "http://localhost:%d/%s" port path))

(defn string->input-stream
  [^String s]
  (-> s
      (.getBytes)
      (ByteArrayInputStream.)))

(defn encode-body
  [body]
  (string->input-stream
   (json/generate-string body)))

(defn get [path port & {:as options}]
  (let [options
        (merge {:as :json
                :throw-exceptions false
                :socket-timeout 10000
                :conn-timeout 10000}
               options)

        response
        (client/get (local-url path port) options)]
    ;; assoc parsed-body for backward compatibiity
    (assoc response :parsed-body (:body response))))

(defn post [path port & {:as opts}]
  (let [{:keys [body] :as options}
        (merge {:content-type :json
                :as :json
                :throw-exceptions false
                :socket-timeout 10000
                :conn-timeout 10000}
               opts)
        response
        (client/post (local-url path port)
                   (-> options
                       (cond-> body (update :body encode-body))))]
    ;; assoc parsed-body for backward compatibiity
    (assoc response :parsed-body (:body response))))

(defn delete [path port & {:as options}]
  (client/delete (local-url path port)
               (merge {:throw-exceptions false}
                      options)))

(defn put [path port & {:as options}]
  (let [{:keys [body]
         :as options}
        (merge {:content-type :json
                :as :json
                :throw-exceptions false
                :socket-timeout 10000
                :conn-timeout 10000}
               options)

        response
        (client/put (local-url path port)
                  (-> options
                      (cond-> body (update :body encode-body))))]
    ;; assoc parsed-body for backward compatibiity
    (assoc response :parsed-body (:body response))))

(defn patch [path port & {:as options}]
  (let [{:keys [body]
         :as options}
        (merge {:content-type :json
                :as :json
                :throw-exceptions false
                :socket-timeout 10000
                :conn-timeout 10000}
               options)

        response
        (client/patch (local-url path port)
                    (-> options
                        (cond-> body (update :body encode-body))))]
    (assoc response :parsed-body (:body response))))


(defn with-port-fn
  "Helper to compose a fn that knows how to lookup an HTTP port with
  an HTTP method fn (from above)
  Example:
    (def post (http/with-port-fn get-http-port http/post))"
  [port-fn http-fn]
  (fn [path & options]
    (apply (partial http-fn path (port-fn)) options)))

(defn doc-id->rel-url
  "given a doc id (url) make a relative url for test queries"
  [doc-id]
  (when doc-id
    (string/replace doc-id #".*(?=ctia)" "")))

(s/defn app->APIHandlerServices :- APIHandlerServices [app]
  (-> app
      app/service-graph
      (csu/select-service-subgraph APIHandlerServices)))

(s/defn app->HTTPShowServices :- HTTPShowServices [app]
  (-> app
      app/service-graph
      (csu/select-service-subgraph HTTPShowServices)))
