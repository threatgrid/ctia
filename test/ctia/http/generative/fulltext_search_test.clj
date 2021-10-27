(ns ctia.http.generative.fulltext-search-test
  (:require
   [clojure.test :refer [deftest testing is are use-fixtures join-fixtures]]
   [clojure.test.check.generators :as gen]
   [ctia.auth.capabilities :as capabilities]
   [ctia.auth.threatgrid :refer [map->Identity]]
   [ctia.bundle.core :as bundle]
   [ctia.http.generative.properties :as prop]
   [ctia.lib.utils :as utils]
   [ctia.store :as store]
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
(defn test-cases []
  (concat
   [{:test-description "Returns all the records when the wildcard used"
     :query-params     {:query "*"}
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
       :query-params     {:query "title:nunc porta vulputate tellus"}
       :bundle-gen       bundle
       :check            check-fn}
      {:test-description "Multiple records with the same value in a given field set in search_fields"
       :query-params     {:query "nunc porta vulputate tellus"
                          :search_fields ["title"]}
       :bundle-gen       bundle
       :check            check-fn}
      {:test-description "Querying for non-existing value should yield no results. Lucene syntax."
       :query-params     {:query "title:0e1c9f6a-c3ac-4fd5-982e-4981f86df07a"}
       :bundle-gen       bundle
       :check            (fn [_ _ _ res] (is (zero? (-> res :parsed-body count))))}
      {:test-description "Querying for non-existing field with wildcard should yield no results. Lucene syntax."
       :query-params     {:query "74f93781-f370-46ea-bd53-3193db379e41:*"}
       :bundle-gen       bundle
       :check            (fn [_ _ _ res] (is (empty? (-> res :parsed-body))))}
      {:test-description "Querying for non-existing field with wildcard should fail the schema validation. search_fields"
       :query-params     {:query "*"
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
       :query-params     {:query "(title of test incident) AND (Log Review)"
                          :search_fields ["title"]}
       :bundle-gen       bundle
       :check            (fn [_ _ _ res] (is (nil? (get-fields res))))}

      {:test-description "Should return an entity where multiple fields match"
       :query-params     {:query "\"title of test incident\" AND \"Log Review\""
                          :search_fields ["title" "discovery_method"]}
       :bundle-gen       bundle
       :check            (fn [_ _ _ res]
                           (is (= 1 (-> res :parsed-body count)))
                           (is (get-fields res)))}])

   [{:test-description "multi_match - looking for the same value in different fields in multiple records"
     :query-params     {:query "bibendum"
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
                           (is (= 3 (count matching)))))}]
   (let [bundle-gen (gen/fmap
                     (fn [bundle]
                       (update
                        bundle :incidents
                        (fn [incidents]
                          (utils/update-items
                           incidents
                           #(assoc % :title "fried eggs eggplant")
                           #(assoc % :title "fried eggs potato")
                           #(assoc % :title "fried eggs frittata")))))
                     (bundle-gen-for :incidents))
         check-fn   (fn [_ _ _ res]
                      (let [matching (->> res :parsed-body)]
                        (is (= 2 (count matching)))
                        (= #{"fried eggs eggplant" "fried eggs potato"}
                           (->> matching (map :title) set))))]
     [{:test-description "simple_query_string"
       :query-params     {:simple_query  "\"fried eggs\" +(eggplant | potato) -frittata"
                          :search_fields ["title"]}
       :bundle-gen       bundle-gen
       :check            check-fn}
      {:test-description "simple_query_string and query_string together"
       :query-params     {:simple_query  "\"fried eggs\" +(eggplant | potato) -frittata"
                          :query         "(fried eggs eggplant) OR (fried eggs potato)"
                          :search_fields ["title"]}
       :bundle-gen       bundle-gen
       :check            check-fn}])

   (let [bundle-gen (->> :incidents
                         bundle-gen-for
                         (gen/fmap
                          (fn [bundle]
                            (update
                             bundle :incidents
                             (fn [incidents]
                               (utils/update-items
                                incidents
                                #(assoc % :title "fried eggs")))))))
         check-fn   (fn [_ _ _ res]
                      (is (seq (get-in res [:parsed-body :errors :search_fields]))))]
     [{:test-description "passing non-existing fields shouldn't be allowed"
       :query-params     {:query "*", :search_fields ["bad-field"]}
       :bundle-gen       bundle-gen
       :check            check-fn}
      {:test-description "passing legit entity, albeit non-searchable fields still not allowed"
       :query-params     {:query "*", :search_fields ["incident_time.discovered"]}
       :bundle-gen       bundle-gen
       :check            check-fn}])

   ;; TODO: Re-enable after solving https://github.com/threatgrid/ctia/pull/1152#pullrequestreview-780638906
   #_(let [expected {:title  "intrusion event 3:19187:7 incident"
                     :source "ngfw_ips_event_service"}
           bundle (gen/fmap
                   (fn [bndl]
                     (update bndl :incidents
                             (fn [items]
                               (utils/update-items items #(merge % expected)))))
                   (bundle-gen-for :incidents))
           check-fn (fn [_ _ _ res]
                      (is (= expected
                             (-> res :parsed-body first (select-keys [:source :title])))))]
       [{:test-description "searching in mixed fields indexed as pure text and keyword"
         :query-params     {:query "the intrusion event 3\\:19187\\:7 incident"
                            :search_fields ["title" "source"]}
         :bundle-gen       bundle
         :check            check-fn}])))

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
      (doseq [test-case (if-let [cases (seq (filter :only (test-cases)))]
                          cases (test-cases))]
        (test-search-case app test-case))))))

;; For the time being (before we fully migrate to ES7), we need to test the behavior
;; of searching in multiple heterogeneous types of fields, i.e., when some fields are
;; mapped to 'text' and others to 'keyword'.
;;
;; Since at the moment we cannot implement a workaround in the API because that would
;; require updating mappings on the live, production data, we'd have to test this by
;; directly sending queries into ES, bypassing the CTIA routes.
(deftest mixed-fields-text-and-keyword-multi-match
  (es-helpers/for-each-es-version
   "Mixed fields text and keyword multimatch"
   [5 7]
   #(es-index/delete! % "ctia_*")
   (helpers/fixture-ctia-with-app
    (fn [app]
      (let [{{:keys [get-store]} :StoreService :as services} (app/service-graph app)

            incidents-store (get-store :incident)

            expected {:title  "intrusion event 3:19187:7 incident"
                      :source "ngfw_ips_event_service"}
            bundle   (->>
                      :incidents
                      bundle-gen-for
                      (gen/fmap
                       (fn [bndl]
                         (update bndl :incidents
                                 (fn [items]
                                   (utils/update-items items #(merge % expected))))))
                      gen/generate)

            _ (bundle/import-bundle
               bundle
               nil    ;; external-key-prefixes
               login services)

            ignore-ks  [:created :groups :id :incident_time :modified :owner :timestamp]]
        (are [desc query check-fn] (let [res (-> incidents-store
                                                 (store/query-string-search
                                                  (merge query {:default_operator "AND"})
                                                  login {})
                                                 :data)]
                                     (testing (str "query: " query)
                                       (is (check-fn res) desc)))
          "base query matches expected data"
          {:full-text [{:query "intrusion event 3\\:19187\\:7 incident"}]}
          (fn [res]
            (and
             (= 1 (count res))
             (-> res first
                 (select-keys (keys expected))
                 (= expected))))

          "querying all, matches generated incidents, minus selected fields in each entity"
          {:full-text [{:query "*"}]}
          (fn [res]
            (let [norm (fn [data] (->> data (map #(apply dissoc % ignore-ks)) set))]
              (= (-> bundle :incidents norm)
                 (-> res norm))))

          "using 'title' and 'source' fields + a stop word"
          {:full-text [{:query "that intrusion event 3\\:19187\\:7 incident"}]
           :fields ["title" "source"]}
          (fn [res]
            (and
             (= 1 (count res))
             (-> res first
                 (select-keys (keys expected))
                 (= expected))))

          "using double-quotes at the end of the query"
          {:full-text [{:query "intrusion event 3\\:19187\\:7 incident \"\""}]
           :fields ["title" "source"]}
          (fn [res]
            (and
             (= 1 (count res))
             (-> res first
                 (select-keys (keys expected))
                 (= expected))))))))))
