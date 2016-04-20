(ns ctia.events.obj-to-event-test
  (:require [clojure.data :refer [diff]]
            [ctia.events.obj-to-event :as evt]
            [ctia.events.schemas :refer [CreateEvent
                                         UpdateEvent
                                         DeleteEvent]]
            [ctia.schemas.actor :refer [StoredActor]]
            [schema.core :as s]
            [clojure.test.check.properties :as properties]
            [clojure.test.check.generators :as check-generators]
            [clojure.test.check.clojure-test :as check-clojure-test]
            [schema.experimental.generators :as generators]
            [clojure.test :as t]))

(t/deftest test-to-create-event
  (doseq [actor (generators/sample 100 StoredActor)]
    (let [event (evt/to-create-event StoredActor actor)]
      (t/is (s/validate CreateEvent event)))))

(t/deftest test-to-update-event
  (doseq [actor (generators/sample 100 StoredActor)]
    (let [event (evt/to-update-event StoredActor actor actor)]
      (t/is (s/validate UpdateEvent event)))))

(t/deftest test-to-delete-event
  (doseq [actor (generators/sample 100 StoredActor)]
    (let [event (evt/to-delete-event StoredActor actor)]
      (t/is (s/validate DeleteEvent event)))))

(t/deftest test-triplet-generation
  (t/is
   #{[:to-remove "deleted" {}]
     [:to-change "modified" {0 1}]
     [:to-add "added" {}]}
   (set (evt/diff-to-list-of-triplet (diff {:to-remove 0
                                            :to-stay 0
                                            :to-change 0}
                                           {:to-stay 0
                                            :to-change 1
                                            :to-add 0})))))
