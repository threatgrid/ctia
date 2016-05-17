(ns ctia.test-helpers.generators.schemas.ttp-generators
  (:require [clojure.test.check.generators :as gen]
            [ctia.lib.time :as time]
            [ctia.schemas
             [common :as schemas-common]
             [ttp :refer [NewTTP StoredTTP]]]
            [ctia.test-helpers.generators.common
             :refer [complete leaf-generators maybe]
             :as common]
            [ctia.test-helpers.generators.id :as gen-id]))

(def gen-ttp
  (gen/fmap
   (fn [id]
     (complete
      StoredTTP
      {:id id}))
   (gen-id/gen-short-id-of-type :ttp)))

(def gen-new-ttp
  (gen/fmap
   (fn [id]
     (complete
      NewTTP
      (cond-> {}
        id (assoc :id id))))
   (maybe (gen-id/gen-short-id-of-type :ttp))))
