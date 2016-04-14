(ns ctia.http.handler
  (:require [ctia.schemas.actor :refer [NewActor StoredActor realize-actor]]
            [ctia.schemas.campaign :refer [NewCampaign StoredCampaign realize-campaign]]
            [ctia.schemas.coa :refer [NewCOA StoredCOA realize-coa]]
            [ctia.schemas.common
             :refer [DispositionName DispositionNumber Time VersionInfo]]
            [ctia.schemas.exploit-target
             :refer [NewExploitTarget StoredExploitTarget realize-exploit-target]]
            [ctia.schemas.incident :refer [NewIncident StoredIncident realize-incident]]
            [ctia.schemas.indicator
             :refer [NewIndicator StoredIndicator generalize-indicator realize-indicator]]
            [ctia.schemas.feedback :refer [NewFeedback StoredFeedback realize-feedback]]
            [ctia.schemas.judgement :refer [NewJudgement StoredJudgement realize-judgement]]
            [ctia.schemas.relationships :as rel]
            [ctia.schemas.sighting :refer [NewSighting StoredSighting realize-sighting]]
            [ctia.schemas.ttp :refer [NewTTP StoredTTP realize-ttp]]
            [ctia.schemas.vocabularies :refer [ObservableType]]
            [ctia.schemas.verdict :refer [Verdict]]
            [ctia.events.schemas :refer [ModelEventBase]]
            [ctia.events :refer [recent-events]]
            [ctia.store :refer :all]
            [ctia.flows.crud :as flows]
            [ctia.flows.hooks :as hooks]
            [compojure.api.sweet :refer :all]
            [ring.middleware.format :refer [wrap-restful-format]]
            [ring.middleware.params :as params]
            [ring.util.http-response :refer :all]
            [schema.core :as s]
            [ctia.schemas.relationships :as rel]
            [ctia.http.middleware.auth :as auth]
            [ctia.http.routes.documentation :refer [documentation-routes]]))

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
  Malicious disposition, and so on down to Unknown.

  <a href='/doc/data_structures.md'>Data structures documentation</a>")



