(ns ctia.entity.attack-pattern.core
  (:require [ctia.schemas.core :refer [APIHandlerServices]]
            [ctia.store :refer [query-string-search]]
            [schema.core :as s]))

(s/defn latest-attack-pattern [patterns]
  (->> patterns (sort-by :timestamp) last))

(s/defn mitre-attack-pattern
  [{{:keys [get-store]} :StoreService
    :as _services} :- APIHandlerServices
   auth-identity
   mitre-id :- s/Str]
  (some-> (get-store :attack-pattern)
          (query-string-search
           {:full-text [{:query "mitre-*"
                         :fields ["kill_chain_phases.kill_chain_name"]}
                        {:query (str "\"" mitre-id "\"")
                         :fields ["external_references.url" "external_references.external_id"]}]}
           auth-identity
           {})
          :data
          latest-attack-pattern))
