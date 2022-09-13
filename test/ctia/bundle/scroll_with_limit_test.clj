(ns ctia.bundle.scroll-with-limit-test
  (:require [clojure.test :refer [deftest is]]
            [ctia.store :refer [IStore]]
            [ctia.bundle.core :as sut]))

(deftest scroll-with-limit-test
  (let [fake-params-base {:limit 2
                          :sort ["source_ref" "timestamp" "id"]}
        fake-params->res { ;; 1. First request to ES returns limited amount of entities with the same source_ref
                          ;;    this response should trigger another call to scroll-with-limit
                          ;;    but with search_after parameter pointing to the entity with "next" source_ref
                          fake-params-base
                          {:data [{:source_ref "1"
                                   :timestamp 0
                                   :id "1"}
                                  {:source_ref "1"
                                   :timestamp 1
                                   :id "2"}]
                           :paging {:next {:offset 2
                                           :search_after ["1" 1 "2"]}}}
                          ;; 2. Second request to ES returns entities belongs to different source_refs
                          ;;    such response used by scroll-with-limit function to identity
                          (assoc fake-params-base
                                 :search_after ["1" -1 ""])
                          {:data [{:source_ref "2"
                                   :timestamp 3
                                   :id "3"}
                                  {:source_ref "3"
                                   :timestamp 4
                                   :id "4"}]
                           :paging {:next {:offset 4
                                           :search_after ["3" 4 "4"]}}}
                          ;; 3. The last request to ES should indicate that there are no more entities to fetch
                          ;;    the loop must stop here
                          (assoc fake-params-base
                                 :search_after ["3" 4 "4"])
                          {:data []}}
        fake-query {:x 42}
        fake-store (reify IStore
                     (list-records [_this query _identity params]
                       (is (= query fake-query) "Query must be the same!")
                       (is (not (contains? params :offset)) "Offset must not be used!")
                       (if-let [res (fake-params->res params)]
                         res
                         (throw (ex-info "Unexpected params" params)))))]
    (#'sut/scroll-with-limit :source_ref
                             fake-store fake-query
                             nil
                             fake-params-base)))
