(ns ctia.stores.es.init-test
  (:require [ctia.stores.es.init :as sut]
            [clojure.test :refer [deftest testing is]]))

(deftest init-store-conn
  (let [props-not-aliased {:entity :sighting
                           :indexname "ctia_sighting"
                           :shards 2
                           :replicas 1
                           :mappings {:a 1 :b 2}
                           :host "localhost"
                           :port 9200}
        {:keys [index props config conn]}
        (sut/init-store-conn props-not-aliased)]
    (is (= index "ctia_sighting"))
    (is (= (:write-alias props) "ctia_sighting"))
    (is (= "http://localhost:9200" (:uri conn)))
    (is (nil? (:aliases config)))
    (is (= 1 (get-in config [:settings :number_of_replicas])))
    (is (= 2 (get-in config [:settings :number_of_shards])))
    (is (= {} (select-keys (:mappings config) [:a :b]))))

  (let [props-aliased {:entity :sighting
                       :indexname "ctia_sighting"
                       :shards 2
                       :replicas 1
                       :mappings {:a 1 :b 2}
                       :host "localhost"
                       :port 9200
                       :aliased true}
        {:keys [index props config conn]}
        (sut/init-store-conn props-aliased)]
    (is (= index "ctia_sighting"))
    (is (= (:write-alias props) "ctia_sighting-write"))
    (is (= "http://localhost:9200" (:uri conn)))
    (is (= #{"ctia_sighting-write" "ctia_sighting"}
           (-> config :aliases keys set)))
    (is (= 1 (get-in config [:settings :number_of_replicas])))
    (is (= 2 (get-in config [:settings :number_of_shards])))
    (is (= {} (select-keys (:mappings config) [:a :b])))))
