(ns ctia.test-helpers.http
  (:refer-clojure :exclude [get])
  (:require
   [clojure.string :as string]
   [clojure.edn :as edn]
   [cheshire.core :as json]
   [ctia.schemas.core :refer [APIHandlerServices HTTPShowServices]]
   [ctia.schemas.utils :as csu]
   [clj-http.client :as http]
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

(defn content-type? [expected-str]
  (fn [test-str]
    (if (some? test-str)
      (string/includes? (name test-str) expected-str)
      false)))
(def json? (content-type? "json"))
(def edn? (content-type? "edn"))

(defn parse-body
  [{{content-type "Content-Type"} :headers
    body :body}]
  (cond
    (edn? content-type) (edn/read-string body)
    (json? content-type) (json/parse-string body)
    :else body))

(defn encode-body
  [body content-type]
  (string->input-stream
   (cond
     (edn? content-type) (pr-str body)
     (json? content-type) (json/generate-string body)
     :else body)))

(def base-opts
  {:accept :edn
   :content-type :edn
   :throw-exceptions false})

(defn with-parsed-body
  [response]
  ;; assoc parsed-body for backward compatibiity
  (assoc response :parsed-body (parse-body response)))

(defn get [path port & {:as opts}]
  (let [options (merge base-opts opts)
        response
        (http/get (local-url path port)
                  options)]
    (with-parsed-body response)))

(defn post [path port & {:as opts}]
  (let [{:keys [body content-type]
         :as options}
        (merge base-opts opts)
        response
        (http/post (local-url path port)
                   (-> options
                       (cond-> body (assoc :body (encode-body body content-type)))))]
    (with-parsed-body response)))

(defn delete [path port & {:as opts}]
  (->> (http/delete (local-url path port)
                    (merge base-opts opts))
       with-parsed-body))

(defn put [path port & {:as opts}]
  (let [{:keys [body content-type]
         :as options}
        (merge base-opts opts)

        response
        (http/put (local-url path port)
                  (-> options
                      (cond-> body (assoc :body (encode-body body content-type)))))]
    (with-parsed-body response)))

(defn patch [path port & {:as opts}]
  (let [{:keys [body content-type]
         :as options}
        (merge base-opts opts)

        response
        (http/patch (local-url path port)
                    (-> options
                        (cond-> body (assoc :body (encode-body body content-type)))))]
    (with-parsed-body response)))

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
