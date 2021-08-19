(ns ctia.http.generative.fulltext-search-test
  (:require
   [clojure.test :refer [deftest testing is use-fixtures join-fixtures]]
   [clojure.test.check.generators :as gen]
   [ctia.auth.capabilities :as capabilities]
   [ctia.auth.threatgrid :refer [map->Identity]]
   [ctia.lib.utils :as utils]
   [ctia.test-helpers.core :as helpers]
   [ctia.test-helpers.es :as es-helpers]
   [ctia.test-helpers.fake-whoami-service :as whoami-helpers]
   [ctia.test-helpers.fixtures :as fixt]
   [ctia.test-helpers.search :as th.search]
   [ductile.index :as es-index]
   [puppetlabs.trapperkeeper.app :as app]))

(def ^:private login
  (map->Identity {:login  "foouser"
                  :groups ["foogroup"]}))

(use-fixtures :once (join-fixtures [es-helpers/fixture-properties:es-store
                                    whoami-helpers/fixture-server]))
(defn- bulk-gen-for
  "Generator for a bulk of entities that contains only given entity keys
  Example: (gen/generate (bulk-gen-for :assets :actors))"
  [& entity-keys]
  (let [examples (->> (-> (fixt/bundle 10 :maximal)
                          (select-keys entity-keys))
                      (reduce-kv
                       (fn [a k v]
                         ;; remove IDs
                         (assoc a k (apply utils/update-items v (repeat #(dissoc % :id)))))
                       {}))]
    (gen/return examples)))

;; Every single query gets tested with its own set of generated Bulk data.
;; These tests are not meant to test relations between entitites, that is why
;; we're not using Bundle, but Bulk.
;;
;; After the query gets sent, the response results
;; are passed into :check function, together with the test-case map, entity key
;; and the original Bulk data
(defn test-cases
  "Returns vector of test cases, where each map represents:

  - a recipe for generating data
  - a query
  - a way to verify the response data

  Test checker function (that would use this) is responsible for generating and
  importing the data into the ES cluster, and then query and verify the
  results"
  []
  (concat
   [{:test-description "Returns all the records when the wildcard used"
     :query-params     {:query_mode "query_string"
                        :query      "*"}
     :bulk-gen         (bulk-gen-for :incidents :assets)
     :check            (fn [_ entity examples res]
                         (is (= (-> res :parsed-body count)
                                (-> examples (get entity) count))))}]
   (let [bulk     (gen/fmap
                   (fn [examples]
                     (update
                      examples :incidents
                      (fn [incidents]
                        (apply
                         utils/update-items incidents
                         ;; update first 3 records, leave the rest unchanged
                         (repeat 3 #(assoc % :title "nunc porta vulputate tellus"))))))
                   (bulk-gen-for :incidents))
         check-fn (fn [{:keys [test-description]} _ _ res]
                    (let [matching (->> res
                                        :parsed-body
                                        (filter #(-> % :title (= "nunc porta vulputate tellus"))))]
                      (is (= 3 (count matching)) test-description)))]
     [{:test-description "Multiple records with the same value in a given field. Lucene syntax"
       :query-params     {:query_mode "query_string"
                          :query      "title:nunc porta vulputate tellus"}
       :bulk-gen         bulk
       :check            check-fn}
      {:test-description "Multiple records with the same value in a given field set in search_fields"
       :query-params     {:query_mode    "query_string"
                          :query         "nunc porta vulputate tellus"
                          :search_fields ["title"]}
       :bulk-gen         bulk
       :check            check-fn}
      {:test-description "Querying for non-existing value should yield no results. Lucene syntax."
       :query-params     {:query_mode "query_string"
                          :query      "title:0e1c9f6a-c3ac-4fd5-982e-4981f86df07a"}
       :bulk-gen         bulk
       :check            (fn [_ _ _ res] (is (zero? (-> res :parsed-body count))))}
      {:test-description "Querying for non-existing field with wildcard should yield no results. Lucene syntax."
       :query-params     {:query_mode "query_string"
                          :query      "74f93781-f370-46ea-bd53-3193db379e41:*"}
       :bulk-gen         bulk
       :check            (fn [_ _ _ res] (is (empty? (-> res :parsed-body))))}
      {:test-description "Querying for non-existing field with wildcard should fail the schema validation. search_fields"
       :query-params     {:query_mode    "query_string"
                          :query         "*"
                          :search_fields ["512b8dce-0423-4e9a-aa63-d3c3b91eb8d8"]}
       :bulk-gen         bulk
       :check            (fn [_ _ _ res] (is (= 400 (-> res :status))))}])

   ;; Test `AND` (set two different fields that contain the same word in the same entity)
   (let [bulk       (gen/fmap
                     (fn [examples]
                       (update
                        examples :incidents
                        (fn [incidents]
                          (utils/update-items
                           incidents
                           ;; update only the first, leave the rest unchanged
                           #(assoc % :discovery_method "Log Review"
                                   :title "title of test incident")))))
                     (bulk-gen-for :incidents))
         get-fields (fn [res]
                      (->> res
                           :parsed-body
                           (some #(and (-> % :discovery_method (= "Log Review"))
                                       (-> % :title (= "title of test incident"))))))]
     [{:test-description (str "Should NOT return anything, because query field is missing."
                              "Asking for multiple things in the query, but not providing all the fields.")
       :query-params     {:query_mode    "query_string"
                          :query         "(title of test incident) AND (Log Review)"
                          :search_fields ["title"]}
       :bulk-gen         bulk
       :check            (fn [_ _ _ res] (is (nil? (get-fields res))))}

      {:test-description "Should return an entity where multiple fields match"
       :query-params     {:query_mode    "query_string"
                          :query         "\"title of test incident\" AND \"Log Review\""
                          :search_fields ["title" "discovery_method"]}
       :bulk-gen         bulk
       :check            (fn [_ _ _ res]
                           (is (= 1 (-> res :parsed-body count)))
                           (is (get-fields res)))}])

   [{:test-description "multi_match - looking for the same value in different fields in multiple records"
     :query-params     {:query_mode    "multi_match"
                        :query         "bibendum"
                        :search_fields ["assignees" "title"]}
     :bulk-gen         (gen/fmap
                        (fn [examples]
                          (update
                           examples :incidents
                           (fn [incidents]
                             (apply
                              utils/update-items incidents
                              (repeat 3 ;; update first 3, leave the rest unchanged
                                      #(assoc % :title "Etiam vel neque bibendum dignissim"
                                              :assignees ["bibendum"]))))))
                        (bulk-gen-for :incidents))
     :check            (fn [_ _ _ res]
                         (let [matching (->> res
                                             :parsed-body
                                             (filter #(and (-> % :assignees (= ["bibendum"]))
                                                           (-> % :title (= "Etiam vel neque bibendum dignissim")))))]
                           (is (= 3 (count matching)))))}]

   [{:test-description "searching for non-existing fields, returns errors"
     :query-params     {:query         "*"
                        :search_fields ["donec_retium_posuere_tellus"
                                        "proin_neque_massa"]}
     :bulk-gen         (bulk-gen-for :incidents)
     :check            (fn [_ _ _ res]
                         (is (= 400 (:status res))))}
    {:test-description "searching for non-existing values, returns nothing"
     :query-params     {:query "nullam tempus nulla posuere pellentesque dapibus suscipit ligula"}
     :bulk-gen         (bulk-gen-for :incidents)
     :check            (fn [_ _ _ res]
                         (is (-> res :parsed-body empty?)))}]
   [(let [bulk (gen/fmap (fn [examples]
                           (update examples :assets
                                   (fn [incidents]
                                     (apply utils/update-items incidents
                                            [;; update only the first record, leave the rest unchanged
                                             #(assoc % :short_description "first incident"
                                                     :title "Lorem Ipsum Test Incident"
                                                     :tlp "white")]))))
                         (bulk-gen-for :assets))]
      {:test-description "searching within fields that don't contain the pattern, shall not return any results"
       :query-params     {:query         "first Ipsum"
                          :search_fields ["description"]}
       :bulk-gen         bulk
       :check            (fn [_ _ _ res] (is (-> res :parsed-body empty?)))}

      {:test-description "searching in default fields that contain the pattern, yields results"
       :query-params     {:query "\"first\" \"Ipsum\""}
       :bulk-gen         bulk
       :check            (fn [_ _ _ unparsed]
                           (let [res (-> unparsed :parsed-body)]
                             (is (= 1 (count res)))
                             (is "first incident" (-> res first :short_description))
                             (is "Lorem Ipsum Test Incident" (-> res first :title))))}
      {:test-description (str "searching in non-default search fields that "
                              "contain the pattern, shall not return any results")
       :query-params     {:query "white"}
       :bulk-gen         bulk
       :check            (fn [_ _ _ res] (is (-> res :parsed-body empty?)))}

      {:test-description "searching in a nested default field, yields results"
       :query-params     {:query "\"lacinia purus\""}
       :bulk-gen         (gen/fmap (fn [examples]
                                     (update examples :sightings
                                             (fn [sightings]
                                               (apply utils/update-items sightings
                                                      [;; update only the first record, leave the rest unchanged
                                                       #(assoc-in % [:observables 0 :value] "lacinia purus")]))))
                                   (bulk-gen-for :sightings))
       :check            (fn [_ _ _ unparsed]
                           (let [res (-> unparsed :parsed-body)]
                            (is (= 1 (count res)))
                            (is (-> res first :observables first :value (= "lacinia purus")))))})]))

(defn test-search-case
  [app test-case]
  (let [{:keys [query-params
                bulk-gen
                check
                test-description]} test-case
        examples (gen/generate bulk-gen)
        ent-keys (keys examples)]
    (helpers/POST-bulk app examples)
    (doseq [plural ent-keys]
      (let [entity     (ffirst (helpers/plural-key->entity plural))
            search-res (th.search/search-raw app entity query-params)]
        (testing test-description (check test-case plural examples search-res))
        (th.search/delete-search app entity {:query "*"
                                             :REALLY_DELETE_ALL_THESE_ENTITIES true})))))

(deftest fulltext-search-test
  (es-helpers/for-each-es-version
   "Extended Fulltext query search"
   [5 7]
   #(es-index/delete! % "ctia_*")
   (helpers/fixture-ctia-with-app
    (fn [app]
      (helpers/set-capabilities! app "foouser" ["foogroup"] "user" (capabilities/all-capabilities))
      (whoami-helpers/set-whoami-response app "45c1f5e3f05d0" "foouser" "foogroup" "user")
      (doseq [test-case (test-cases)]
        (test-search-case app test-case))))))
