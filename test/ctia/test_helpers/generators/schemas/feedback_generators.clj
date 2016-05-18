(ns ctia.test-helpers.generators.schemas.feedback-generators
  (:require [clojure.test.check.generators :as gen]
            [ctia.lib.time :as time]
            [ctia.schemas
             [feedback :refer [NewFeedback Feedback]]
             [common :as schemas-common]]
            [ctia.test-helpers.generators.common
             :refer [complete leaf-generators maybe]
             :as common]
            [ctia.test-helpers.generators.id :as gen-id]))

(def gen-feedback
  (gen/fmap
   (fn [id]
     (complete
      Feedback
      {:id id}))
   (gen-id/gen-short-id-of-type :feedback)))

(def gen-new-feedback
  (gen/fmap
   (fn [[id entity-id]]
     (complete
      Feedback
      (cond-> {}
        id (assoc :id id)
        entity-id (assoc :entity_id entity-id))))
   (gen/tuple
    (gen-id/gen-short-id-of-type :feedback)
    (gen-id/gen-short-id-of-type :judgement))))
