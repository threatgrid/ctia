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

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Every single query gets tested with its own set of generated Bulk data.       ;;
;; These tests are not meant to test relations between entitites, that is why    ;;
;; we're not using Bundle, but Bulk.                                             ;;
;;                                                                               ;;
;; After query gets sent, :check fn executed with the following argument map:    ;;
;; :test-case  - original test-case map                                          ;;
;; :entity     - entity key                                                      ;;
;; :in         - original imported data (list of records for the current entity) ;;
;; :out        - the results of the posted http query                            ;;
;; :es-version - version of Elastic Search                                       ;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn test-cases
  "Returns a vector of test cases, where each map represents:

  - a recipe for generating data
  - a query
  - a way to verify the response data

  Test checker function (that would use this) should generate and import the
  data into the ES cluster, and then send an http request, and finally verify
  the results of that request by executing :check function."
  []
  (concat
   [{:test-description "Returns all the records when the wildcard used"
     :query-params {:query_mode "query_string"
                    :query "*"}
     :data-gen (bulk-gen-for :incidents :assets)
     :check (fn [{:keys [in out]}]
              (is (= (-> out :parsed-body count)
                     (-> in count))))}]
   (let [bulk (gen/fmap
               (fn [examples]
                 (update
                  examples :incidents
                  (fn [incidents]
                    (apply
                     utils/update-items incidents
                     ;; update first 3 records, leave the rest unchanged
                     (repeat 3 #(assoc % :title "nunc porta vulputate tellus"))))))
               (bulk-gen-for :incidents))
         check-fn (fn [{:keys [test-case out]}]
                    (let [match (->> out
                                     :parsed-body
                                     (filter #(-> % :title
                                                  (= "nunc porta vulputate tellus"))))]
                      (is (= 3 (count match))
                          (:test-description test-case))))]
     [{:test-description (str "Multiple records with the same value in "
                              "a given field. Lucene syntax")
       :query-params {:query_mode "query_string"
                      :query "title:nunc porta vulputate tellus"}
       :data-gen bulk
       :check check-fn}
      {:test-description (str "Multiple records with the same value in a "
                              "given field set in search_fields")
       :query-params {:query_mode "query_string"
                      :query "nunc porta vulputate tellus"
                      :search_fields ["title"]}
       :data-gen bulk
       :check check-fn}
      {:test-description (str "Querying for non-existing value should "
                              "yield no results. Lucene syntax.")
       :query-params {:query_mode "query_string"
                      :query "title:0e1c9f6a-c3ac-4fd5-982e-4981f86df07a"}
       :data-gen bulk
       :check (fn [{:keys [out]}] (is (-> out :parsed-body count zero?)))}
      {:test-description (str "Querying for non-existing field with wildcard "
                              "should yield no results. Lucene syntax.")
       :query-params {:query_mode "query_string"
                      :query "74f93781-f370-46ea-bd53-3193db379e41:*"}
       :data-gen bulk
       :check (fn [{:keys [out]}] (is (-> out :parsed-body empty?)))}
      {:test-description (str "Querying for non-existing field with wildcard "
                              "should fail the schema validation.")
       :query-params {:query_mode "query_string"
                      :query "*"
                      :search_fields ["512b8dce-0423-4e9a-aa63-d3c3b91eb8d8"]}
       :data-gen bulk
       :check (fn [{:keys [out]}] (is (= 400 (-> out :status))))}])

   ;; Test `AND` (set two different fields that contain the same word in the same entity)
   (let [bulk (gen/fmap
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
         get-fields (fn [out]
                      (->> out :parsed-body
                           (some #(and (-> % :discovery_method (= "Log Review"))
                                       (-> % :title (= "title of test incident"))))))]
     [{:test-description (str "Should NOT return anything, because query field is missing. "
                              "Asking for multiple things in the query, but not providing all the fields.")
       :query-params {:query_mode "query_string"
                      :query "(title of test incident) AND (Log Review)"
                      :search_fields ["title"]}
       :data-gen bulk
       :check (fn [{:keys [out]}] (is (nil? (get-fields out))))}

      {:test-description "Should return an entity where multiple fields match"
       :query-params {:query_mode "query_string"
                      :query "\"title of test incident\" AND \"Log Review\""
                      :search_fields ["title" "discovery_method"]}
       :data-gen bulk
       :check (fn [{:keys [out]}]
                (is (= 1 (-> out :parsed-body count)))
                (is (get-fields out)))}])

   [{:test-description (str "multi_match - looking for the same value in "
                            "different fields in multiple records")
     :query-params {:query_mode "multi_match"
                    :query "bibendum"
                    :search_fields ["assignees" "title"]}
     :data-gen (gen/fmap
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
     :check (fn [{:keys [out]}]
              (let [matching (->> out
                                  :parsed-body
                                  (filter #(and (-> % :assignees (= ["bibendum"]))
                                                (-> % :title (= "Etiam vel neque bibendum dignissim")))))]
                (is (= 3 (count matching)))))}]

   [{:test-description "searching for non-existing fields, returns errors"
     :query-params {:query "*"
                    :search_fields ["donec_retium_posuere_tellus"
                                    "proin_neque_massa"]}
     :data-gen (bulk-gen-for :incidents)
     :check (fn [{:keys [out]}]
              (is (= 400 (:status out))))}
    {:test-description "searching for non-existing values, returns nothing"
     :query-params {:query "nullam tempus nulla posuere pellentesque dapibus suscipit ligula"}
     :data-gen (bulk-gen-for :incidents)
     :check (fn [{:keys [out]}]
              (is (-> out :parsed-body empty?)))}]
   [(let [bulk (gen/fmap (fn [examples]
                           (update examples :assets
                                   (fn [incidents]
                                     (apply utils/update-items incidents
                                            [;; update only the first record, leave the rest unchanged
                                             #(assoc % :short_description "first incident"
                                                     :title "Lorem Ipsum Test Incident"
                                                     :tlp "white")]))))
                         (bulk-gen-for :assets))]
      {:test-description (str "searching within fields that don't contain the "
                              "pattern, shall not return any results")
       :query-params {:query "first Ipsum"
                      :search_fields ["description"]}
       :data-gen bulk
       :check (fn [{:keys [out]}] (is (-> out :parsed-body empty?)))}

      {:test-description "searching in default fields that contain the pattern, yields results"
       :query-params {:query "\"first\" \"Ipsum\""}
       :data-gen bulk
       :check (fn [{:keys [out]}]
                (let [res (-> out :parsed-body)]
                  (is (= 1 (count res)))
                  (is "first incident" (-> res first :short_description))
                  (is "Lorem Ipsum Test Incident" (-> res first :title))))}
      {:test-description (str "searching in non-default search fields that "
                              "contain the pattern, shall not return any results")
       :query-params {:query "white"}
       :data-gen bulk
       :check (fn [{:keys [out]}] (is (-> out :parsed-body empty?)))}

      {:test-description "searching in a nested default field, yields results"
       :query-params {:query "\"lacinia purus\""}
       :data-gen (gen/fmap (fn [examples]
                             (update examples :sightings
                                     (fn [sightings]
                                       (apply utils/update-items sightings
                                              [;; update only the first record, leave the rest unchanged
                                               #(assoc-in % [:observables 0 :value] "lacinia purus")]))))
                           (bulk-gen-for :sightings))
       :check (fn [{:keys [out]}]
                (let [res (-> out :parsed-body)]
                  (is (= 1 (count res)))
                  (is (-> res first :observables first :value (= "lacinia purus")))))})]

   [{:test-description (str "split_on_whitespace deprecated in ES6, "
                            "query expected to work differently in 5 and 7")
     :query-params {:query "the intrusion event 3\\:19187\\:7 incident"}
     :data-gen (gen/fmap (fn [examples]
                           (update examples :incidents
                                   (fn [entities]
                                     (apply utils/update-items entities
                                            [;; update only the first record, leave the rest unchanged
                                             #(assoc % :title "Intrusion event 3:19187:7 incident"
                                                     :source "ngfw_ips_event_service")]))))
                         (bulk-gen-for :incidents))
     :check (fn [{:keys [in out es-version]}]
              (let [res (-> out :parsed-body)]
                (case es-version
                  5 (is (zero? (count res)))

                  7 (do
                      (is (seq res))
                      (is (= (-> res first (dissoc :id :groups :owner))
                             (-> in first (dissoc :id :groups :owner))))))))}]))

(defn test-search-case
  [app test-case & {:keys [es-version]}]
  (let [{:keys [test-description
                data-gen
                query-params
                check]} test-case
        examples        (gen/generate data-gen)
        ent-keys        (keys examples)]
    (helpers/POST-bulk app examples)
    (doseq [plural ent-keys]
      (let [entity     (ffirst (helpers/plural-key->entity plural))
            search-res (th.search/search-raw app entity query-params)]
        (testing test-description
          (check
           {:test-case  test-case
            :entity     entity
            :in         (get examples plural)
            :out        search-res
            :es-version es-version}))
        ;; cleanup
        (th.search/delete-search
         app entity
         {:query "*"
          :REALLY_DELETE_ALL_THESE_ENTITIES true})))))

(deftest fulltext-search-test
  (es-helpers/for-each-es-version
   "Extended Fulltext query search"
   [5 7]
   #(es-index/delete! % "ctia_*")
   (helpers/fixture-ctia-with-app
    (fn [app]
      (helpers/set-capabilities!
       app "foouser" ["foogroup"] "user" (capabilities/all-capabilities))
      (whoami-helpers/set-whoami-response
       app "45c1f5e3f05d0" "foouser" "foogroup" "user")
      (doseq [test-case (test-cases)]
        (test-search-case
         app
         test-case
         ;; `version` is an anaphoric var interned by `for-each-version` macro
         ;; please disregard concerning linter errors
         :es-version version))))))
