(ns ctia.entity.attack-pattern.core
  (:require [ctia.entity.attack-pattern.schemas :refer [StoredAttackPattern]]
            [ctia.schemas.core :refer [APIHandlerServices]]
            [ctia.store :refer [query-string-search]]
            [schema.core :as s]))

(s/defn mitre-attack-pattern :- (s/maybe StoredAttackPattern)
  [{{:keys [get-store]} :StoreService
    :as _services} :- APIHandlerServices
   identity-map
   mitre-id :- s/Str]
  (some-> (get-store :attack-pattern)
          (query-string-search
           {:full-text [{:query "external_references.external_id:\"" mitre-id "\""]}]}
           identity-map
           {:sort_by :timestamp :sort_order :desc})
          :data
          first))
