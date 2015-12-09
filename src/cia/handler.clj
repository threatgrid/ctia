(ns cia.handler
  (:require [compojure.api.sweet :refer :all]
            [ring.util.http-response :refer :all]
            [cia.models :refer :all]
            [schema.core :as s]))

(def JudgementSort
  "A sort ordering"
  (s/enum "disposition" "timestamp" "priority" "severity" "confidence"))

(def IndicatorSort
  "A sort ordering"
  (s/enum "title" "timestamp"))

(def RelationSort
  "A sort ordering"
  (s/enum "relation" "related" "related_type" "timestamp"))

(def SortOrder
  (s/enum "asc" "desc"))

(defapi app
  (swagger-ui)
  (swagger-docs
   {:info {:title "Cisco Intel API "
           :license {:name "All Rights Reserved",
                     :url ""}
           :contact {:name "Cisco Security Business Group -- Advanced Threat "
                     :url "http://intel.threatgrid.cisco.com/support"
                     :email "cisco-intel-api-support@cisco.com"}
           :description "A Threat Intelligence API service

This API provides a mechanism for making Judgements on the Disposition
of Entities, which are then distilled into a final Verdict.  A
Disposition is a statement regarding the malicious, or otherwise,
nature of an Entity.

The Judgements can be grouped into Indicators, which can be associated
with Campaigns, Actors and TTPs.  Feedback can be given on specific
Judgements, indicating agreement or disagreement, or clarification.

When an Entity with a malicious Verdict is seen, it can be recorded as
a Sighting, and the Relations that Entity had with other Entities can
be recorded as well.

We support a pre-defined set of Entity Types.  Each Entity Type has a
specific form of natural identifier, its ID, which is almost always
the default way it it represented when observed in the wild.

 * Ipv4/IPv6  -- 192.168.1.1
 * Domain/Hostname -- foo.com, www.bar.com
 * SHA256  -- the sha256 checksum of a file, or other data blob
 * MD5 -- the md5 checksum of a file, or other data blob
 * SHA1 -- the sha1 checksum of a file, or other data blob
 * URL -- A minimal form of the URL

The Verdict is derived from all of the Judgements on that Entity which
have not yet expired.  The highest priority Judgement becomes the
active verdict.  If there is more than one Judgement with that
priority, than Clean disposition has priority over all others, then
Malicious disposition, and so on down to Unknown.

"}
    :tags [{:name "threat", :description "Threat Intelligence"}]})
  (context* "/cia" []
            :tags ["version"]
            (GET* "/version" []
                  :return VersionInfo 
                  ;;:query-params [name :- String]
                  ;;:summary "say hello"
                  (ok {:uuid 1
                       :base "/cia"
                       :version "0.1"
                       :beta true}))
            
            
            (context* "/judgements" []
                      :tags ["Judgement"]
                      (GET* "/" []
                            :query-params [{offset :-  Long 0}
                                           {limit :-  Long 0}
                                           {after :-  Time nil}
                                           {before :-  Time nil}
                                           {sort_by :- JudgementSort "timestamp"}
                                           {sort_order :- DispositionSortOrder "desc"}
                                           {origin :- s/Str nil}
                                           {entity :- EntityType nil}
                                           {priority :- Long nil}
                                           {severity :- Long nil}
                                           {confidence :- s/Str nil}
                                           {disposition :- DispositionNumber nil}
                                           {disposition_name :- DispositionName nil}]
                            :return [Judgement]
                            :summary "Search Judgements"
                            :description "Asdad"
                            (ok (get-judgements)))
                      (POST* "/" []
                             :return Judgement 
                             :body [judgement NewJudgement {:description "a new Judgement"}]
                             :summary "Adds a new Judgement"
                             (ok (add! judgement)))
                      (POST* "/:id/feedback" []
                             :tags ["Feedback"]
                             :return Feedback
                             :body [feedback NewFeedback {:description "a new Feedback on a Judgement"}]
                             :summary "Adds a Feedback to a Judgeent"
                             (ok (add! feedback)))
                      (GET* "/:id/feedback" []
                            :tags ["Feedback"]
                            :return [Feedback]
                            :path-params [id :- Long]
                            :summary "Gets all Feedback for this Judgement."
                            (not-found))
                      (GET* "/:id" []
                            :return (s/maybe Judgement)
                            :path-params [id :- Long]
                            :summary "Gets a Judgement by ID"
                            (if-let [d (get-judgement id)]
                              (ok d)
                              (not-found)))
                      (DELETE* "/:id" []
                               :path-params [id :- Long]
                               :summary "Deletes a Judgement"
                               (ok (delete! id))))

            (context* "/producers" []
                      :tags ["Producer"])

            (context* "/campaigns" []
                      :tags ["Campaign"])
            
            (context* "/indicators" []
                      :tags ["Indicator"]
                      (GET* "/:id/judgements" []
                            :return [Judgement]
                            :path-params [id :- Long]
                            :summary "Gets all Judgements associated with the Indicator"
                            (not-found))
                      (GET* "/:id/sightings" []
                            :return [Sighting]
                            :path-params [id :- Long]
                            :summary "Gets all Sightings associated with the Indicator"
                            (not-found))
                      (GET* "/:id/campaigns" []
                            :return [Campaign]
                            :path-params [id :- Long]
                            :summary "Gets all Campaigns associated with the Indicator"
                            (not-found))
                      (GET* "/:id/actors" []
                            :return [Actor]
                            :path-params [id :- Long]
                            :summary "Gets all Actors associated with the Indicator"
                            (not-found))
                      (GET* "/:id/ttps" []
                            :return [TTP]
                            :path-params [id :- Long]
                            :summary "Gets all TTPs associated with the Indicator"
                            (not-found))

                      (GET* "/" []
                            :description "This is a little decription"
                            :query-params [{offset :-  Long {:summary "asdads" :default 0}}
                                           {limit :-  Long 0}
                                           {after :-  Time nil}
                                           {before :-  Time nil}
                                           {sort_by :- IndicatorSort "timestamp"}
                                           {sort_order :- SortOrder "desc"}
                                           {origin :- s/Str nil}
                                           {entity :- EntityType nil}]))
            
            (context* "/actors" []
                      :tags ["Actor"]
                      (GET* "/" []
                            :description "This is a little decription"
                            :query-params [{offset :-  Long 0}
                                           {limit :-  Long 0}
                                           {after :-  Time nil}
                                           {before :-  Time nil}
                                           {sort_by :- IndicatorSort "timestamp"}
                                           {sort_order :- SortOrder "desc"}
                                           {origin :- s/Str nil}
                                           {entity :- EntityType nil}]))

            (context* "/ttps" []
                      :tags ["TTP"]
                      (GET* "/" []
                            :description "This is a little decription"
                            :query-params [{offset :-  Long 0}
                                           {limit :-  Long 0}
                                           {after :-  Time nil}
                                           {before :-  Time nil}
                                           {sort_by :- IndicatorSort "timestamp"}
                                           {sort_order :- SortOrder "desc"}
                                           {origin :- s/Str nil}
                                           {entity :- EntityType nil}]))

            (context* "/sightings" []
                      :tags ["Sighting"])

            
            (GET* "/:entitytype/:id/judgements" []
                  :query-params [{offset :-  Long 0}
                                 {limit :-  Long 0}
                                 {after :-  Time nil}
                                 {before :-  Time nil}
                                 {sort_by :- DispositionSort "timestamp"}
                                 {sort_order :- DispositionSortOrder "desc"}
                                 {origin :- s/Str nil}
                                 {disposition :- DispositionNumber nil}
                                 {disposition_name :- DispositionName nil}]
                  :path-params [entitytype :- EntityType
                                id :- s/Str]
                  :return [Judgement]
                  :summary "Returns all the Judgements associated with the specified entity."
                  (ok (find-judgements entitytype id)))

            (GET* "/:entitytype/:id/indicators" []
                  :query-params [{offset :-  Long 0}
                                 {limit :-  Long 0}
                                 {after :-  Time nil}
                                 {before :-  Time nil}
                                 {sort_by :- DispositionSort "timestamp"}
                                 {sort_order :- DispositionSortOrder "desc"}
                                 {origin :- s/Str nil}]
                  :path-params [entitytype :- EntityType
                                id :- s/Str]
                  :return [Indicator]
                  :summary "Returns all the Indiators associated with the specified entity."
                  (ok (find-judgements entitytype id)))

            (GET* "/:entitytype/:id/sightings" []
                  :query-params [{offset :-  Long 0}
                                 {limit :-  Long 0}
                                 {after :-  Time nil}
                                 {before :-  Time nil}
                                 {sort_by :- DispositionSort "timestamp"}
                                 {sort_order :- DispositionSortOrder "desc"}
                                 {origin :- s/Str nil}]
                  :path-params [entitytype :- EntityType
                                id :- s/Str]
                  :return [Sighting]
                  :summary "Returns all the Sightings associated with the specified entity."
                  (ok (find-judgements entitytype id)))

            (GET* "/:entitytype/:id/relations" []
                  :query-params [{offset :-  Long 0}
                                 {limit :-  Long 0}
                                 {after :-  Time nil}
                                 {before :-  Time nil}
                                 {sort_by :- RelationSort "timestamp"}
                                 {sort_order :- SortOrder "desc"}
                                 {origin :- s/Str nil}
                                 {relation :- DispositionNumber nil}]
                  :path-params [entitytype :- EntityType
                                id :- s/Str]
                  :return [Judgement]
                  :summary "Returns all the Judgements associated with the specified entity."
                  (ok (find-judgements entitytype id)))
            
            (GET* "/:entitytype/:id/verdict" []
                  :tags ["Verdict"]
                  :path-params [entitytype :- EntityType
                                id :- s/Str]
                  :return (s/maybe Verdict)
                  :summary "Returns the current Verdict associated with the specified entity."
                  (ok (current-verdict entitytype id)))))
