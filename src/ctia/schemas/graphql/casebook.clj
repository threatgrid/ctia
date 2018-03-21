(ns ctia.schemas.graphql.casebook
  (:require
   [flanders.utils :as fu]
   [ctia.schemas.graphql
    [flanders :as flanders]
    [helpers :as g]
    [pagination :as pagination]
    [refs :as refs]
    [sorting :as sorting]]
   [ctia.schemas.sorting :as sort-fields]
   [ctim.schemas.casebook :as ctim-casebook]))

(def CasebookType
  (let [{:keys [fields name description]}
        (flanders/->graphql
         (fu/optionalize-all ctim-casebook/Casebook)
         {refs/observable-type-name refs/ObservableTypeRef
          refs/judgement-type-name refs/JudgementRef
          refs/sighting-type-name refs/SightingRef
          refs/verdict-type-name refs/VerdictRef
          })]
    (g/new-object
     name
     description
     []
     fields)))

(def casebook-order-arg
  (sorting/order-by-arg
   "CasebookOrder"
   "casebooks"
   (into {}
         (map (juxt sorting/sorting-kw->enum-name name)
              sort-fields/casebook-sort-fields))))

(def CasebookConnectionType
  (pagination/new-connection CasebookType))
