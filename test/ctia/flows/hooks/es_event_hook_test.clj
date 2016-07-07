(ns ctia.flows.hooks.es-event-hook-test
  (:require [clojure.test :refer [is join-fixtures testing use-fixtures]]
            [ctia.domain.entities :refer [schema-version]]
            [ctia.events.producers.es.producer :refer [init-producer-conn]]
            [ctia.lib.es
             [document :as document]
             [index :as index]]
            [ctim.schemas.common :as c]
            [ctia.test-helpers
             [core :as test-helpers :refer [deftest-for-each-fixture post]]
             [es :as es-helpers]]))

(use-fixtures :once test-helpers/fixture-schema-validation)

(deftest-for-each-fixture test-event-producer

  {:es-filtered-alias (join-fixtures [test-helpers/fixture-properties:clean
                                      test-helpers/fixture-properties:atom-store
                                      test-helpers/fixture-properties:es-hook-filtered-alias
                                      test-helpers/fixture-ctia
                                      test-helpers/fixture-allow-all-auth
                                      es-helpers/fixture-purge-producer-indexes])

   :es-aliased-index (join-fixtures [test-helpers/fixture-properties:clean
                                     test-helpers/fixture-properties:atom-store
                                     test-helpers/fixture-properties:es-hook-aliased-index
                                     test-helpers/fixture-ctia
                                     test-helpers/fixture-allow-all-auth
                                     es-helpers/fixture-purge-producer-indexes])}

  (testing "Events are published to es"
    (let [{{judgement-1-id :id} :parsed-body
           judgement-1-status :status
           :as judgement-1}
          (post "ctia/judgement"
                :body {:observable {:value "1.2.3.4"
                                    :type "ip"}
                       :disposition 1
                       :source "source"
                       :tlp "green"
                       :priority 100
                       :severity 100
                       :confidence "Low"
                       :valid_time {:start_time "2016-02-11T00:40:48.212-00:00"}})

          {{judgement-2-id :id} :parsed-body
           judgement-2-status :status
           :as judgement-2}
          (post "ctia/judgement"
                :body {:observable {:value "1.2.3.4"
                                    :type "ip"}
                       :disposition 2
                       :source "source"
                       :tlp "green"
                       :priority 100
                       :severity 100
                       :confidence "Low"
                       :valid_time {:start_time "2016-02-11T00:40:48.212-00:00"}})

          {{judgement-3-id :id} :parsed-body
           judgement-3-status :status
           :as judgement-3}
          (post "ctia/judgement"
                :body {:observable {:value "1.2.3.4"
                                    :type "ip"}
                       :disposition 3
                       :source "source"
                       :tlp "green"
                       :priority 100
                       :severity 100
                       :confidence "Low"
                       :valid_time {:start_time "2016-02-11T00:40:48.212-00:00"}})]

      (is (= 201 judgement-1-status))
      (is (= 201 judgement-2-status))
      (is (= 201 judgement-3-status))

      (let [{:keys [index conn props]} (init-producer-conn)]
        ((index/refresh-fn conn) conn index)

        (is (= [{:owner "Unknown"
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
                          :id judgement-1-id
                          :severity 100
                          :confidence "Low"
                          :owner "Unknown"}
                 :id judgement-1-id
                 :type "CreatedModel"}
                {:owner "Unknown"
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
                          :id judgement-2-id
                          :severity 100
                          :confidence "Low"
                          :owner "Unknown"}
                 :id judgement-2-id
                 :type "CreatedModel"}
                {:owner "Unknown"
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
                          :id judgement-3-id
                          :severity 100
                          :confidence "Low"
                          :owner "Unknown"}
                 :id judgement-3-id
                 :type "CreatedModel"}]
               (->> (document/search-docs conn
                                          index
                                          "event"
                                          nil
                                          {:sort_by "timestamp"
                                           :sort_order "asc"
                                           :query {"match_all" {}}})
                    :data
                    (map #(dissoc % :timestamp :http-params))
                    (map #(update % :entity dissoc :created)))))))))
