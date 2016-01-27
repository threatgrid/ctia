(ns cia.auth)

(def capabilities
  [:get-verdict

   ;; judgements
   :create-judgement
   :read-judgement
   :delete-judgement
   :list-judgements-by-observable
   :list-judgements-by-indicator

   ;; indicators
   :create-indicator
   :read-indicator
   :read-indicator-implementation
   :update-indicator
   :delete-indicator
   :list-indicators

   ;; feedback
   :create-feedback


   ;; threats
   :create-ttp
   :update-ttp
   :delete-ttp
   :read-ttp
   :list-ttps

   :create-campaign
   :update-campaign
   :delete-campaign
   :read-campaign
   :list-campaigns

   :create-actor
   :update-actor
   :delete-actor
   :read-actor
   :list-actors


   ;; sightings
   :create-sighting
   :update-sighting
   :delete-sighting
   :read-sighting
   :list-sightings-by-observable
   :list-sightings-by-indicator
   :list-sightings

   ;; incidents
   :create-incident
   :update-incident
   :delete-incident
   :read-incident
   :list-incidents-by-observable
   :list-incidents-by-indicator
   :list-incidents

   ;; relations
   :create-relation
   :delete-relation
   :update-relation
   :read-relation
   :list-relations
   
   ])

(defprotocol IAuth
  (default-capabilities [this])
  (capabilities-for-token [this token])
  (owner-for-token [this token]))

(defonce auth-service
  (atom
   (reify IAuth
     (default-capabilities [this]
       capabilities)
     
     (capabilities-for-token [this token]
       capabilities)

     (owner-for-token [this token]
       "default"))))



