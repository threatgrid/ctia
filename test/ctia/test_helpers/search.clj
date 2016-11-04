(ns ctia.test-helpers.search
  (:refer-clojure :exclude [get])
  (:require [clojure.test :refer [is testing]]
            [ctia.test-helpers
             [core :as helpers :refer [delete get post put]]]))

(defmacro test-query-string-search [entity query query-field]
  `(testing (str "GET /ctia/" (name ~entity) "/search")
     ;; only when ES store
     (when (= "es" (get-in @ctia.properties/properties [:ctia :store ~entity]))
       (let [query-url# (str "ctia/" (name ~entity) "/search")]
         
         (let [response# (get query-url#
                              :headers {"api_key" "45c1f5e3f05d0"}
                              :query-params {"query" ~query})]
           (is (= 200 (:status response#)))
           (is (= ~query (first (map ~query-field (:parsed-body response#))))
               "query term works"))
         
         (let [response# (get query-url#
                              :headers {"api_key" "45c1f5e3f05d0"}
                              :query-params {"query" ~query
                                             "tlp" "red"})]
           (is (= 200 (:status response#)))
           (is (empty? (:parsed-body response#))
               "filters are applied, and discriminate"))
         
         (let [response# (get query-url#
                              :headers {"api_key" "45c1f5e3f05d0"}
                              :query-params {"query" ~query
                                             "tlp" "green"})]
           (is (= 200 (:status response#)))
           (is (= 1  (count (:parsed-body response#)))
               "filters are applied, and match properly"))))))
