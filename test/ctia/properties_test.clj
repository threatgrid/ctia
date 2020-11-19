(ns ctia.properties-test
  (:require [ctia.properties :as sut]
            [clojure.test :refer [deftest is testing]]
            [ductile.schemas :refer [Refresh]]
            [schema.core :as s]))

(deftest es-store-impl-properties-test
  (testing "Stores ES properties are generated according to given prefix and entity name."
    (is (= {"ctia.store.es.malware.host" s/Str
            "ctia.store.es.malware.port" s/Int
            "ctia.store.es.malware.protocol" s/Keyword
            "ctia.store.es.malware.clustername" s/Str
            "ctia.store.es.malware.indexname" s/Str
            "ctia.store.es.malware.refresh" Refresh
            "ctia.store.es.malware.refresh_interval"  s/Str
            "ctia.store.es.malware.replicas" s/Num
            "ctia.store.es.malware.shards" s/Num
            "ctia.store.es.malware.rollover.max_docs" s/Num
            "ctia.store.es.malware.rollover.max_age" s/Str
            "ctia.store.es.malware.aliased"  s/Bool
            "ctia.store.es.malware.default_operator" (s/enum "OR" "AND")
            "ctia.store.es.malware.version" s/Num
            "ctia.store.es.malware.update-mappings" s/Bool
            "ctia.store.es.malware.update-settings" s/Bool
            "ctia.store.es.malware.timeout" s/Num}
           (sut/es-store-impl-properties "ctia.store.es." "malware")))

    (is (= {"prefix.sighting.host" s/Str
            "prefix.sighting.port" s/Int
            "prefix.sighting.protocol" s/Keyword
            "prefix.sighting.clustername" s/Str
            "prefix.sighting.indexname" s/Str
            "prefix.sighting.refresh" Refresh
            "prefix.sighting.refresh_interval"  s/Str
            "prefix.sighting.replicas" s/Num
            "prefix.sighting.shards" s/Num
            "prefix.sighting.rollover.max_docs" s/Num
            "prefix.sighting.rollover.max_age" s/Str
            "prefix.sighting.aliased"  s/Bool
            "prefix.sighting.default_operator" (s/enum "OR" "AND")
            "prefix.sighting.version" s/Num
            "prefix.sighting.update-mappings" s/Bool
            "prefix.sighting.update-settings" s/Bool
            "prefix.sighting.timeout" s/Num}
           (sut/es-store-impl-properties "prefix." "sighting")))))
