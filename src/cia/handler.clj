(ns cia.handler
  (:require [compojure.api.sweet :refer :all]
            [cia.printers :refer :all]
            [cia.schemas.actor :refer [NewActor StoredActor]]
            [cia.schemas.campaign :refer [NewCampaign StoredCampaign]]
            [cia.schemas.coa :refer [NewCOA StoredCOA]]
            [cia.schemas.common
             :refer [DispositionName DispositionNumber Time VersionInfo]]
            [cia.schemas.exploit-target
             :refer [NewExploitTarget StoredExploitTarget]]
            [cia.schemas.incident :refer [NewIncident StoredIncident]]
            [cia.schemas.indicator
             :refer [NewIndicator Sighting StoredIndicator]]
            [cia.schemas.feedback :refer [NewFeedback StoredFeedback]]
            [cia.schemas.judgement :refer [NewJudgement StoredJudgement]]
            [cia.schemas.ttp :refer [NewTTP StoredTTP]]
            [cia.schemas.vocabularies :refer [ObservableType]]
            [cia.schemas.verdict :refer [Verdict]]
            [cia.store :refer :all]
            [ring.middleware.format :refer [wrap-restful-format]]
            [ring.util.http-response :refer :all]
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

(def api-description
  "A Threat Intelligence API service

  This API provides a mechanism for making Judgements on the Disposition
  of Observables, which are then distilled into a final Verdict.  A
  Disposition is a statement regarding the malicious, or otherwise,
  nature of an Observable.

  The Judgements can be grouped into Indicators, which can be associated
  with Campaigns, Actors and TTPs.  Feedback can be given on specific
  Judgements, indicating agreement or disagreement, or clarification.

  When an Observable with a malicious Verdict is seen, it can be recorded as
  a Sighting, and the Relations that Observable had with other Observables can
  be recorded as well.

  We support a pre-defined set of Observable Types.  Each Observable Type has a
  specific form of natural identifier, its ID, which is almost always
  the default way it it represented when observed in the wild.

  * Ipv4/IPv6  -- 192.168.1.1
  * Domain/Hostname -- foo.com, www.bar.com
  * SHA256  -- the sha256 checksum of a file, or other data blob
  * MD5 -- the md5 checksum of a file, or other data blob
  * SHA1 -- the sha1 checksum of a file, or other data blob
  * URL -- A minimal form of the URL

  The Verdict is derived from all of the Judgements on that Observable which
  have not yet expired.  The highest priority Judgement becomes the
  active verdict.  If there is more than one Judgement with that
  priority, than Clean disposition has priority over all others, then
  Malicious disposition, and so on down to Unknown.")



(defapi api-handler
  {:swagger {:ui "/"
             :spec "/swagger.json"
             :data {:info {:title "Cisco Intel API "
                           :license {:name "All Rights Reserved",
                                     :url ""}
                           :contact {:name "Cisco Security Business Group -- Advanced Threat "
                                     :url "http://intel.threatgrid.cisco.com/support"
                                     :email "cisco-intel-api-support@cisco.com"}
                           :description api-description}
                    :tags [{:name "threat", :description "Threat Intelligence"}]}}}
  (context "/cia" []
    (context "/version" []
      :tags ["version"]
      (GET "/" []
        :return VersionInfo
        :summary "API version details"
        (ok {:base "/cia"
             :version "0.1"
             :beta true
             :supported_features []})))

    (context "/actor" []
      :tags ["Actor"]
      (POST "/" []
        :return StoredActor
        :body [actor NewActor {:description "a new Actor"}]
        :summary "Adds a new Actor"
        (ok (create-actor @actor-store actor)))
      (PUT "/:id" []
        :return StoredActor
        :body [actor NewActor {:description "an updated Actor"}]
        :summary "Updates an Actor"
        :path-params [id :- s/Str]
        (ok (update-actor @actor-store id actor)))
      (GET "/:id" []
        :return (s/maybe StoredActor)
        :summary "Gets an Actor by ID"
        :path-params [id :- s/Str]
        (if-let [d (read-actor @actor-store id)]
          (ok d)
          (not-found)))
      (DELETE "/:id" []
        :path-params [id :- s/Str]
        :summary "Deletes an Actor"
        (if (delete-actor @actor-store id)
          (no-content)
          (not-found))))

    (context "/campaign" []
      :tags ["Campaign"]
      (POST "/" []
        :return StoredCampaign
        :body [campaign NewCampaign {:description "a new campaign"}]
        :summary "Adds a new Campaign"
        (ok (create-campaign @campaign-store campaign)))
      (PUT "/:id" []
        :return StoredCampaign
        :body [campaign NewCampaign {:description "an updated campaign"}]
        :summary "Updates a campaign"
        :path-params [id :- s/Str]
        (ok (update-campaign @campaign-store id campaign)))
      (GET "/:id" []
        :return (s/maybe StoredCampaign)
        :summary "Gets a Campaign by ID"
        :path-params [id :- s/Str]
        (if-let [d (read-campaign @campaign-store id)]
          (ok d)
          (not-found)))
      (DELETE "/:id" []
        :path-params [id :- s/Str]
        :summary "Deletes a Campaign"
        (if (delete-campaign @campaign-store id)
          (no-content)
          (not-found))))

    (context "/exploit-target" []
      :tags ["ExploitTarget"]
      (POST "/" []
        :return StoredExploitTarget
        :body [exploit-target NewExploitTarget {:description "a new exploit target"}]
        :summary "Adds a new ExploitTarget"
        (ok (create-exploit-target @exploit-target-store exploit-target)))
      (PUT "/:id" []
        :return StoredExploitTarget
        :body [exploit-target
               NewExploitTarget
               {:description "an updated exploit target"}]
        :summary "Updates an exploit target"
        :path-params [id :- s/Str]
        (ok (update-exploit-target @exploit-target-store id exploit-target)))
      (GET "/:id" []
        :return (s/maybe StoredExploitTarget)
        :summary "Gets an ExploitTarget by ID"
        :path-params [id :- s/Str]
        (if-let [d (read-exploit-target @exploit-target-store id)]
          (ok d)
          (not-found)))
      (DELETE "/:id" []
        :path-params [id :- s/Str]
        :summary "Deletes an ExploitTarget"
        (if (delete-exploit-target @exploit-target-store id)
          (no-content)
          (not-found))))

    (context "/coa" []
      :tags ["coa"]
      (POST "/" []
        :return StoredCOA
        :body [coa NewCOA {:description "a new COA"}]
        :summary "Adds a new COA"
        (ok (create-coa @coa-store coa)))
      (PUT "/:id" []
        :return StoredCOA
        :body [coa NewCOA {:description "an updated COA"}]
        :summary "Updates a COA"
        :path-params [id :- s/Str]
        (ok (update-coa @coa-store id coa)))
      (GET "/:id" []
        :return (s/maybe StoredCOA)
        :summary "Gets a COA by ID"
        :path-params [id :- s/Str]
        (if-let [d (read-coa @coa-store id)]
          (ok d)
          (not-found)))
      (DELETE "/:id" []
        :path-params [id :- s/Str]
        :summary "Deletes a COA"
        (if (delete-coa @coa-store id)
          (no-content)
          (not-found))))

    (context "/incident" []
      :tags ["Incident"]
      (POST "/" []
        :return StoredIncident
        :body [incident NewIncident {:description "a new incident"}]
        :summary "Adds a new Incident"
        (ok (create-incident @incident-store incident)))
      (PUT "/:id" []
        :return StoredIncident
        :body [incident NewIncident {:description "an updated incident"}]
        :summary "Updates an Incident"
        :path-params [id :- s/Str]
        (ok (update-incident @incident-store id incident)))
      (GET "/:id" []
        :return (s/maybe StoredIncident)
        :summary "Gets an Incident by ID"
        :path-params [id :- s/Str]
        (if-let [d (read-incident @incident-store id)]
          (ok d)
          (not-found)))
      (DELETE "/:id" []
        :path-params [id :- s/Str]
        :summary "Deletes an Incident"
        (if (delete-incident @incident-store id)
          (no-content)
          (not-found))))

    (context "/judgement" []
      :tags ["Judgement"]
      (POST "/" []
        :return StoredJudgement
        :body [judgement NewJudgement {:description "a new Judgement"}]
        :summary "Adds a new Judgement"
        (ok (create-judgement @judgement-store judgement)))
      (POST "/:judgement-id/feedback" []
        :tags ["Feedback"]
        :return StoredFeedback
        :path-params [judgement-id :- s/Str]
        :body [feedback NewFeedback {:description "a new Feedback on a Judgement"}]
        :summary "Adds a Feedback to a Judgement"
        (ok (create-feedback @feedback-store feedback judgement-id)))
      (GET "/:judgement-id/feedback" []
        :tags ["Feedback"]
        :return [StoredFeedback]
        :path-params [judgement-id :- s/Str]
        :summary "Gets all Feedback for this Judgement."
        (ok (list-feedback @feedback-store {:judgement judgement-id})))
      (GET "/:id" []
        :return (s/maybe StoredJudgement)
        :path-params [id :- s/Str]
        :summary "Gets a Judgement by ID"
        (if-let [d (read-judgement @judgement-store id)]
          (ok d)
          (not-found)))
      (DELETE "/:id" []
        :path-params [id :- s/Str]
        :summary "Deletes a Judgement"
        (if (delete-judgement @judgement-store id)
          (no-content)
          (not-found))))

    (context "/indicator" []
      :tags ["Indicator"]
      (GET "/:id/judgements" []
        :return [StoredJudgement]
        :path-params [id :- Long]
        :summary "Gets all Judgements associated with the Indicator"
        (not-found))
      (GET "/:id/sightings" []
        :return [Sighting]
        :path-params [id :- Long]
        :summary "Gets all Sightings associated with the Indicator"
        (not-found))
      (GET "/:id/campaigns" []
        :return [StoredCampaign]
        :path-params [id :- Long]
        :summary "Gets all Campaigns associated with the Indicator"
        (not-found))
      (GET "/:id/coas" []
        :tags ["COA"]
        :return [StoredCOA]
        :path-params [id :- Long]
        :summary "Gets all TTPs associated with the Indicator"
        (not-found))
      (GET "/:id/ttps" []
        :tags ["TTP"]
        :return [StoredTTP]
        :path-params [id :- Long]
        :summary "Gets all TTPs associated with the Indicator"
        (not-found))
      (POST "/" []
        :return StoredIndicator
        :body [indicator NewIndicator {:description "a new Indicator"}]
        :summary "Adds a new Indicator"
        (ok (create-indicator @indicator-store indicator)))
      (PUT "/:id" []
        :return StoredIndicator
        :body [indicator NewIndicator {:description "an updated Indicator"}]
        :summary "Updates an Indicator"
        :path-params [id :- s/Str]
        (ok (update-indicator @indicator-store id indicator)))
      (GET "/:id" []
        :return (s/maybe StoredIndicator)
        :summary "Gets an Indicator by ID"
        :path-params [id :- s/Str]
        ;; :description "This is a little decription"
        ;; :query-params [{offset :-  Long {:summary "asdads" :default 0}}
        ;;                {limit :-  Long 0}
        ;;                {after :-  Time nil}
        ;;                {before :-  Time nil}
        ;;                {sort_by :- IndicatorSort "timestamp"}
        ;;                {sort_order :- SortOrder "desc"}
        ;;                {source :- s/Str nil}
        ;;                {observable :- ObservableType nil}]
        (if-let [d (read-indicator @indicator-store id)]
          (ok d)
          (not-found))))

    (context "/ttp" []
      :tags ["TTP"]
      (POST "/" []
        :return StoredTTP
        :body [ttp NewTTP {:description "a new TTP"}]
        :summary "Adds a new TTP"
        (ok (create-ttp @ttp-store ttp)))
      (PUT "/:id" []
        :return StoredTTP
        :body [ttp NewTTP {:description "an updated TTP"}]
        :summary "Updated a TTP"
        :path-params [id :- s/Str]
        (ok (update-ttp @ttp-store id ttp)))
      (GET "/:id" []
        :return (s/maybe StoredTTP)
        :summary "Gets a TTP by ID"
        ;;:description "This is a little description"
        ;; :query-params [{offset :-  Long 0}
        ;;                {limit :-  Long 0}
        ;;                {after :-  Time nil}
        ;;                {before :-  Time nil}
        ;;                {sort_by :- IndicatorSort "timestamp"}
        ;;                {sort_order :- SortOrder "desc"}
        ;;                {source :- s/Str nil}
        ;;                {observable :- ObservableType nil}]
        :path-params [id :- s/Str]
        (if-let [d (read-ttp @ttp-store id)]
          (ok d)
          (not-found)))
      (DELETE "/:id" []
        :path-params [id :- s/Str]
        :summary "Deletes a TTP"
        (if (delete-ttp @ttp-store id)
          (no-content)
          (not-found))))

    (context "/sightings" []
      :tags ["Sighting"])


    (GET "/:observable_type/:observable_value/judgements" []
      :query-params [{offset :-  Long 0}
                     {limit :-  Long 0}
                     {after :-  Time nil}
                     {before :-  Time nil}
                     {sort_by :- JudgementSort "timestamp"}
                     {sort_order :- SortOrder "desc"}
                     {source :- s/Str nil}
                     {disposition :- DispositionNumber nil}
                     {disposition_name :- DispositionName nil}]
      :path-params [observable_type :- ObservableType
                    observable_value :- s/Str]
      :return [StoredJudgement]
      :summary "Returns all the Judgements associated with the specified observable."
      (ok (list-judgements @judgement-store
                           {[:observable :type]  observable_type
                            [:observable :value] observable_value})))

    (GET "/:observable_type/:observable_value/indicators" []
      :query-params [{offset :-  Long 0}
                     {limit :-  Long 0}
                     {after :-  Time nil}
                     {before :-  Time nil}
                     {sort_by :- JudgementSort "timestamp"}
                     {sort_order :- SortOrder "desc"}
                     {source :- s/Str nil}]
      :path-params [observable_type :- ObservableType
                    observable_value :- s/Str]
      :return [StoredIndicator]
      :summary "Returns all the Indiators associated with the specified observable."
      (ok (list-indicators-by-observable @indicator-store
                                         @judgement-store
                                         {:type observable_type
                                          :value observable_value})))

    (GET "/:observable_type/:observable_value/sightings" []
      :query-params [{offset :-  Long 0}
                     {limit :-  Long 0}
                     {after :-  Time nil}
                     {before :-  Time nil}
                     {sort_by :- JudgementSort "timestamp"}
                     {sort_order :- SortOrder "desc"}
                     {source :- s/Str nil}]
      :path-params [observable_type :- ObservableType
                    observable_value :- s/Str]
      :return [Sighting]
      :summary "Returns all the Sightings associated with the specified observable."
      (ok (list-indicator-sightings-by-observable @indicator-store
                                                  @judgement-store
                                                  {:type observable_type
                                                   :value observable_value})))

    (GET "/:observable_type/:observable_value/verdict" []
      :tags ["Verdict"]
      :path-params [observable_type :- ObservableType
                    observable_value :- s/Str]
      :return (s/maybe Verdict)
      :summary "Returns the current Verdict associated with the specified observable."
      (ok (calculate-verdict @judgement-store {:type observable_type
                                               :value observable_value})))))

(def app
  (-> api-handler
      (wrap-restful-format)))
