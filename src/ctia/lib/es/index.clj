(ns ctia.lib.es.index
  (:refer-clojure :exclude [get])
  (:require [clj-http.client :as client]
            [ctia.lib.es
             [conn :refer [default-opts safe-es-read]]
             [schemas :refer [ESConn]]]
            [schema.core :as s]))

(s/defn index-uri :- s/Str
  "make an index uri from a host and an index name"
  [uri :- s/Str
   index-name :- s/Str]
  (format "%s/%s" uri index-name))

(s/defn template-uri :- s/Str
  "make a template uri from a host and a template name"
  [uri :- s/Str
   template-name :- s/Str]
  "make a template uri from a host and a template name"
  (format "%s/_template/%s" uri template-name))

(s/defn index-exists? :- s/Bool
  "check if the supplied ES index exists"
  [{:keys [uri cm]} :- ESConn
   index-name :- s/Str]

  (safe-es-read
   (-> (client/head (index-uri uri index-name)
                    (assoc default-opts
                           :connection-manager cm))
       safe-es-read
       boolean)))


(s/defn create!
  "create an index"
  [{:keys [uri cm] :as conn} :- ESConn
   index-name :- s/Str
   settings :- s/Any]

  (safe-es-read
   (client/put (index-uri uri index-name)
               (assoc default-opts
                      :form-params settings
                      :connection-manager cm))))
(s/defn get
  "get an index"
  [{:keys [uri cm] :as conn} :- ESConn
   index-name :- s/Str]

  (safe-es-read
   (client/get (index-uri uri index-name)
               (assoc default-opts
                      :connection-manager cm))))

(s/defn delete!
  "delete indexes using a wildcard"
  [{:keys [uri cm] :as conn} :- ESConn
   index-wildcard :- s/Str]

  (safe-es-read
   (client/delete (index-uri uri index-wildcard)
                  (assoc default-opts
                         :connection-manager cm))))

(s/defn create-template!
  "create an index template, update if already exists"
  [{:keys [uri cm]} :- ESConn
   index-name :- s/Str
   index-config]

  (let [template (str index-name "*")
        opts (assoc index-config :template template)]

    (safe-es-read
     (client/put (template-uri uri index-name)
                 (merge default-opts
                        {:form-params opts
                         :connection-manager cm})))))

