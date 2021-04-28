(ns ctia.entity.entities
  (:require
   ;; !!! Order Matters !!!
   [clojure.tools.logging :as log]
   [ctia.schemas.core :refer [Entity]]
   [ctia.entity
    [asset :refer [asset-entity]]
    [asset-mapping :refer [asset-mapping-entity]]
    [asset-properties :refer [asset-properties-entity]]
    [target-record :refer [target-record-entity]]
    [feedback :refer [feedback-entity]]
    [weakness :refer [weakness-entity]]
    [vulnerability :refer [vulnerability-entity]]
    [attack-pattern :refer [attack-pattern-entity]]
    [indicator :refer [indicator-entity]]
    [incident :refer [incident-entity]]
    [malware :refer [malware-entity]]
    [tool :refer [tool-entity]]
    [sighting :refer [sighting-entity]]
    [judgement :refer [judgement-entity]]
    [casebook :refer [casebook-entity]]
    [identity-assertion :refer [identity-assertion-entity]]
    [actor :refer [actor-entity]]
    [campaign :refer [campaign-entity]]
    [coa :refer [coa-entity]]
    [data-table :refer [data-table-entity]]
    [relationship :refer [relationship-entity]]
    [identity :refer [identity-entity]]
    [feed :refer [feed-entity]]
    [event :refer [event-entity]]
    [investigation :refer [investigation-entity]]]
   [schema.core :as s]))

(s/defschema AllEntities 
  {(s/pred simple-keyword?) Entity})

(s/defn all-entities :- AllEntities []
  {:actor              actor-entity
   :asset              asset-entity
   :asset-mapping      asset-mapping-entity
   :asset-properties   asset-properties-entity
   :attack-pattern     attack-pattern-entity
   :campaign           campaign-entity
   :casebook           casebook-entity
   :coa                coa-entity
   :data-table         data-table-entity
   :event              event-entity
   :feed               feed-entity
   :feedback           feedback-entity
   :identity           identity-entity
   :identity-assertion identity-assertion-entity
   :incident           incident-entity
   :indicator          indicator-entity
   :investigation      investigation-entity
   :judgement          judgement-entity
   :malware            malware-entity
   :relationship       relationship-entity
   :sighting           sighting-entity
   :target-record      target-record-entity
   :tool               tool-entity
   :vulnerability      vulnerability-entity
   :weakness           weakness-entity})

(defn entity-plural->entity
  "Returns entity map for given plural form of the entity key"
  [entity-key]
  (reduce-kv
   (fn [m k v]
     (if (-> v :plural (= entity-key))
       (assoc m k v)
       m))
   {}
   (all-entities)))
