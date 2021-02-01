(ns ctia.entity.target-record.graphql-schemas
  (:require
   [ctia.entity.feedback.graphql-schemas :as feedback]
   [ctia.entity.relationship.graphql-schemas :as relationship]
   [ctia.entity.target-record :as target-record]
   [ctia.schemas.graphql.flanders :as flanders]
   [ctia.schemas.graphql.helpers :as g]
   [ctia.schemas.graphql.ownership :as go]
   [ctia.schemas.graphql.pagination :as pagination]
   [ctia.schemas.graphql.refs :as refs]
   [ctia.schemas.graphql.sorting :as sorting]
   [ctim.schemas.target-record :as target-record-schema]
   [flanders.utils :as fu]))

(def TargetRecordType
  (let [{:keys [fields name description]}
        (flanders/->graphql
         (fu/optionalize-all target-record-schema/TargetRecord)
         {refs/observable-type-name refs/ObservableTypeRef})]
    (g/new-object
     name
     description
     []
     (merge fields
            feedback/feedback-connection-field
            relationship/relatable-entity-fields
            go/graphql-ownership-fields))))

(def target-record-order-arg
  (sorting/order-by-arg
   "TargetRecordOrder"
   "target-records"
   (into {}
         (map (juxt sorting/sorting-kw->enum-name name)
              target-record/target-record-fields))))

(def TargetRecordConnectionType
  (pagination/new-connection TargetRecordType))
