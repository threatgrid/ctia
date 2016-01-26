(ns cia.test-helpers
  (:require [cheshire.core :as json]
            [clj-http.client :as http]
            [clojure.edn :as edn]
            [ring.adapter.jetty :as jetty]))

(def http-port 3000)

(defn fixture-server [app & {:keys [port]
                             :or {port http-port}}]
  (fn [f]
    (let [server (jetty/run-jetty app
                                  {:host "localhost"
                                   :port port
                                   :join? false})]
      (f)
      (.stop server))))

(defn url
  ([path]
   (url path http-port))
  ([path port]
   (format "http://localhost:%d/%s" port path)))

(defn starts-with? [^String str ^String test]
  (.startsWith str test))

(defn json? [content-type]
  (starts-with? content-type "application/json"))

(defn edn? [content-type]
  (starts-with? content-type "application/edn"))

(defn get [path & {:as options}]
  (let [{body :body
         {content-type "Content-Type"} :headers
         :as response}
        (http/get (url path)
                  (merge {:accept :edn
                          :throw-exceptions false}
                         options))]
    (assoc response
           :parsed-body
           (cond
             (edn? content-type) (edn/read-string body)
             (json? content-type) (json/parse-string body)))))
