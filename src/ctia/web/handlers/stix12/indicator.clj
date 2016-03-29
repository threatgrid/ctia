(ns ctia.web.handlers.stix12.indicator
  (:require [ctia.store :as store]
            [ctia.web.handlers.stix12.indicator
             [composite-indicator-expression
              :refer [composite-indicator-expression?
                      attach-composite-indicator-expression]]
             [confidence
              :refer [confidence? attach-confidence]]
             [kill-chain-phases
              :refer [kill-chain-phases? attach-kill-chain-phases]]
             [likely-impact
              :refer [likely-impact? attach-likely-impact]]
             [related-indicators
              :refer [related-indicators? attach-related-indicators]]
             [tags
              :refer [tags? attach-tags]]
             [type
              :refer [type? attach-type]]]
            [ctia.web.handlers.stix12.observable
             :refer [ctia-observable->stix-observable]])
  (:import javax.xml.namespace.QName

           [org.mitre.stix.common_1
            StructuredTextType]

           [org.mitre.stix.indicator_2
            Indicator]))

(defn ->stix-indicator [indicator]
  (->
   (doto (Indicator.)
     (.setId (QName. (:id indicator)))
     (.setTitle (:title indicator))
     (.withDescriptions
      [(.withValue (StructuredTextType.)
                   (:description indicator))
       (.withValue (StructuredTextType.)
                   (str "producer: " (:producer indicator)))])
     (cond->
         (:short_description indicator)
         (.withShortDescriptions
          [(doto (StructuredTextType.)
             (.setValue (:short_description indicator)))])

         (not-empty (:alternate_ids indicator))
         (.withAlternativeIDs
          (:alternate_ids indicator))

         (:version indicator)
         (.withVersion (str (:version indicator)))

         (some? (:negate indicator))
         (.withNegate (:negate indicator))

         (type? indicator)
         (attach-type indicator)

         (tags? indicator)
         (attach-tags indicator)

         (:observable indicator)
         (.withObservable
          (ctia-observable->stix-observable
           (:observable indicator)))

         (related-indicators? indicator)
         (attach-related-indicators indicator ->stix-indicator)

         (composite-indicator-expression? indicator)
         (attach-composite-indicator-expression indicator ->stix-indicator)

         (likely-impact? indicator)
         (attach-likely-impact indicator)

         (confidence? indicator)
         (attach-confidence indicator)

         (kill-chain-phases? indicator)
         (attach-kill-chain-phases indicator)))))

(def pretty-print? false)

(defn read-xml [id]
  (if-let [indicator (store/read-indicator @store/indicator-store id)]
    (-> (->stix-indicator indicator)
        (.toXMLString pretty-print?))))
