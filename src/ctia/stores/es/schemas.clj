(ns ctia.stores.es.schemas
  (:require [ctia.schemas.services :as external-svc-fns]
            [ctia.schemas.utils :as csu]
            [ductile.schemas] ;; no alias to avoid ESConnState clashes
            [schema.core :as s]
            [schema-tools.core :as st]))

(s/defschema ESConnServices
  {:ConfigService (-> external-svc-fns/ConfigServiceFns
                      (csu/select-all-keys #{:get-in-config}))
   :FeaturesService (-> external-svc-fns/FeaturesServiceFns
                      (csu/select-all-keys #{:flag-value}))})

(s/defschema ESConnState
  (st/merge
   ;; disallows services
   ductile.schemas/ESConnState
   {:services ESConnServices}
   (st/optional-keys
    {:searchable-fields (s/maybe #{s/Keyword})})))
