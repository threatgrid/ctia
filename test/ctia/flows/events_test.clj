(ns ctia.flows.events-test
  (:require [clj-momo.test-helpers.core :as mth]
            [clojure
             [string :as str]
             [test :refer [is join-fixtures testing use-fixtures]]]
            [ctia.domain.entities :refer [schema-version]]
            [ctia.store :as store]
            [ctia.test-helpers
             [core :as test-helpers :refer [deftest-for-each-fixture post]]
             [es :as es-helpers]]
            [ctim.domain.id :as id]))

(use-fixtures :once mth/fixture-schema-validation)

(deftest-for-each-fixture test-flow-event-creation

  {:es-aliased-index (join-fixtures [test-helpers/fixture-properties:clean
                                     es-helpers/fixture-properties:es-store
                                     test-helpers/fixture-properties:events-aliased-index
                                     test-helpers/fixture-ctia
                                     test-helpers/fixture-allow-all-auth
                                     es-helpers/fixture-purge-event-indexes
                                     es-helpers/fixture-delete-store-indexes])

   :es-simple-index (join-fixtures [test-helpers/fixture-properties:clean
                                    es-helpers/fixture-properties:es-store
                                    test-helpers/fixture-properties:events-enabled
                                    test-helpers/fixture-ctia
                                    test-helpers/fixture-allow-all-auth
                                    es-helpers/fixture-purge-event-indexes
                                    es-helpers/fixture-delete-store-indexes])}

  (testing "Events are published to es"
    (let [{{judgement-1-long-id :id
            :as judgement-1} :parsed-body
           judgement-1-status :status}
          (post "ctia/judgement"
                :body {:observable {:value "1.2.3.4"
                                    :type "ip"}
                       :disposition 1
                       :source "source"
                       :tlp "green"
                       :priority 100
                       :severity "High"
                       :confidence "Low"
                       :valid_time {:start_time "2016-02-11T00:40:48.212-00:00"}})

          judgement-1-id
          (id/long-id->id judgement-1-long-id)

          {{judgement-2-long-id :id
            :as judgement-2} :parsed-body
           judgement-2-status :status}
          (post "ctia/judgement"
                :body {:observable {:value "1.2.3.4"
                                    :type "ip"}
                       :disposition 2
                       :source "source"
                       :tlp "green"
                       :priority 100
                       :severity "High"
                       :confidence "Low"
                       :valid_time {:start_time "2016-02-11T00:40:48.212-00:00"}})

          judgement-2-id
          (id/long-id->id judgement-2-long-id)

          {{judgement-3-long-id :id
            :as judgement-3} :parsed-body
           judgement-3-status :status}
          (post "ctia/judgement"
                :body {:observable {:value "1.2.3.4"
                                    :type "ip"}
                       :disposition 3
                       :source "source"
                       :tlp "green"
                       :priority 100
                       :severity "High"
                       :confidence "Low"
                       :valid_time {:start_time "2016-02-11T00:40:48.212-00:00"}})

          judgement-3-id
          (id/long-id->id judgement-3-long-id)]

      (is (= 201 judgement-1-status))
      (is (= 201 judgement-2-status))
      (is (= 201 judgement-3-status))

      (let [events (:data (store/read-store :event
                                            store/list-events
                                            {:owner "Unknown"}
                                            {:login "Unknown"
                                             :groups ["Administrators"]}
                                            {:sort_by :timestamp
                                             :sort_order :asc}))]

        (testing "variable event fields have correct type"
          (doseq [event events]
            (is (str/starts-with? (:id event) "event-"))
            (is (instance? java.util.Date (:timestamp event)))))
        (is (= [{:owner "Unknown"
                 :groups ["Administrators"]
                 :entity {:valid_time
                          {:start_time "2016-02-11T00:40:48.212Z"
                           :end_time "2525-01-01T00:00:00.000Z"}
                          :observable {:value "1.2.3.4" :type "ip"},
                          :type "judgement"
                          :source "source"
                          :tlp "green"
                          :schema_version schema-version
                          :disposition 1
                          :disposition_name "Clean"
                          :priority 100
                          :id (id/long-id judgement-1-id)
                          :severity "High"
                          :confidence "Low"
                          :owner "Unknown"
                          :groups ["Administrators"]}
                 :type "CreatedModel"}
                {:owner "Unknown"
                 :groups ["Administrators"]
                 :entity {:valid_time
                          {:start_time "2016-02-11T00:40:48.212Z"
                           :end_time "2525-01-01T00:00:00.000Z"}
                          :observable {:value "1.2.3.4" :type "ip"},
                          :type "judgement"
                          :source "source"
                          :tlp "green"
                          :schema_version schema-version
                          :disposition 2
                          :disposition_name "Malicious"
                          :priority 100
                          :id (id/long-id judgement-2-id)
                          :severity "High"
                          :confidence "Low"
                          :owner "Unknown"
                          :groups ["Administrators"]}
                 :type "CreatedModel"}
                {:owner "Unknown"
                 :groups ["Administrators"]
                 :entity {:valid_time
                          {:start_time "2016-02-11T00:40:48.212Z"
                           :end_time "2525-01-01T00:00:00.000Z"}
                          :observable {:value "1.2.3.4" :type "ip"},
                          :type "judgement"
                          :source "source"
                          :tlp "green"
                          :schema_version schema-version
                          :disposition 3
                          :disposition_name "Suspicious"
                          :priority 100
                          :id (id/long-id judgement-3-id)
                          :severity "High"
                          :confidence "Low"
                          :owner "Unknown"
                          :groups ["Administrators"]}
                 :type "CreatedModel"}]
               (->> events
                    (map #(dissoc % :http-params :id :timestamp :created))
                    (map #(update % :entity dissoc :created)))))))))