(defapi api-handler
  {:swagger {:ui "/"
             :spec "/swagger.json"
             :data {:info {:title "Cisco Threat Intel API "
                           :license {:name "All Rights Reserved",
                                     :url ""}
                           :contact {:name "Cisco Security Business Group -- Advanced Threat "
                                     :url "http://intel.threatgrid.cisco.com/support"
                                     :email "cisco-intel-api-support@cisco.com"}
                           :description api-description}
                    :tags [{:name "threat", :description "Threat Intelligence"}]}}}

  documentation-routes

  (context "/ctia" []
    (context "/version" []
      :tags ["version"]
      (GET "/" []
        :return VersionInfo
        :summary "API version details"
        (ok {:base "/ctia"
             :version "0.1"
             :beta true
             :supported_features []})))

    (context "/actor" []
      :tags ["Actor"]
      (POST "/" []
        :return StoredActor
        :body [actor NewActor {:description "a new Actor"}]
        :header-params [api_key :- s/Str]
        :summary "Adds a new Actor"
        :capabilities #{:create-actor :admin}
        :login login
        (ok (flows/create-flow :realize-fn realize-actor
                               :store-fn #(create-actor @actor-store %)
                               :object-type :actor
                               :login login
                               :object actor)))
      (PUT "/:id" []
        :return StoredActor
        :body [actor NewActor {:description "an updated Actor"}]
        :header-params [api_key :- (s/maybe s/Str)]
        :summary "Updates an Actor"
        :path-params [id :- s/Str]
        :capabilities #{:create-actor :admin}
        :login login
        (ok (flows/update-flow :get-fn #(read-actor @actor-store %)
                               :realize-fn realize-actor
                               :update-fn #(update-actor @actor-store (:id %) %)
                               :object-type :actor
                               :id id
                               :login login
                               :object actor)))
      (GET "/:id" []
        :return (s/maybe StoredActor)
        :summary "Gets an Actor by ID"
        :path-params [id :- s/Str]
        :header-params [api_key :- (s/maybe s/Str)]
        :capabilities #{:read-actor :admin}
        (if-let [d (read-actor @actor-store id)]
          (ok d)
          (not-found)))
      (DELETE "/:id" []
        :no-doc true
        :path-params [id :- s/Str]
        :summary "Deletes an Actor"
        :header-params [api_key :- (s/maybe s/Str)]
        :capabilities #{:delete-actor :admin}
        (if (flows/delete-flow :get-fn #(read-actor @actor-store %)
                               :delete-fn #(delete-actor @actor-store %)
                               :object-type :actor
                               :id id)
          (no-content)
          (not-found))))

    (context "/campaign" []
      :tags ["Campaign"]
      (POST "/" []
        :return StoredCampaign
        :body [campaign NewCampaign {:description "a new campaign"}]
        :summary "Adds a new Campaign"
        :header-params [api_key :- (s/maybe s/Str)]
        :capabilities #{:create-campaign :admin}
        :login login
        (ok (flows/create-flow :realize-fn realize-campaign
                               :store-fn #(create-campaign @campaign-store %)
                               :object-type :campaign
                               :login login
                               :object campaign)))
      (PUT "/:id" []
        :return StoredCampaign
        :body [campaign NewCampaign {:description "an updated campaign"}]
        :summary "Updates a Campaign"
        :path-params [id :- s/Str]
        :header-params [api_key :- (s/maybe s/Str)]
        :capabilities #{:create-campaign :admin}
        :login login
        (ok (flows/update-flow :get-fn #(read-campaign @campaign-store %)
                               :realize-fn realize-campaign
                               :update-fn #(update-campaign @campaign-store (:id %) %)
                               :object-type :campaign
                               :id id
                               :login login
                               :object campaign)))
      (GET "/:id" []
        :return (s/maybe StoredCampaign)
        :summary "Gets a Campaign by ID"
        :path-params [id :- s/Str]
        :header-params [api_key :- (s/maybe s/Str)]
        :capabilities #{:read-campaign :admin}
        (if-let [d (read-campaign @campaign-store id)]
          (ok d)
          (not-found)))
      (DELETE "/:id" []
        :no-doc true
        :path-params [id :- s/Str]
        :summary "Deletes a Campaign"
        :header-params [api_key :- (s/maybe s/Str)]
        :capabilities #{:delete-campaign :admin}
        (if (flows/delete-flow :get-fn #(read-campaign @campaign-store %)
                               :delete-fn #(delete-campaign @campaign-store %)
                               :object-type :campaign
                               :id id)
          (no-content)
          (not-found))))

    (context "/exploit-target" []
      :tags ["ExploitTarget"]
      (POST "/" []
        :return StoredExploitTarget
        :body [exploit-target NewExploitTarget {:description "a new ExploitTarget"}]
        :summary "Adds a new ExploitTarget"
        :header-params [api_key :- (s/maybe s/Str)]
        :capabilities #{:create-exploit-target :admin}
        :login login
        (ok (flows/create-flow :realize-fn realize-exploit-target
                               :store-fn #(create-exploit-target @exploit-target-store %)
                               :object-type :exploit-target
                               :login login
                               :object exploit-target)))
      (PUT "/:id" []
        :return StoredExploitTarget
        :body [exploit-target
               NewExploitTarget
               {:description "an updated ExploitTarget"}]
        :summary "Updates an ExploitTarget"
        :path-params [id :- s/Str]
        :header-params [api_key :- (s/maybe s/Str)]
        :capabilities #{:create-exploit-target :admin}
        :login login
        (ok (flows/update-flow :get-fn #(read-exploit-target @exploit-target-store %)
                               :realize-fn realize-exploit-target
                               :update-fn #(update-exploit-target @exploit-target-store (:id %) %)
                               :object-type :exploit-target
                               :id id
                               :login login
                               :object exploit-target)))
      (GET "/:id" []
        :return (s/maybe StoredExploitTarget)
        :summary "Gets an ExploitTarget by ID"
        :path-params [id :- s/Str]
        :header-params [api_key :- (s/maybe s/Str)]
        :capabilities #{:read-exploit-target :admin}
        (if-let [d (read-exploit-target @exploit-target-store id)]
          (ok d)
          (not-found)))
      (DELETE "/:id" []
        :no-doc true
        :path-params [id :- s/Str]
        :summary "Deletes an ExploitTarget"
        :header-params [api_key :- (s/maybe s/Str)]
        :capabilities #{:delete-exploit-target :admin}
        (if (flows/delete-flow :get-fn #(read-exploit-target @exploit-target-store %)
                               :delete-fn #(delete-exploit-target @exploit-target-store %)
                               :object-type :exploit-target
                               :id id)
          (no-content)
          (not-found))))

    (context "/coa" []
      :tags ["COA"]
      (POST "/" []
        :return StoredCOA
        :body [coa NewCOA {:description "a new COA"}]
        :summary "Adds a new COA"
        :header-params [api_key :- (s/maybe s/Str)]
        :capabilities #{:create-coa :admin}
        :login login
        (ok (flows/create-flow :realize-fn realize-coa
                               :store-fn #(create-coa @coa-store %)
                               :object-type :coa
                               :login login
                               :object coa)))
      (PUT "/:id" []
        :return StoredCOA
        :body [coa NewCOA {:description "an updated COA"}]
        :summary "Updates a COA"
        :path-params [id :- s/Str]
        :header-params [api_key :- (s/maybe s/Str)]
        :capabilities #{:create-coa :admin}
        :login login
        (ok (flows/update-flow :get-fn #(read-coa @coa-store %)
                               :realize-fn realize-coa
                               :update-fn #(update-coa @coa-store (:id %) %)
                               :object-type :coa
                               :id id
                               :login login
                               :object coa)))
      (GET "/:id" []
        :return (s/maybe StoredCOA)
        :summary "Gets a COA by ID"
        :path-params [id :- s/Str]
        :header-params [api_key :- (s/maybe s/Str)]
        :capabilities #{:read-coa :admin}
        (if-let [d (read-coa @coa-store id)]
          (ok d)
          (not-found)))
      (DELETE "/:id" []
        :no-doc true
        :path-params [id :- s/Str]
        :summary "Deletes a COA"
        :header-params [api_key :- (s/maybe s/Str)]
        :capabilities #{:delete-coa :admin}
        (if (flows/delete-flow :get-fn #(read-coa @coa-store %)
                               :delete-fn #(delete-coa @coa-store %)
                               :object-type :coa
                               :id id)
          (no-content)
          (not-found))))

    (context "/incident" []
      :tags ["Incident"]
      (POST "/" []
        :return StoredIncident
        :body [incident NewIncident {:description "a new incident"}]
        :summary "Adds a new Incident"
        :header-params [api_key :- (s/maybe s/Str)]
        :capabilities #{:create-incident :admin}
        :login login
        (ok (flows/create-flow :realize-fn realize-incident
                               :store-fn #(create-incident @incident-store %)
                               :object-type :incident
                               :login login
                               :object incident)))
      (PUT "/:id" []
        :return StoredIncident
        :body [incident NewIncident {:description "an updated incident"}]
        :summary "Updates an Incident"
        :path-params [id :- s/Str]
        :header-params [api_key :- (s/maybe s/Str)]
        :capabilities #{:create-incident :admin}
        :login login
        (ok (flows/update-flow :get-fn #(read-incident @incident-store %)
                               :realize-fn realize-incident
                               :update-fn #(update-incident @incident-store (:id %) %)
                               :object-type :incident
                               :id id
                               :login login
                               :object incident)))
      (GET "/:id" []
        :return (s/maybe StoredIncident)
        :summary "Gets an Incident by ID"
        :path-params [id :- s/Str]
        :header-params [api_key :- (s/maybe s/Str)]
        :capabilities :read-incident
        (if-let [d (read-incident @incident-store id)]
          (ok d)
          (not-found)))
      (DELETE "/:id" []
        :no-doc true
        :path-params [id :- s/Str]
        :summary "Deletes an Incident"
        :header-params [api_key :- (s/maybe s/Str)]
        :capabilities #{:delete-incident :admin}
        (if (flows/delete-flow :get-fn #(read-incident @incident-store %)
                               :delete-fn #(delete-incident @incident-store %)
                               :object-type :incident
                               :id id)
          (no-content)
          (not-found))))

    (context "/judgement" []
      :tags ["Judgement"]
      (POST "/" []
        :return StoredJudgement
        :body [judgement NewJudgement {:description "a new Judgement"}]
        :header-params [api_key :- (s/maybe s/Str)]
        :summary "Adds a new Judgement"
        :capabilities #{:create-judgement :admin}
        :login login
        (ok (flows/create-flow :realize-fn realize-judgement
                               :store-fn #(create-judgement @judgement-store %)
                               :object-type :judgement
                               :login login
                               :object judgement)))
      (POST "/:judgement-id/feedback" []
        :tags ["Feedback"]
        :return StoredFeedback
        :path-params [judgement-id :- s/Str]
        :body [feedback NewFeedback {:description "a new Feedback on a Judgement"}]
        :header-params [api_key :- (s/maybe s/Str)]
        :summary "Adds a Feedback to a Judgement"
        :capabilities #{:create-feedback :admin}
        :login login
        (ok (flows/create-flow :realize-fn #(realize-feedback %1 %2 %3 judgement-id)
                               :store-fn #(create-feedback @feedback-store %)
                               :object-type :feedback
                               :login login
                               :object feedback)))
      (GET "/:judgement-id/feedback" []
        :tags ["Feedback"]
        :return (s/maybe [StoredFeedback])
        :path-params [judgement-id :- s/Str]
        :header-params [api_key :- (s/maybe s/Str)]
        :capabilities #{:read-feedback :admin}
        :summary "Gets all Feedback for this Judgement."
        (if-let [d (list-feedback @feedback-store {:judgement judgement-id})]
          (ok d)
          (not-found)))
      (POST "/:judgement-id/indicator" []
        :return (s/maybe rel/RelatedIndicator)
        :path-params [judgement-id :- s/Str]
        :body [indicator-relationship rel/RelatedIndicator]
        :header-params [api_key :- s/Str]
        :summary "Adds an Indicator to a Judgement"
        :capabilities #{:create-judgement-indicator}
        (if-let [d (add-indicator-to-judgement @judgement-store
                                               judgement-id
                                               indicator-relationship)]
          (ok d)
          (not-found)))
      (GET "/:id" []
        :return (s/maybe StoredJudgement)
        :path-params [id :- s/Str]
        :header-params [api_key :- (s/maybe s/Str)]
        :summary "Gets a Judgement by ID"
        :capabilities #{:read-judgement :admin}
        (if-let [d (read-judgement @judgement-store id)]
          (ok d)
          (not-found)))
      (DELETE "/:id" []
        :no-doc true
        :path-params [id :- s/Str]
        :header-params [api_key :- (s/maybe s/Str)]
        :summary "Deletes a Judgement"
        :capabilities #{:delete-judgement :admin}
        (if (flows/delete-flow :get-fn #(read-judgement @judgement-store %)
                               :delete-fn #(delete-judgement @judgement-store %)
                               :object-type :judgement
                               :id id)
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
        :return [StoredSighting]
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
        :summary "Gets all COAs associated with the Indicator"
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
        :header-params [api_key :- (s/maybe s/Str)]
        :capabilities #{:create-indicator :admin}
        :login login
        (ok (flows/create-flow :realize-fn realize-indicator
                               :store-fn #(create-indicator @indicator-store %)
                               :object-type :indicator
                               :login login
                               :object indicator)))
      (PUT "/:id" []
        :return StoredIndicator
        :body [indicator NewIndicator {:description "an updated Indicator"}]
        :summary "Updates an Indicator"
        :path-params [id :- s/Str]
        :header-params [api_key :- (s/maybe s/Str)]
        :capabilities #{:create-indicator :admin}
        :login login
        (ok (flows/update-flow :get-fn #(read-indicator @indicator-store %)
                               :realize-fn realize-indicator
                               :update-fn #(update-indicator @indicator-store (:id %) %)
                               :object-type :indicator
                               :id id
                               :login login
                               :object indicator)))
      (GET "/:id" []
        :return (s/maybe StoredIndicator)
        :summary "Gets an Indicator by ID"
        :path-params [id :- s/Str]
        :header-params [api_key :- (s/maybe s/Str)]
        :capabilities #{:read-indicator :admin}
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
          (not-found)))
      (GET "/title/:title" []
        :return (s/maybe [StoredIndicator])
        :summary "Gets an Indicator by title"
        :path-params [title :- s/Str]
        :header-params [api_key :- (s/maybe s/Str)]
        :capabilities #{:list-indicators-by-title :admin}
        (if-let [d (list-indicators @indicator-store {:title title})]
          (ok d)
          (not-found)))
      (POST "/:id/sighting" []
        :return StoredSighting
        :path-params [id :- s/Str]
        :body [sighting NewSighting {:description "a new Sighting"}]
        :summary "Adds a new Sighting for the given Indicator"
        :header-params [api_key :- (s/maybe s/Str)]
        :capabilities #{:create-sighting :admin}
        :login login
        (if-let [indicator (read-indicator @indicator-store id)]
          (let [sighting (flows/create-flow :realize-fn realize-sighting
                                            :store-fn #(create-sighting @sighting-store %)
                                            :object-type :sighting
                                            :login login
                                            :object (assoc sighting
                                                           :indicator
                                                           {:indicator_id id}))]
            (flows/update-flow :get-fn #(read-indicator @indicator-store %)
                               :realize-fn realize-indicator
                               :update-fn #(update-indicator @indicator-store (:id %) %)
                               :object-type :indicator
                               :id id
                               :login login
                               :object (-> (generalize-indicator indicator)
                                           (update :sightings
                                                   conj {:sighting_id (:id sighting)})))
            (ok sighting))
          (not-found))))

    (context "/ttp" []
      :tags ["TTP"]
      (POST "/" []
        :return StoredTTP
        :body [ttp NewTTP {:description "a new TTP"}]
        :summary "Adds a new TTP"
        :header-params [api_key :- (s/maybe s/Str)]
        :capabilities #{:create-ttp :admin}
        :login login
        (ok (flows/create-flow :realize-fn realize-ttp
                               :store-fn #(create-ttp @ttp-store %)
                               :object-type :ttp
                               :login login
                               :object ttp)))
      (PUT "/:id" []
        :return StoredTTP
        :body [ttp NewTTP {:description "an updated TTP"}]
        :summary "Updates a TTP"
        :path-params [id :- s/Str]
        :header-params [api_key :- (s/maybe s/Str)]
        :capabilities #{:create-ttp :admin}
        :login login
        (ok (flows/update-flow :get-fn #(read-ttp @ttp-store %)
                               :realize-fn realize-ttp
                               :update-fn #(update-ttp @ttp-store (:id %) %)
                               :object-type :ttp
                               :id id
                               :login login
                               :object ttp)))
      (GET "/:id" []
        :return (s/maybe StoredTTP)
        :summary "Gets a TTP by ID"
        :header-params [api_key :- (s/maybe s/Str)]
        :capabilities #{:read-ttp :admin}
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
        :no-doc true
        :path-params [id :- s/Str]
        :summary "Deletes a TTP"
        :header-params [api_key :- (s/maybe s/Str)]
        :capabilities #{:delete-ttp :admin}
        (if (flows/delete-flow :get-fn #(read-ttp @ttp-store %)
                               :delete-fn #(delete-ttp @ttp-store %)
                               :object-type :ttp
                               :id id)
          (no-content)
          (not-found))))

    (context "/sighting" []
      :tags ["Sighting"]
      (POST "/" []
        :return StoredSighting
        :body [sighting NewSighting {:description "A new Sighting"}]
        :header-params [api_key :- s/Str]
        :summary "Adds a new Sighting"
        :capabilities #{:create-sighting :admin}
        :login login
        (ok (flows/create-flow :realize-fn realize-sighting
                               :store-fn #(create-sighting @sighting-store %)
                               :object-type :sighting
                               :login login
                               :object sighting)))
      (PUT "/:id" []
        :return StoredSighting
        :body [sighting NewSighting {:description "An updated Sighting"}]
        :header-params [api_key :- s/Str]
        :summary "Updates a Sighting"
        :path-params [id :- s/Str]
        :capabilities #{:create-sighting :admin}
        :login login
        (ok (flows/update-flow :get-fn #(read-sighting @sighting-store %)
                               :realize-fn realize-sighting
                               :update-fn #(update-sighting @sighting-store (:id %) %)
                               :object-type :sighting
                               :id id
                               :login login
                               :object sighting)))
      (GET "/:id" []
        :return (s/maybe StoredSighting)
        :summary "Gets a Sighting by ID"
        :path-params [id :- s/Str]
        :header-params [api_key :- s/Str]
        :capabilities #{:read-sighting :admin}
        (if-let [d (read-sighting @sighting-store id)]
          (ok d)
          (not-found)))
      (DELETE "/:id" []
        :path-params [id :- s/Str]
        :summary "Deletes a Sighting"
        :header-params [api_key :- s/Str]
        :capabilities #{:delete-sighting :admin}
        (if (delete-sighting @sighting-store id)
          (no-content)
          (not-found))))

    (context "/events" []
      :tags ["Events"]
      (GET "/log" []
        :return [ModelEventBase]
        :summary "Recent Event log"
        :capabilities #{:admin}
        (ok (recent-events))))

    (GET "/:observable_type/:observable_value/judgements" []
      :tags ["Judgement"]
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
      :return (s/maybe [StoredJudgement])
      :summary "Returns all the Judgements associated with the specified observable."
      :header-params [api_key :- (s/maybe s/Str)]
      :capabilities #{:list-judgements-by-observable :admin}
      (ok
       (some->> {:type observable_type
                 :value observable_value}
                (list-judgements-by-observable @judgement-store))))

    (GET "/:observable_type/:observable_value/indicators" []
      :tags ["Indicator"]
      :query-params [{offset :-  Long 0}
                     {limit :-  Long 0}
                     {after :-  Time nil}
                     {before :-  Time nil}
                     {sort_by :- JudgementSort "timestamp"}
                     {sort_order :- SortOrder "desc"}
                     {source :- s/Str nil}]
      :path-params [observable_type :- ObservableType
                    observable_value :- s/Str]
      :return (s/maybe [StoredIndicator])
      :summary "Returns all the Indicators associated with the specified observable."
      :header-params [api_key :- (s/maybe s/Str)]
      :capabilities #{:list-indicators-by-observable :admin}
      (ok
       (some->> {:type observable_type
                 :value observable_value}
                (list-judgements-by-observable @judgement-store)
                (list-indicators-by-judgements @indicator-store))))

    (GET "/:observable_type/:observable_value/sightings" []
      :tags ["Sighting"]
      :query-params [{offset :-  Long 0}
                     {limit :-  Long 0}
                     {after :-  Time nil}
                     {before :-  Time nil}
                     {sort_by :- JudgementSort "timestamp"}
                     {sort_order :- SortOrder "desc"}
                     {source :- s/Str nil}]
      :path-params [observable_type :- ObservableType
                    observable_value :- s/Str]
      :header-params [api_key :- (s/maybe s/Str)]
      :capabilities #{:list-sightings-by-observable :admin}
      :return (s/maybe [StoredSighting])
      :summary "Returns all the Sightings associated with the specified observable."
      (ok
       (some->> {:type observable_type
                 :value observable_value}
                (list-judgements-by-observable @judgement-store)
                (list-indicators-by-judgements @indicator-store)
                (list-sightings-by-indicators @sighting-store))))

    (GET "/:observable_type/:observable_value/verdict" []
      :tags ["Verdict"]
      :path-params [observable_type :- ObservableType
                    observable_value :- s/Str]
      :return (s/maybe Verdict)
      :summary "Returns the current Verdict associated with the specified observable."
      :header-params [api_key :- (s/maybe s/Str)]
      :capabilities #{:get-verdict :admin}
      (if-let [d (calculate-verdict @judgement-store {:type observable_type
                                                      :value observable_value})]
        (ok d)
        (not-found)))))

(def app
  (-> api-handler
      auth/wrap-authentication
      params/wrap-params
      wrap-restful-format))
