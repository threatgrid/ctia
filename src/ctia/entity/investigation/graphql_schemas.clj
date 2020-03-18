(ns ctia.entity.investigation.graphql-schemas
  (:require [flanders.utils :as fu]
            [ctia.schemas.graphql
             [sorting :as graphql-sorting]
             [flanders :as flanders]
             [helpers :as g]
             [pagination :as pagination]]
            [ctia.entity.investigation.flanders-schemas :as f-inv]
            [ctia.entity.investigation :refer [investigation-fields]]
            [ctia.schemas.graphql.ownership :as go]))

(def InvestigationType
  (let [{:keys [fields name description]}
        (flanders/->graphql
         (fu/optionalize-all f-inv/Investigation)
         {})]
    (g/new-object
     name
     description
     []
     (merge
      fields go/graphql-ownership-fields))))

(def investigation-order-arg
  (graphql-sorting/order-by-arg
   "InvestigationOrder"
   "investigations"
   (into {}
         (map (juxt graphql-sorting/sorting-kw->enum-name name)
              investigation-fields))))

(def InvestigationConnectionType
  (pagination/new-connection InvestigationType))

