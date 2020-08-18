(ns ctia.bulk.routes
  (:require
   [compojure.api.sweet :refer :all]
   [ctia.bulk
    [core :refer [bulk-size create-bulk fetch-bulk get-bulk-max-size]]
    [schemas :refer [Bulk BulkRefs NewBulk]]]
   [ctia.http.routes.common :as common]
   [ctia.schemas.core :refer [Reference]]
   [ring.util.http-response :refer :all]
   [schema.core :as s]))

(defroutes bulk-routes
  (POST "/" []
        :return BulkRefs
        :query-params [{wait_for :- (describe s/Bool "wait for created entities to be available for search") nil}]
        :body [bulk NewBulk {:description "a new Bulk object"}]
        :summary "POST many new entities using a single HTTP call"
        :auth-identity login
        :capabilities #{:create-actor
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
                        :create-weakness}
        (if (> (bulk-size bulk)
               (get-bulk-max-size))
          (bad-request (str "Bulk max nb of entities: " (get-bulk-max-size)))
          (common/created (create-bulk bulk
                                       {}
                                       login
                                       (common/wait_for->refresh wait_for)))))

  (GET "/" []
       :return (s/maybe Bulk)
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
       :capabilities #{:read-actor
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
                       :read-weakness}
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
         (ok (fetch-bulk entities-map auth-identity)))))
