(ns ctia.bundle.routes
  (:refer-clojure :exclude [identity])
  (:require
   [ctia.lib.compojure.api.core :refer [GET POST context routes]]
   [ctia.bundle.core :refer [bundle-max-size
                             bundle-size
                             import-bundle
                             export-bundle
                             prep-bundle-schema]]
   [ctia.bundle.schemas :refer [AssetPropertiesMergeStrategy
                                BundleImportResult
                                NewBundleExport
                                BundleExportIds
                                BundleExportOptions
                                BundleExportQuery
                                IncidentTacticsTechniquesMergeStrategy]]
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
                          (describe (st/optional-keys-schema bundle-schema)
                                    "a Bundle to import, partial entities allowed for existing entities")]
                   :query-params
                   [{external-key-prefixes
                     :- (describe s/Str "Comma separated list of external key prefixes")
                     nil}
                    {patch-existing :- (describe s/Bool
                                         (str "If true, existing entities will be patched with result=updated. Otherwise, existing entities will be "
                                              "ignored with result-existing."))
                     false}
                    {asset_properties-merge-strategy :-
                     (describe AssetPropertiesMergeStrategy
                               (str "Only relevant if patch-existing=true.\n\n" 
                                    "If ignore-existing, then asset properties will be patched to their new "
                                    "values as they appear in the request bundle.\n\n"
                                    "If merge-overriding-previous, then existing asset properties "
                                    "will be retrieved and combined with the asset properties in the request bundle "
                                    "as if by concatenating existing and new properties together in a single list, "
                                    "removing properties to the left of a property with the same name, "
                                    "then sorting the list lexicographically by name before using this list to patch the existing entity."
                                    "\n\n"
                                    " Defaults to ignore-existing"))
                     :ignore-existing}
                    {incident-tactics-techniques-merge-strategy :-
                     (describe IncidentTacticsTechniquesMergeStrategy
                               (str "Only relevant if patch-existing=true.\n\n" 
                                    "If ignore-existing, then tactics and techniques on incidents will be patched to their new "
                                    "values as they appear in the request bundle.\n\n"
                                    "If merge-previous, then, for each incident, existing tactics and techniques "
                                    "will each be retrieved and combined with those provided in the request bundle "
                                    "as if by concatenating existing and new values together in a single list, "
                                    "removing duplicates, then sorting lexicographically."
                                    "\n\n"
                                    " Defaults to ignore-existing"))
                     :ignore-existing}]
                   :summary "POST many new and partial entities using a single HTTP call"
                   :auth-identity auth-identity
                   :description (common/capabilities->description capabilities)
                   :capabilities capabilities
                   (let [max-size (bundle-max-size get-in-config)]
                     (if (< max-size (bundle-size bundle))
                       (bad-request (str "Bundle max nb of entities: " max-size))
                       (ok (import-bundle bundle external-key-prefixes auth-identity services
                                          {:patch-existing patch-existing
                                           :asset_properties-merge-strategy asset_properties-merge-strategy
                                           :incident-tactics-techniques-merge-strategy incident-tactics-techniques-merge-strategy})))))))))
