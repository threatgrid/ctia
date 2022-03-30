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
           {:full-text [{:query
                         (format "kill_chain_phases.kill_chain_name:\"mitre-attack\" AND (external_references.url:\"%s\" OR external_references.external_id:\"%s\")"
                                 mitre-id mitre-id)}]}
           auth-identity
           {})
          :data
          latest-attack-pattern))
