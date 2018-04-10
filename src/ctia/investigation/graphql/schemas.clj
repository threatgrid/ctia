(ns ctia.schemas.graphql.investigation
  (:require
   [flanders.utils :as fu]
   [ctia.schemas.graphql
    [flanders :as flanders]
    [helpers :as g]
    [pagination :as pagination]
    [refs :as refs]
    [sorting :as sorting]]
   [ctia.schemas.sorting :as sort-fields]
   [ctim.schemas.investigation :as ctim-investigation]))

(def InvestigationType
  (let [{:keys [fields name description]}
        (flanders/->graphql
         (fu/optionalize-all ctim-investigation/Investigation)
         {})]
    (g/new-object
     name
     description
     []
     fields)))

(def investigation-order-arg
  (sorting/order-by-arg
   "InvestigationOrder"
   "investigations"
   (into {}
         (map (juxt sorting/sorting-kw->enum-name name)
              sort-fields/investigation-sort-fields))))

(def InvestigationConnectionType
  (pagination/new-connection InvestigationType))
