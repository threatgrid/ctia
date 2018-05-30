(ns ctia.bundle.routes
  (:refer-clojure :exclude [identity])
  (:require
   [compojure.api.sweet :refer :all]
   [ctia.bundle
    [core :refer [bundle-max-size
                  bundle-size
                  import-bundle
                  export-bundle]]
    [schemas :refer [BundleImportResult]]]
   [ctia.schemas.core :refer [Bundle NewBundle]]
   [ring.util.http-response :refer :all]
   [schema.core :as s]))

(s/defschema BundleExportQuery
  {:ids [s/Str]
   (s/optional-key :include_related_entities) s/Bool})

(defroutes bundle-routes
  (context "/bundle" []
           :tags ["Bundle"]
           (GET "/export" []
                :return Bundle
                :header-params [{Authorization :- (s/maybe s/Str) nil}]
                :query [q BundleExportQuery]
                :summary "Export a record with its local relationships"
                :capabilities #{:read-actor
                                :read-attack-pattern
                                :read-campaign
                                :read-coa
                                :read-exploit-target
                                :read-feedback
                                :read-incident
                                :read-indicator
                                :list-indicators
                                :read-judgement
                                :list-judgements
                                :read-malware
                                :read-relationship
                                :list-relationships
                                :read-sighting
                                :list-sightings
                                :read-tool
                                :read-verdict}
                :auth-identity identity
                :identity-map identity-map
                (ok (export-bundle
                     (:ids q)
                     identity-map
                     identity
                     (select-keys q [:include_related_entities]))))

           (POST "/import" []
                 :return BundleImportResult
                 :body [bundle NewBundle {:description "a Bundle to import"}]
                 :query-params
                 [{external-key-prefixes
                   :- (describe s/Str "Comma separated list of external key prefixes")
                   nil}]
                 :header-params [{Authorization :- (s/maybe s/Str) nil}]
                 :summary "POST many new entities using a single HTTP call"
                 :auth-identity auth-identity
                 :capabilities #{:create-actor
                                 :create-attack-pattern
                                 :create-campaign
                                 :create-coa
                                 :create-data-table
                                 :create-exploit-target
                                 :create-feedback
                                 :create-incident
                                 :create-indicator
                                 :create-judgement
                                 :create-malware
                                 :create-relationship
                                 :create-sighting
                                 :create-tool
                                 :import-bundle}
                 (let [max-size (bundle-max-size)]
                   (if (> (bundle-size bundle)
                          max-size)
                     (bad-request (str "Bundle max nb of entities: " max-size))
                     (ok (import-bundle bundle external-key-prefixes auth-identity)))))))
