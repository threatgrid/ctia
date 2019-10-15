(ns ctia.bundle.routes
  (:refer-clojure :exclude [identity])
  (:require
   [compojure.api.sweet :refer :all]
   [ctia.http.routes.crud :refer [wait_for->refresh]]
   [ctia.bundle
    [core :refer [bundle-max-size
                  bundle-size
                  import-bundle
                  export-bundle]]
    [schemas :refer [BundleImportResult
                     NewBundleExport]]]
   [ctia.schemas.core :refer [Bundle NewBundle]]
   [ring.util.http-response :refer :all]
   [schema.core :as s]))

(s/defschema BundleExportOptions
  {(s/optional-key :related_to) [(s/enum :source_ref :target_ref)]
   (s/optional-key :include_related_entities) s/Bool})

(s/defschema BundleExportIds
  {:ids [s/Str]})

(s/defschema BundleExportQuery
  (merge BundleExportIds
         BundleExportOptions))

(def export-capabilities
  #{:list-campaigns
    :read-actor
    :read-malware
    :read-attack-pattern
    :read-judgement
    :read-sighting
    :list-sightings
    :list-relationships
    :read-coa
    :read-indicator
    :list-judgements
    :list-tools
    :list-indicators
    :read-feedback
    :list-verdicts
    :list-feedbacks
    :list-malwares
    :list-data-tables
    :list-incidents
    :read-campaign
    :list-attack-patterns
    :read-relationship
    :list-actors
    :read-investigation
    :read-incident
    :list-coas
    :read-tool
    :list-investigations
    :read-data-table
    :read-weakness
    :list-weaknesses
    :read-vulnerability
    :list-vulnerabilities
    :read-casebook
    :list-casebooks})

(defroutes bundle-routes
  (context "/bundle" []
           :tags ["Bundle"]
           (GET "/export" []
                :return NewBundleExport
                :query [q BundleExportQuery]
                :summary "Export records with their local relationships. Ids are URIs (with port if precised)."
                :capabilities export-capabilities
                :auth-identity identity
                :identity-map identity-map
                (ok (export-bundle
                     (:ids q)
                     identity-map
                     identity
                     (select-keys q [:include_related_entities :related_to]))))

           (POST "/export" []
                :return NewBundleExport
                :query [q BundleExportOptions]
                :body [b BundleExportIds]
                :summary "Export records with their local relationships. Ids are URIs (with port if precised)."
                :capabilities export-capabilities
                :auth-identity identity
                :identity-map identity-map
                (ok (export-bundle
                     (:ids b)
                     identity-map
                     identity
                     (select-keys q [:include_related_entities :related_to]))))

           (POST "/import" []
                 :return BundleImportResult
                 :body [bundle NewBundle {:description "a Bundle to import"}]
                 :query-params
                 [{external-key-prefixes
                   :- (describe s/Str "Comma separated list of external key prefixes")
                   nil}
                  {wait_for :- (describe s/Bool "wait for created entities to be available for search") nil}]
                 :summary "POST many new entities using a single HTTP call"
                 :auth-identity auth-identity
                 :capabilities #{:create-actor
                                 :create-attack-pattern
                                 :create-campaign
                                 :create-coa
                                 :create-data-table
                                 :create-feedback
                                 :create-incident
                                 :create-indicator
                                 :create-judgement
                                 :create-malware
                                 :create-relationship
                                 :create-sighting
                                 :create-tool
                                 :create-weakness
                                 :create-vulnerability
                                 :import-bundle}
                 (let [max-size (bundle-max-size)]
                   (if (> (bundle-size bundle)
                          max-size)
                     (bad-request (str "Bundle max nb of entities: " max-size))
                     (ok (import-bundle bundle
                                        external-key-prefixes
                                        auth-identity
                                        (wait_for->refresh wait_for))))))))
