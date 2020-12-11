(ns ctia.bundle.routes
  (:refer-clojure :exclude [identity])
  (:require
   [clojure.string :as str]
   [compojure.api.core :refer [GET POST context routes]]
   [ctia.bundle.core :refer [bundle-max-size
                             bundle-size
                             import-bundle
                             export-bundle]]
   [ctia.bundle.schemas :refer [BundleImportResult
                                NewBundleExport
                                BundleExportIds
                                BundleExportOptions
                                BundleExportQuery]]
   [ctia.entity.entities :as entities]
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

(defn- keyword->str [key] (str (if (namespace key) "/" "") (name key)))

(defn- entity->bundle-keys
  "For given entity key returns corresponding keys that may be present in Bundle schema.
  e.g. :asset => [:assets :asset_refs]"
  [entity-key]
  (let [{:keys [entity plural]} (get (entities/all-entities) entity-key)
        kw->snake-case-str      (fn [kw] (-> kw keyword->str (str/replace #"-" "_")))]
    [(-> plural kw->snake-case-str keyword)
     (-> entity kw->snake-case-str (str "_refs") keyword)]))

(s/defn prep-bundle-schema :- s/Schema
  [{{:keys [enabled?]} :FeaturesService} :- APIHandlerServices]
  (let [to-remove (->> (entities/all-entities)
                       keys
                       (filter (comp not enabled?))
                       (mapcat entity->bundle-keys))]
    (apply st/dissoc NewBundle to-remove)))

(s/defn bundle-routes [{{:keys [get-in-config]} :ConfigService
                        :as services} :- APIHandlerServices]
 (routes
  (context "/bundle" []
           :tags ["Bundle"]
           (let [capabilities export-capabilities]
             (GET "/export" []
                  :return NewBundleExport
                  :query [q BundleExportQuery]
                  :summary "Export records with their local relationships. Ids are URIs (with port if specified)."
                  :description (common/capabilities->description capabilities)
                  :capabilities capabilities
                  :auth-identity identity
                  :identity-map identity-map
                  (ok (export-bundle
                       (:ids q)
                       identity-map
                       identity
                       q
                       services))))

           (let [capabilities export-capabilities]
             (POST "/export" []
                  :return NewBundleExport
                  :query [q BundleExportOptions]
                  :body [b BundleExportIds]
                  :summary "Export records with their local relationships. Ids are URIs (with port if specified)."
                  :description (common/capabilities->description capabilities)
                  :capabilities capabilities
                  :auth-identity identity
                  :identity-map identity-map
                  (ok (export-bundle
                       (:ids b)
                       identity-map
                       identity
                       q
                       services))))

           (let [capabilities #{:create-actor
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
                                :create-relationship
                                :create-sighting
                                :create-tool
                                :create-vulnerability
                                :create-weakness
                                :import-bundle}]
             (POST "/import" []
                   :return BundleImportResult
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
