(ns ctia.lib.es.index
  (:require
   [ctia.lib.es.conn :refer [ESConn default-opts safe-es-read]]
   [clj-http.client :as client]
   [schema.core :as s]))

(s/defschema ESSlicing
  {:strategy s/Keyword
   :granularity s/Keyword})

(s/defschema ESConnState
  {:index s/Str
   :props {s/Any s/Any}
   :config {s/Any s/Any}
   :conn ESConn
   (s/optional-key :slicing) ESSlicing})

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

  (-> (client/head (index-uri uri index-name)
                   (assoc default-opts
                          :connection-manager cm))
      safe-es-read
      boolean))

(s/defn delete!
  "delete indexes using a wildcard"
  [{:keys [uri cm] :as conn} :- ESConn
   index-wildcard :- s/Str]

  (client/delete (index-uri uri index-wildcard)
                 (assoc default-opts
                        :connection-manager cm)))

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
