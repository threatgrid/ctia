(ns ctia.http.generative.fulltext-search-test
  (:require
   [clojure.test :refer [deftest testing is use-fixtures join-fixtures]]
   [clojure.test.check.generators :as gen]
   [ctia.auth.capabilities :as capabilities]
   [ctia.auth.threatgrid :refer [map->Identity]]
   [ctia.bundle.core :as bundle]
   [ctia.http.generative.properties :as prop]
   [ctia.lib.utils :as utils]
   [ctia.test-helpers.core :as helpers]
   [ctia.test-helpers.es :as es-helpers]
   [ctia.test-helpers.fake-whoami-service :as whoami-helpers]
   [ctia.test-helpers.search :as th.search]
   [ctim.examples.bundles :refer [bundle-maximal]]
   [ctim.schemas.bundle :as bundle.schema]
   [ductile.index :as es-index]
   [puppetlabs.trapperkeeper.app :as app]))

(def ^:private login
  (map->Identity {:login  "foouser"
                  :groups ["foogroup"]}))

(use-fixtures :once (join-fixtures [es-helpers/fixture-properties:es-store
                                    whoami-helpers/fixture-server]))

(def bundle-entity-field-names
  "extracted non-essential Bundle keys"
  (->>
   bundle.schema/objects-entries
   (into bundle.schema/references-entries)
   (mapcat #(-> % :key :values))
   (set)))

(defn- entities-gen
  "Generator for a map where each k/v represents entity-type/list of entities (of
  that type)"
  [& entity-keys]
  (let [gens (map
              (fn [e]
                (-> (->> e
                         helpers/plural-key->entity
                         ffirst
                         name
                         (str "max-new-")
                         prop/spec-gen
                         ;; remove IDs so it can be used it in Bundle import
                         (gen/fmap #(dissoc % :id)))
                    (gen/vector 5 11)))
              entity-keys)]
    (gen/bind
     (apply gen/tuple gens)
     (fn [entity-maps]
       (gen/return
        (zipmap entity-keys entity-maps))))))

(defn- bundle-gen-for
  "Generator for a bundle that contains only given entity key(s)
  Example: (gen/generate (bundle-gen-for :assets :actors))"
  [& entity-keys]
  (gen/let [bundle (->
                    bundle-maximal
                    (dissoc :id)
                    gen/return
                    (gen/bind
                     (fn [bundle]
                       ;; To generate a bundle that contains only given entity(ies), we're gonna need
                       ;; to generate a complete bundle and remove all other keys from it
                       (let [bundle-keys-to-remove (apply
                                                    disj bundle-entity-field-names
                                                    entity-keys)
                             new-bundle            (apply
                                                    dissoc
                                                    bundle
                                                    bundle-keys-to-remove)]
                         (gen/return new-bundle)))))
            entities (apply entities-gen entity-keys)]
    (merge bundle entities)))

;; Every single query gets tested with its own set of generated Bundle
;; data. After the query gets sent, the response results are passed into :check
;; function, together with the test-case map, entity key and the Bundle data
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
     :bundle-gen       (bundle-gen-for :incidents :assets)
     :check            (fn [_ entity bundle res]
                         (is (= (-> res :parsed-body count)
                                (-> bundle (get entity) count))))}]
   (let [bundle   (gen/fmap
                   (fn [bundle]
                     (update
                      bundle :incidents
                      (fn [incidents]
                        (apply
                         utils/update-items incidents
                         ;; update first 3 records, leave the rest unchanged
                         (repeat 3 #(assoc % :title "nunc porta vulputate tellus"))))))
                   (bundle-gen-for :incidents))
         check-fn (fn [{:keys [test-description]} _ _ res]
                    (let [matching (->> res
                                        :parsed-body
                                        (filter #(-> % :title (= "nunc porta vulputate tellus"))))]
                      (is (= 3 (count matching)) test-description)))]
     [{:test-description "Multiple records with the same value in a given field. Lucene syntax"
       :query-params     {:query_mode "query_string"
                          :query      "title:nunc porta vulputate tellus"}
       :bundle-gen       bundle
       :check            check-fn}
      {:test-description "Multiple records with the same value in a given field set in search_fields"
       :query-params     {:query_mode      "query_string"
                          :query           "nunc porta vulputate tellus"
                          :search_fields ["title"]}
       :bundle-gen       bundle
       :check            check-fn}
      {:test-description "Querying for non-existing value should yield no results. Lucene syntax."
       :query-params     {:query_mode "query_string"
                          :query      "title:0e1c9f6a-c3ac-4fd5-982e-4981f86df07a"}
       :bundle-gen       bundle
       :check            (fn [_ _ _ res] (is (zero? (-> res :parsed-body count))))}
      {:test-description "Querying for non-existing field with wildcard should yield no results. Lucene syntax."
       :query-params     {:query_mode "query_string"
                          :query      "74f93781-f370-46ea-bd53-3193db379e41:*"}
       :bundle-gen       bundle
       :check            (fn [_ _ _ res] (is (empty? (-> res :parsed-body))))}
      {:test-description "Querying for non-existing field with wildcard should fail the schema validation. search_fields"
       :query-params     {:query_mode      "query_string"
                          :query           "*"
                          :search_fields ["512b8dce-0423-4e9a-aa63-d3c3b91eb8d8"]}
       :bundle-gen       bundle
       :check            (fn [_ _ _ res] (is (= 400 (-> res :status))))}])

   ;; Test `AND` (set two different fields that contain the same word in the same entity)
   (let [bundle        (gen/fmap
                        (fn [bundle]
                          (update
                           bundle :incidents
                           (fn [incidents]
                             (utils/update-items
                              incidents
                              ;; update only the first, leave the rest unchanged
                              #(assoc % :discovery_method "Log Review"
                                      :title "title of test incident")))))
                        (bundle-gen-for :incidents))
         get-fields (fn [res]
                      (->> res
                           :parsed-body
                           (some #(and (-> % :discovery_method (= "Log Review"))
                                       (-> % :title (= "title of test incident"))))))]
     [{:test-description (str "Should NOT return anything, because query field is missing."
                              "Asking for multiple things in the query, but not providing all the fields.")
       :query-params     {:query_mode      "query_string"
                          :query           "(title of test incident) AND (Log Review)"
                          :search_fields ["title"]}
       :bundle-gen       bundle
       :check            (fn [_ _ _ res] (is (nil? (get-fields res))))}

      {:test-description "Should return an entity where multiple fields match"
       :query-params     {:query_mode      "query_string"
                          :query           "\"title of test incident\" AND \"Log Review\""
                          :search_fields ["title" "discovery_method"]}
       :bundle-gen       bundle
       :check            (fn [_ _ _ res]
                           (is (= 1 (-> res :parsed-body count)))
                           (is (get-fields res)))}])

   [{:test-description "multi_match - looking for the same value in different fields in multiple records"
     :query-params     {:query_mode      "multi_match"
                        :query           "bibendum"
                        :search_fields ["assignees" "title"]}
     :bundle-gen       (gen/fmap
                        (fn [bundle]
                          (update
                           bundle :incidents
                           (fn [incidents]
                             (apply
                              utils/update-items incidents
                              (repeat 3 ;; update first 3, leave the rest unchanged
                                      #(assoc % :title "Etiam vel neque bibendum dignissim"
                                              :assignees ["bibendum"]))))))
                        (bundle-gen-for :incidents))
     :check            (fn [_ _ _ res]
                         (let [matching (->> res
                                             :parsed-body
                                             (filter #(and (-> % :assignees (= ["bibendum"]))
                                                           (-> % :title (= "Etiam vel neque bibendum dignissim")))))]
                           (is (= 3 (count matching)))))}]))

(defn test-search-case
  [app
   {:keys [query-params
           bundle-gen
           check
           test-description] :as test-case}]
  (let [services (app/service-graph app)
        bundle   (gen/generate bundle-gen)
        ent-keys (->> bundle
                      keys
                      (filter (partial contains? bundle-entity-field-names)))]
    (bundle/import-bundle
     bundle
     nil    ;; external-key-prefixes
     login
     services)

    (doseq [plural ent-keys]
      (let [entity (ffirst (helpers/plural-key->entity plural))
            search-res (th.search/search-raw app entity query-params)]
       (testing test-description (check test-case plural bundle search-res))
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
