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

(deftest truncate-test
  (let [v (o2e/truncate (range 10) '... 5 5)]
    (is (seq? v))
    (is (= (concat (range 5) ['...])
           v)))
  (let [v (o2e/truncate (vec (range 10)) '... 5 5)]
    (is (vector? v))
    (is (= (concat (range 5) ['...])
           v)))
  (is (= (last (take 7 (iterate vector '...)))
         (o2e/truncate (last (take 15 (iterate vector 1))) '... 5 5)))
  (is (= (last (take 7 (iterate vector '...)))
         (o2e/truncate (last (take 15 (iterate vector (range 10)))) '... 5 5)))
  (let [v (o2e/truncate {:a (zipmap (range 10) (range))} '... 5 5)]
    (is (map? v))
    (is (map? (:a v)))
    (is (= #{'...}
           (set/difference
             (set (keys (:a v)))
             (set (range 10)))))
    (is (= {:a (-> (zipmap (range 10) (range))
                   (assoc '... '...)
                   (select-keys (keys (:a v))))}
           v))))

(deftest to-update-event-test
  (let [to-update-event #(o2e/to-update-event
                           %1 %2 %3
                           {:placeholder '...
                            :max-count 10
                            :max-depth 10})
        old {:owner "tester"
             :id "test-2"
             :tlp "white"
             :type :test
             :data 2}]
    (is (= [{:field :new, :action "added", :change {:after "bar"}}]
           (:fields
             (to-update-event
               (assoc old :new "bar") old
               "foo"))))
    (is (= [{:field :data, :action "deleted", :change {:before 2}}]
           (:fields
             (to-update-event
               (dissoc old :data) old
               "foo"))))
    (is (= [{:field :data, :action "modified",
             :change {:before 2
                      :after 3}}]
           (:fields
             (to-update-event
               (assoc old :data 3) old
               "foo"))))
    (is (= [{:field :data,
             :action "modified",
             :change {:before [1], :after [1 2]}}]
           (:fields
             (to-update-event
               (assoc old :data [1 2])
               (assoc old :data [1])
               "foo"))))
    (is (= [{:field :data,
             :action "modified",
             :change {:before [0], :after (concat (range 10) ['...])}}]
           (:fields
             (to-update-event
               (assoc old :data (range 1000))
               (assoc old :data [0])
               "foo"))))
    (is (= [{:field :data,
             :action "modified",
             :change {:before '[[[[[[[[[[[...]]]]]]]]]]], :after [0]}}]
           (:fields
             (to-update-event
               (assoc old :data [0])
               (assoc old :data (last (take 15 (iterate vector 1))))
               "foo"))))
    (is (= [{:field :data,
             :action "modified",
             :change {:before '[[[[[[[[[[[...]]]]]]]]]]], :after [0]}}]
           (:fields
             (to-update-event
               (assoc old :data [0])
               (assoc old :data (last (take 15 (iterate vector 1))))
               "foo"))))
    (is (= [{:field :data,
             :action "modified",
             :change
             {:before 2,
              :after
              {:a '(0 1 2 3 4 5 6 7 8 9 ...),
               :b '(0 1 2 3 4 5 6 7 8 9 ...),
               :c '[[[[[[[[[[...]]]]]]]]]]}}}]
           (:fields
             (to-update-event
               (assoc old :data {:a (range 100) :b (range 23) :c (last (take 15 (iterate vector 1)))})
               old
               "foo"))))))
