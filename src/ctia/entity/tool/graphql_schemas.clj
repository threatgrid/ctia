(ns ctia.entity.tool.graphql-schemas
  (:require [ctia.entity.feedback.graphql-schemas :as feedback]
            [ctia.entity.relationship.graphql-schemas :as relationship]
            [ctia.schemas.graphql
             [flanders :as flanders]
             [helpers :as g]
             [pagination :as pagination]
             [sorting :as sorting]]
            [ctim.schemas.tool :as ctim-tool]
            [flanders.utils :as fu]
            [ctia.entity.tool.schemas :as ts]))

(def ToolType
  (let [{:keys [fields name description]}
        (flanders/->graphql
         (fu/optionalize-all ctim-tool/Tool)
         {})]
    (g/new-object
     name
     description
     []
     (merge fields
            feedback/feedback-connection-field
            relationship/relatable-entity-fields))))

(def tool-order-arg
  (sorting/order-by-arg
   "ToolOrder"
   "tools"
   (into {}
         (map (juxt sorting/sorting-kw->enum-name name)
              ts/tool-fields))))

(def ToolConnectionType
  (pagination/new-connection ToolType))
