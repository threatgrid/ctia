(ns ctia.bulk.routes
  (:require
   [ctia.bulk.core :as core]
   [ctia.bulk.schemas :as bulk.schemas]
   [ctia.http.routes.common :as common]
   [ctia.lib.compojure.api.core :refer [GET POST DELETE PATCH PUT routes]]
   [ctia.schemas.core :refer [APIHandlerServices Reference]]
   [ring.swagger.json-schema :refer [describe]]
   [ring.util.http-response :refer [ok]]
   [schema.core :as s]))

(s/defn bulk-routes [services :- APIHandlerServices]
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
                         :create-note
                         :create-relationship
                         :create-casebook
                         :create-sighting
                         :create-identity-assertion
                         :create-target-record
                         :create-tool
                         :create-vulnerability
                         :create-weakness}]
      (routes
       (POST "/" []
         :responses {201 {:schema (bulk.schemas/BulkCreateRes services)}}
         :query-params [{wait_for :- (describe s/Bool "wait for created entities to be available for search") nil}]
         :body [bulk (describe (bulk.schemas/NewBulk services) "a new Bulk object")]
         :summary "POST many new entities using a single HTTP call"
         :auth-identity login
         :description (common/capabilities->description capabilities)
         :capabilities capabilities
         (core/validate-bulk-size! bulk services)
         (common/created (core/create-bulk bulk
                                           {}
                                           login
                                           (common/wait_for->refresh wait_for)
                                           services)))
       (PUT "/" []
         :responses {200 {:schema (s/maybe (bulk.schemas/BulkActionsRefs services))}}
         :summary "UPDATE many entities at once"
         :query-params [{wait_for :- (describe s/Bool "wait for updated entities to be available for search") nil}]
         :body [bulk (describe (bulk.schemas/BulkUpdate services) "a new Bulk Update object")]
         :description (common/capabilities->description capabilities)
         :capabilities capabilities
         :auth-identity auth-identity
         (core/validate-bulk-size! bulk services)
         (ok (core/update-bulk bulk
                               auth-identity
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
                        :read-note
                        :read-relationship
                        :read-sighting
                        :read-target-record
                        :read-tool
                        :read-vulnerability
                        :read-weakness}]
     (GET "/" []
          :responses {200 {:schema (s/maybe (bulk.schemas/Bulk services))}}
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
                         {notes               :- [Reference] []}
                         {relationships       :- [Reference] []}
                         {sightings           :- [Reference] []}
                         {target_records      :- [Reference] []}
                         {tools               :- [Reference] []}
                         {vulnerabilities     :- [Reference] []}
                         {weaknesses          :- [Reference] []}]
          :description (common/capabilities->description capabilities)
          :capabilities capabilities
          :auth-identity auth-identity
          (let [bulk {:actors              actors
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
                      :notes               notes
                      :relationships       relationships
                      :sightings           sightings
                      :target_records      target_records
                      :tools               tools
                      :vulnerabilities     vulnerabilities
                      :weaknesses          weaknesses}]
            (core/validate-bulk-size! bulk services)
            (ok (core/fetch-bulk bulk auth-identity services)))))
    (let [capabilities (bulk.schemas/bulk-patch-capabilities services)]
      (PATCH "/" []
        :responses {200 {:schema (s/maybe (bulk.schemas/BulkActionsRefs services))}}
        :summary "PATCH many entities at once"
        :query-params [{wait_for :- (describe s/Bool "wait for patched entities to be available for search") nil}]
        :body [bulk (describe (bulk.schemas/BulkPatch services) "a new Bulk Patch object")]
        :description (common/capabilities->description capabilities)
        :capabilities capabilities
        :auth-identity auth-identity
        (core/validate-bulk-size! bulk services)
        (ok (core/patch-bulk bulk
                             {} ;; transient ids only supported via PATCH bundle/import
                             auth-identity
                             (common/wait_for->refresh wait_for)
                             services))))
    (let [capabilities #{:delete-actor
                         :delete-asset
                         :delete-asset-mapping
                         :delete-asset-properties
                         :delete-attack-pattern
                         :delete-campaign
                         :delete-coa
                         :delete-data-table
                         :delete-feedback
                         :delete-incident
                         :delete-investigation
                         :delete-indicator
                         :delete-judgement
                         :delete-malware
                         :delete-note
                         :delete-relationship
                         :delete-casebook
                         :delete-sighting
                         :delete-identity-assertion
                         :delete-target-record
                         :delete-tool
                         :delete-vulnerability
                         :delete-weakness}]
     (DELETE "/" []
          :responses {200 {:schema (s/maybe (bulk.schemas/BulkActionsRefs services))}}
          :summary "DELETE many entities at once"
          :query-params [{wait_for :- (describe s/Bool "wait for deleted entities to not be available anymore for search") nil}]
          :body [bulk (describe (bulk.schemas/BulkRefs services) "a new Bulk Delete object")]
          :description (common/capabilities->description capabilities)
          :capabilities capabilities
          :auth-identity auth-identity
          (core/validate-bulk-size! bulk services)
          (ok (core/delete-bulk bulk
                                auth-identity
                                (common/wait_for->refresh wait_for)
                                services))))))
