(ns ctia.events.obj-to-event-test
  (:require [clojure.data :refer [diff]]
            [clojure.test.check.clojure-test :refer [defspec]]
            [clojure.test.check.properties :refer [for-all]]
            [ctia.events.obj-to-event :as evt]
            [ctia.events.schemas :refer [CreateEvent
                                         UpdateEvent
                                         DeleteEvent]]
            [ctia.test-helpers.core :as helpers]
            [ctia.test-helpers.generators.schemas :as gen]
            [clojure.test :refer [deftest is use-fixtures]]
            [schema.core :as s]))

(use-fixtures :once helpers/fixture-schema-validation)

(defspec spec-to-create-event
  (for-all [actor (gen/gen-entity :actor)]
           (s/validate CreateEvent (evt/to-create-event actor))))

(defspec spec-to-update-event
  (for-all [actor (gen/gen-entity :actor)]
           (s/validate UpdateEvent (evt/to-update-event actor actor))))

(defspec spec-to-delete-event
  (for-all [actor (gen/gen-entity :actor)]
           (s/validate DeleteEvent (evt/to-delete-event actor))))

(deftest test-triplet-generation
  (is
   (=
    #{[:to-remove "deleted" {}]
      [:to-change "modified" {0 1}]
      [:to-add "added" {}]}
    (set (evt/diff-to-list-of-triplet (diff {:to-remove 0
                                             :to-stay 0
                                             :to-change 0}
                                            {:to-stay 0
                                             :to-change 1
                                             :to-add 0}))))))
