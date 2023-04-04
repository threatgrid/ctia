(ns ctia.properties-test
  (:require [ctia.properties :as sut]
            [ductile.schemas :refer [AuthParams Refresh]]
            [clojure.test :refer [are deftest is testing]]
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
            "ctia.store.es.malware.allow_partial_search_results" s/Bool
            "ctia.store.es.malware.version" s/Num
            "ctia.store.es.malware.update-mappings" s/Bool
            "ctia.store.es.malware.update-settings" s/Bool
            "ctia.store.es.malware.refresh-mappings" s/Bool
            "ctia.store.es.malware.default-sort" s/Str
            "ctia.store.es.malware.timeout" s/Num
            "ctia.store.es.malware.auth.type" sut/AuthParamsType
            "ctia.store.es.malware.auth.params.id" s/Str
            "ctia.store.es.malware.auth.params.api-key" s/Str
            "ctia.store.es.malware.auth.params.headers.authorization" s/Str
            "ctia.store.es.malware.auth.params.user" s/Str
            "ctia.store.es.malware.auth.params.pwd" s/Str}
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
            "prefix.sighting.allow_partial_search_results" s/Bool
            "prefix.sighting.version" s/Num
            "prefix.sighting.update-mappings" s/Bool
            "prefix.sighting.update-settings" s/Bool
            "prefix.sighting.refresh-mappings" s/Bool
            "prefix.sighting.default-sort" s/Str
            "prefix.sighting.timeout" s/Num
            "prefix.sighting.auth.type" sut/AuthParamsType
            "prefix.sighting.auth.params.id" s/Str
            "prefix.sighting.auth.params.api-key" s/Str
            "prefix.sighting.auth.params.headers.authorization" s/Str
            "prefix.sighting.auth.params.user" s/Str
            "prefix.sighting.auth.params.pwd" s/Str}
           (sut/es-store-impl-properties "prefix." "sighting")))))
