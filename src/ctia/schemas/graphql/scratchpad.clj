(ns ctia.schemas.graphql.scratchpad
  (:require
   [flanders.utils :as fu]
   [ctia.schemas.graphql
    [flanders :as flanders]
    [helpers :as g]
    [pagination :as pagination]
    [refs :as refs]
    [sorting :as sorting]]
   [ctia.schemas.sorting :as sort-fields]
   [ctim.schemas.scratchpad :as ctim-scratchpad]))

(def ScratchpadType
  (let [{:keys [fields name description]}
        (flanders/->graphql
         (fu/optionalize-all ctim-scratchpad/Scratchpad)
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

(def scratchpad-order-arg
  (sorting/order-by-arg
   "ScratchpadOrder"
   "scratchpads"
   (into {}
         (map (juxt sorting/sorting-kw->enum-name name)
              sort-fields/scratchpad-sort-fields))))

(def ScratchpadConnectionType
  (pagination/new-connection ScratchpadType))
