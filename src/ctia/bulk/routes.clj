(ns ctia.bulk.routes
  (:require
   [ctia.bulk.core :refer [bulk-size create-bulk fetch-bulk delete-bulk get-bulk-max-size]]
   [ctia.bulk.schemas :refer [Bulk BulkRefs NewBulk]]
   [ctia.http.routes.common :as common]
   [ctia.lib.compojure.api.core :refer [GET POST DELETE routes]]
   [ctia.schemas.core :refer [APIHandlerServices Reference]]
   [ring.swagger.json-schema :refer [describe]]
   [ring.util.http-response :refer [bad-request ok]]
   [schema.core :as s]))

(s/defn bulk-routes [{{:keys [get-in-config]} :ConfigService :as services} :- APIHandlerServices]
  (routes
    (let [capabilities #{:create-actor
                         :create-asset
                         :create-asset-mapping
                         :create-asset-properties
                         :create-attack-pattern
                         :create-campaign
                         :create-coa
                         :create-data-table
                         :create-feedback
                         :create-incident
                         :create-investigation
                         :create-indicator
                         :create-judgement
                         :create-malware
                         :create-relationship
                         :create-casebook
                         :create-sighting
                         :create-identity-assertion
                         :create-target-record
                         :create-tool
                         :create-vulnerability
                         :create-weakness}]
      (POST "/" []
            :return (BulkRefs services)
            :query-params [{wait_for :- (describe s/Bool "wait for created entities to be available for search") nil}]
            :body [bulk (NewBulk services) {:description "a new Bulk object"}]
            :summary "POST many new entities using a single HTTP call"
            :auth-identity login
            :description (common/capabilities->description capabilities)
            :capabilities capabilities
            (if (> (bulk-size bulk)
                   (get-bulk-max-size get-in-config))
              (bad-request (str "Bulk max nb of entities: " (get-bulk-max-size get-in-config)))
              (common/created (create-bulk bulk
                                           {}
                                           login
                                           (common/wait_for->refresh wait_for)
                                           services)))))

   (let [capabilities #{:read-actor
                        :read-asset
                        :read-asset-mapping
                        :read-asset-properties
                        :read-attack-pattern
                        :read-campaign
                        :read-casebook
                        :read-coa
                        :read-data-table
                        :read-feedback
                        :read-identity-assertion
                        :read-incident
                        :read-indicator
                        :read-investigation
                        :read-judgement
                        :read-malware
                        :read-relationship
                        :read-sighting
                        :read-target-record
                        :read-tool
                        :read-vulnerability
                        :read-weakness}]
     (GET "/" []
          :return (s/maybe (Bulk services))
          :summary "GET many entities at once"
          :query-params [{actors              :- [Reference] []}
                         {asset_mappings      :- [Reference] []}
                         {assets              :- [Reference] []}
                         {asset_properties    :- [Reference] []}
                         {attack_patterns     :- [Reference] []}
                         {campaigns           :- [Reference] []}
                         {casebooks           :- [Reference] []}
                         {coas                :- [Reference] []}
                         {data_tables         :- [Reference] []}
                         {feedbacks           :- [Reference] []}
                         {identity_assertions :- [Reference] []}
                         {incidents           :- [Reference] []}
                         {indicators          :- [Reference] []}
                         {investigations      :- [Reference] []}
                         {judgements          :- [Reference] []}
                         {malwares            :- [Reference] []}
                         {relationships       :- [Reference] []}
                         {sightings           :- [Reference] []}
                         {target_records      :- [Reference] []}
                         {tools               :- [Reference] []}
                         {vulnerabilities     :- [Reference] []}
                         {weaknesses          :- [Reference] []}]
          :description (common/capabilities->description capabilities)
          :capabilities capabilities
          :auth-identity auth-identity
          (let [entities-map {:actors              actors
                              :asset_mappings      asset_mappings
                              :assets              assets
                              :asset_properties    asset_properties
                              :attack_patterns     attack_patterns
                              :campaigns           campaigns
                              :casebooks           casebooks
                              :coas                coas
                              :data_tables         data_tables
                              :feedbacks           feedbacks
                              :identity_assertions identity_assertions
                              :incidents           incidents
                              :indicators          indicators
                              :investigations      investigations
                              :judgements          judgements
                              :malwares            malwares
                              :relationships       relationships
                              :sightings           sightings
                              :target_records      target_records
                              :tools               tools
                              :vulnerabilities     vulnerabilities
                              :weaknesses          weaknesses}]
            (ok (fetch-bulk entities-map auth-identity services)))))
     ))
