(ns ctia.observable.graphql.schemas
  (:refer-clojure :exclude [update list read])
  (:require
   [ctia.store :refer :all]
   [ctia.domain.entities :as ctia-entities]
   [ctia.entity.judgement :as judgement]
   [ctia.entity.sighting.graphql-schemas :as sighting]
   [ctia.schemas.graphql
    [flanders :as f]
    [helpers :as g]
    [pagination :as pagination]
    [resolvers :as resolvers]]
   [ctia.store :refer :all]
   [ctia.verdict.graphql.schemas :as verdict]
   [ctim.schemas.common :as ctim-common-schema]
   [flanders.utils :as fu]))

(defn observable-verdict
  [{observable-type :type
    observable-value :value}
   ident
   {{{:keys [get-in-config]} :ConfigService
     {:keys [read-store]} :StoreService}
    :services
    :as _rt-opt_}]
  (some-> (read-store :judgement
                      calculate-verdict
                      {:type observable-type :value observable-value}
                      ident)
          (clojure.core/update :judgement_id ctia-entities/short-id->long-id get-in-config)))

(def observable-fields
  {:verdict {:type verdict/VerdictType
             :resolve (fn [context _ _ src]
                       #(observable-verdict (select-keys src [:type :value])
                                            (:ident context) %))}
   :judgements {:type judgement/JudgementConnectionType
                :args (into pagination/connection-arguments
                            judgement/judgement-order-arg)
                :resolve (fn [context args field-selection src]
                           (resolvers/search-judgements-by-observable
                            (select-keys src [:type :value])
                            context
                            args
                            field-selection))}
   :sightings {:type sighting/SightingConnectionType
               :args (into pagination/connection-arguments
                           sighting/sighting-order-arg)
               :resolve (fn [context args field-selection src]
                          (resolvers/search-sightings-by-observable
                           (select-keys src [:type :value])
                           context
                           args
                           field-selection))}})

(def ObservableType
  (let [{:keys [fields name description]}
        (f/->graphql (fu/optionalize-all ctim-common-schema/Observable))]
    (g/new-object name description [] (into observable-fields
                                            fields))))
