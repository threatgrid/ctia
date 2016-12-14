(ns ctia.lib.es.index
  (:require [clj-http
             [client :as client]
             [conn-mgr :refer [make-reusable-conn-manager]]]
            [schema.core :as s])
  (:import [org.apache.http.impl.conn PoolingClientConnectionManager
            PoolingHttpClientConnectionManager]))

(def default-cm-options {:timeout 30000
                         :threads 100
                         :default-per-route 100})

(defn make-connection-manager []
  (make-reusable-conn-manager default-cm-options))

(s/defschema ESConn
  {:cm (s/either PoolingClientConnectionManager
                 PoolingHttpClientConnectionManager)
   :uri s/Str})

(s/defschema ESSlicing
  {:strategy s/Keyword
   :granularity s/Keyword})

(s/defschema ESConnState
  {:index s/Str
   :props {s/Any s/Any}
   :config {s/Any s/Any}
   :conn ESConn
   (s/optional-key :slicing) ESSlicing})

(defn connect
  "instantiate an ES conn from props"
  [{:keys [transport host port clustername]
    :or {transport :http}}]

  {:cm (make-connection-manager)
   :uri (format "http://%s:%s" host port)})

(s/defn index-uri :- s/Str
  [uri :- s/Str
   index-name :- s/Str]
  "make an index uri from a host and an index name"
  (format "%s/%s" uri index-name))

(s/defn template-uri :- s/Str
  [uri :- s/Str
   template-name :- s/Str]
  "make a template uri from a host and a template name"
  (format "%s/_template/%s" uri template-name))

(s/defn index-exists? :- s/Bool
  "check if the supplied ES index exists"
  [{:keys [uri cm]} :- ESConn
   index-name :- s/Str]

  (->> (client/head (index-uri uri index-name)
                    {:throw-exceptions false
                     :connection-manager cm})

       :status
       (= 200)))

(s/defn delete!
  "delete an index, abort if non existant"
  [{:keys [uri cm] :as conn} :- ESConn
   index-name :- s/Str]

  (when (index-exists? conn index-name)
    (:body (client/delete (index-uri uri index-name)
                          {:connection-manager cm}))))

(s/defn create-template!
  "create an index template, update if already exists"
  [{:keys [uri cm]} :- ESConn
   index-name :- s/Str
   index-config]

  (let [template (str index-name "*")
        opts (assoc index-config :template template)]

    (:body (client/put (template-uri uri index-name)
                       {:form-params opts
                        :as :json
                        :content-type :json
                        :connection-manager cm}))))
