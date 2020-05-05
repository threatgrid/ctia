(ns ctia.events-test
  (:require
   [clojure.test :refer [deftest
                         is
                         testing
                         use-fixtures
                         join-fixtures]]
   [clojure.core.async :refer [<!! chan poll! tap]]
   [clojure.set :as set]
   [ctia.entity.event.obj-to-event :as o2e]
   [ctia.events :as e]
   [ctia.lib.async :as la]
   [ctia.test-helpers
    [core :as helpers]
    [es :as es-helpers]]
   [schema.test :as st]))

(use-fixtures :once st/validate-schemas)

(use-fixtures :each (join-fixtures [helpers/fixture-properties:clean
                                    es-helpers/fixture-properties:es-store
                                    helpers/fixture-ctia-fast]))

(deftest test-send-event
  "Tests the basic action of sending an event"
  (let [{b :chan-buf c :chan m :mult :as ec} (la/new-channel)
        output (chan)]
    (try
      (tap m output)
      (e/send-event ec (o2e/to-create-event
                        {:owner "tester"
                         :id "test-1"
                         :tlp "white"
                         :type :test
                         :data 1}
                        "test-1"))
      (e/send-event ec (o2e/to-create-event
                        {:owner "tester"
                         :id "test-2"
                         :tlp "white"
                         :type :test
                         :data 2}
                        "test-2"))
      (e/send-event ec (o2e/to-create-event
                        {:owner "tester"
                         :id "test-3"
                         :tlp "white"
                         :type :test
                         :data 3}
                        "test-3"))
      (is (= 1 (-> (<!! output) :entity :data)))
      (is (= 2 (-> (<!! output) :entity :data)))
      (is (= 3 (-> (<!! output) :entity :data)))
      (finally
        (la/shutdown-channel 100 ec)))))

(deftest test-central-events
  "Tests the basic action of sending an event to the central channel"
  (let [{b :chan-buf c :chan m :mult} @e/central-channel
        output (chan)]
    (tap m output)
    (e/send-event (o2e/to-create-event
                   {:owner "tester"
                    :id "test-1"
                    :tlp "white"
                    :type :test
                    :data 1}
                   "test-1"))
    (e/send-event (o2e/to-create-event
                   {:owner "teseter"
                    :id "test-2"
                    :tlp "white"
                    :type :test
                    :data 2}
                   "test-2"))
    (is (= 1 (-> (<!! output) :entity :data)))
    (is (= 2 (-> (<!! output) :entity :data)))
    (is (nil? (poll! output)))))

(deftest to-update-event-test
  (let [old {:owner "tester"
             :id "test-2"
             :tlp "white"
             :type :test
             :data 2}]
    (is (= [{:field :new, :action "added", :change {:after "bar"}}]
           (:fields
             (o2e/to-update-event
               (assoc old :new "bar") old
               "foo"))))
    (is (= [{:field :data, :action "deleted", :change {:before 2}}]
           (:fields
             (o2e/to-update-event
               (dissoc old :data) old
               "foo"))))
    (is (= [{:field :data, :action "modified",
             :change {:before 2
                      :after 3}}]
           (:fields
             (o2e/to-update-event
               (assoc old :data 3) old
               "foo"))))
    (is (= [{:field :data,
             :action "modified",
             :change {:before [1], :after [1 2]}}]
           (:fields
             (o2e/to-update-event
               (assoc old :data [1 2])
               (assoc old :data [1])
               "foo"))))
    (is (= [{:action "added"
             :field :added
             :change {:after 1}}
            {:action "modified"
             :field :data
             :change {:before {}
                      :after {:a 1}}}
            {:action "deleted"
             :field :removed
             :change {:before 1}}]
           (:fields
             (o2e/to-update-event
               (-> old
                   (assoc :data {:a 1}
                          :added 1)
                   (dissoc :removed))
               (assoc old :data {} :removed 1)
               "foo"))))))
