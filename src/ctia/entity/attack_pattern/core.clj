(ns ctia.entity.attack-pattern.core
  (:require [clojure.tools.logging :as log]
            [ctia.entity.attack-pattern.schemas :refer [StoredAttackPattern]]
            [ctia.schemas.core :refer [APIHandlerServices]]
            [ctia.store :refer [query-string-search]]
            [schema.core :as s]))

(s/defn latest-attack-pattern :- (s/maybe StoredAttackPattern)
  [[p1 & remaining :as _attack-patterns] :- [StoredAttackPattern]]
  (when (seq remaining)
    (log/warnf "Multiple MITRE attack-patterns found for external_id: %s" (get-in p1 [:external_references 0 :external_id])))
  p1)

(s/defn mitre-attack-pattern :- (s/maybe StoredAttackPattern)
  [{{:keys [get-store]} :StoreService
    :as _services} :- APIHandlerServices
   identity-map
   mitre-id :- s/Str]
  (some-> (get-store :attack-pattern)
          (query-string-search
           {:full-text [{:query (str "external_references.external_id:\"" mitre-id "\"")}]}
           identity-map
           {:sort_by :timestamp :sort_order :desc})
          :data
          latest-attack-pattern))
