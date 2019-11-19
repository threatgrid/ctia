(ns ctia.task.settings-test
  (:require [clojure.test :refer [deftest is testing join-fixtures use-fixtures]]
            [ctia.task.settings :as sut]
            [ctia.stores.es.init :as init]
            [clj-momo.lib.es.index :as es-index]))

(deftest update-store!-test
  (let [initial-props {:entity :malware
                       :indexname "ctia_malware"
                       :host "localhost"
                       :aliased true
                       :port 9200
                       :shards 5
                       :replicas 2
                       :refresh_interval "1s"}
        ;; create index
        {:keys [conn index]} (init/init-es-conn! initial-props)
        new-props (assoc initial-props
                         :shards 4
                         :replicas 1
                         :refresh_interval "2s")
        _ (sut/update-store! new-props)
        {:keys [refresh_interval
                number_of_shards
                number_of_replicas]} (-> (es-index/get conn index)
                                         first
                                         val
                                         :settings
                                        :index)]
    (is (= "5" number_of_shards)
        "the number of shards is a static parameter")
    (testing "dynamic parameters should be updated"
      (is (= "1" number_of_replicas))
      (is (= "2s" refresh_interval)))
    (es-index/delete! conn (str index "*"))))

