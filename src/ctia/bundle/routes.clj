(ns ctia.bundle.routes
  (:refer-clojure :exclude [identity])
  (:require
   [ctia.lib.compojure.api.core :refer [GET POST context routes]]
   [ctia.bundle.core :refer [bundle-max-size
                             bundle-size
                             import-bundle
                             export-bundle
                             prep-bundle-schema]]
   [ctia.bundle.schemas :refer [BundleImportResult
                                NewBundleExport
                                BundleExportIds
                                BundleExportOptions
                                BundleExportQuery]]
   [ctia.http.routes.common :as common]
   [ctia.schemas.core :refer [APIHandlerServices NewBundle]]
   [ring.swagger.json-schema :refer [describe]]
   [ring.util.http-response :refer [ok bad-request]]
   [schema-tools.core :as st]
   [schema.core :as s]))

(def export-capabilities
  #{:list-campaigns
    :read-actor
    :read-asset
    :list-assets
    :read-asset-mapping
    :list-asset-mappings
    :read-asset-properties
    :list-asset-properties
    :read-malware
    :read-attack-pattern
    :read-judgement
    :read-sighting
    :list-sightings
    :read-identity-assertion
    :list-identity-assertions
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
    :list-notes
    :read-note
    :list-data-tables
    :list-incidents
    :read-campaign
    :list-attack-patterns
    :read-relationship
    :list-actors
    :read-investigation
    :read-incident
    :list-coas
    :read-target-record
    :list-target-records
    :read-tool
    :list-investigations
    :read-data-table
    :read-weakness
    :list-weaknesses
    :read-vulnerability
    :list-vulnerabilities
    :read-casebook
    :list-casebooks})

(s/defn bundle-routes [{{:keys [get-in-config]} :ConfigService
                        :as services} :- APIHandlerServices]
 (routes
  (context "/bundle" []
           :tags ["Bundle"]
           (let [capabilities export-capabilities]
             (GET "/export" []
                  :responses {200 {:schema NewBundleExport}}
                  :query [query BundleExportQuery]
                  :summary "Export records with their local relationships. Ids are URIs (with port if specified)."
                  :description (common/capabilities->description capabilities)
                  :capabilities capabilities
                  :auth-identity identity
                  (ok (export-bundle (:ids query) identity query services))))

           (let [capabilities export-capabilities]
             (POST "/export" []
                  :responses {200 {:schema NewBundleExport}}
                  :query [query BundleExportOptions]
                  :body [body BundleExportIds]
                  :summary "Export records with their local relationships. Ids are URIs (with port if specified)."
                  :description (common/capabilities->description capabilities)
                  :capabilities capabilities
                  :auth-identity identity
                  (ok (export-bundle (:ids body) identity query services))))

           (let [bundle-schema (prep-bundle-schema services)
                 capabilities #{:create-actor
                                :create-asset
                                :create-asset-mapping
                                :create-asset-properties
                                :create-attack-pattern
                                :create-campaign
                                :create-coa
                                :create-data-table
                                :create-feedback
                                :create-identity-assertion
                                :create-incident
                                :create-indicator
                                :create-judgement
                                :create-malware
                                :create-note
                                :create-relationship
                                :create-sighting
                                :create-tool
                                :create-vulnerability
                                :create-weakness
                                :import-bundle}]
             (POST "/import" []
                   :responses {200 {:schema BundleImportResult}}
                   :body [bundle
                          (prep-bundle-schema services)
                          {:description "a Bundle to import"}]
                   :query-params
                   [{external-key-prefixes
                     :- (describe s/Str "Comma separated list of external key prefixes")
                     nil}]
                   :summary "POST many new entities using a single HTTP call"
                   :auth-identity auth-identity
                   :description (common/capabilities->description capabilities)
                   :capabilities capabilities
                   (let [max-size (bundle-max-size get-in-config)]
                     (if (< max-size (bundle-size bundle))
                       (bad-request (str "Bundle max nb of entities: " max-size))
                       (ok (import-bundle bundle external-key-prefixes auth-identity services)))))))))
