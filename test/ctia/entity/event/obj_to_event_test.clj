(ns ctia.entity.event.obj-to-event-test
  (:require [clojure.test :refer [deftest is]]
            [ctia.entity.event.obj-to-event :as o2e]
            [ctia.http.routes.common :refer [default-now-fn]]))

(deftest to-update-event-test
  (let [services {:CTIATimeService {:now default-now-fn}}
        old {:owner "tester"
             :id "test-2"
             :tlp "white"
             :type :test
             :data 2}]
    (is (= [{:field :new, :action "added", :change {:after "bar"}}]
           (:fields
             (o2e/to-update-event
               services
               (assoc old :new "bar") old
               "foo"))))
    (is (= [{:field :data, :action "deleted", :change {:before 2}}]
           (:fields
             (o2e/to-update-event
               services
               (dissoc old :data) old
               "foo"))))
    (is (= [{:field :data, :action "modified",
             :change {:before 2
                      :after 3}}]
           (:fields
             (o2e/to-update-event
               services
               (assoc old :data 3) old
               "foo"))))
    (is (= [{:field :data,
             :action "modified",
             :change {:before [1], :after [1 2]}}]
           (:fields
             (o2e/to-update-event
               services
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
               services
               (-> old
                   (assoc :data {:a 1}
                          :added 1)
                   (dissoc :removed))
               (assoc old :data {} :removed 1)
               "foo"))))))
