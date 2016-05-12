(ns ctia.http.routes.feedback
  (:require
   [clojure.string :refer [capitalize]]
   [compojure.api.sweet :refer :all]
   [ctia.flows.crud :as flows]
   [ctia.http.routes.common :refer [paginated-ok PagingParams]]
   [ctia.schemas.feedback :refer [NewFeedback realize-feedback StoredFeedback]]
   [ctia.store :refer :all]
   [ring.util.http-response :refer :all]
   [schema-tools.core :as st]
   [schema.core :as s]))

(s/defschema FeedbackQueryParams
  (st/merge
   PagingParams
   {(s/optional-key :sort_by) (s/enum :id :feedback :reason)}))

(defn entity-label [e]
  "Generate a human readable label from an entity name"
  (case e
    "coa" "COA"
    "exploit-target" "ExploitTarget"
    "ttp" "TTP"
    (capitalize e)))

(defmacro def-feedback-routes
  "Generates all feedback routes (GET and POST) for the given entities"
  [route-symbol entities]
  `(defroutes ~(symbol (str route-symbol))
     ~@(for [entity entities]
         `(context ~(str "/" entity) []
            :tags ~[(entity-label entity)]
            (POST ~(str "/:" entity "-id/feedback") []
              :return StoredFeedback
              :path-params [~(symbol (str entity "-id")) :- s/Str]
              :body [feedback# NewFeedback {:description
                                            ~(str "a new Feedback on a " (entity-label entity))}]
              :header-params [~(symbol "api_key") :- (s/maybe s/Str)]
              :summary ~(str "Adds a feedback to a " entity)
              :capabilities #{:create-feedback :admin}
              :login login#
              (ok (flows/create-flow :realize-fn #(realize-feedback %1 %2 %3
                                                                    ~(symbol (str entity "-id")))
                                     :store-fn #(create-feedback @feedback-store %)
                                     :entity-type :feedback
                                     :login login#
                                     :entity feedback#)))

            (GET ~(str "/:" entity "-id/feedback") []
              :tags ~[(entity-label entity)]
              :return (s/maybe [StoredFeedback])
              :query [~(symbol "params") FeedbackQueryParams]
              :path-params [~(symbol (str entity "-id")) :- s/Str]
              :header-params [~(symbol "api_key") :- (s/maybe s/Str)]
              :capabilities #{:read-feedback :admin}
              :summary ~(str "Gets all Feedback for this " (entity-label entity))

              (paginated-ok
               (list-feedback @feedback-store
                              {:entity_id ~(symbol (str entity "-id"))} ~(symbol "params"))))))))

(def-feedback-routes "feedback-routes" ["actor"
                                        "campaign"
                                        "coa"
                                        "exploit-target"
                                        "incident"
                                        "sighting"
                                        "ttp"
                                        "judgement"
                                        "indicator"])
