(ns ctia.http.generative.fulltext-search
  (:require
   [clojure.spec.alpha :as spec]
   [clojure.test :refer [deftest testing is use-fixtures join-fixtures]]
   [clojure.test.check.generators :as gen]
   [ctia.auth.capabilities :as capabilities]
   [ctia.auth.threatgrid :refer [map->Identity]]
   [ctia.bundle.core :as bundle]
   [ctia.entity.entities :as entities]
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

(defn- bundle-gen-for
  "Generator for a bundle that contains only given entity key(s)"
  [& entity-keys]
  (gen/fmap
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
       ;; let's remove :id from every entity
       ;; so we can import the Bundle
       (reduce
        (fn [bundle ent-key]
          (assoc bundle ent-key
                 (apply utils/update-items
                        (vec (get bundle ent-key))
                        (repeat #(dissoc % :id)))))
        new-bundle
        entity-keys)))
   (prop/spec-gen "max-new-bundle")))

(defn test-cases []
  [{:query-params {:query_mode "query_string"
                   :query "(title of test incident) AND (Log Review)"
                   :es_query_fields ["title"]} :bundle-gen (gen/fmap
                 (fn [bundle]
                   (update
                    bundle :incidents
                    (fn [incidents]
                      (utils/update-items
                       incidents
                       #(assoc % :discovery_method "Log Review", :title "title of test incident")))))
                 (bundle-gen-for :incidents))
    :check (fn [_ res]
             (is (nil?
                  (->> res
                       :parsed-body
                       (some #(and (-> % :discovery_method (= "Log Review"))
                                   (-> % :title (= "title of test incident"))))))))
    :test-description "Should NOT return anything, because a query field is missing"}

   {:query-params {:query_mode "query_string"
                   :query "(title of test incident) AND (Log Review)"
                   :es_query_fields ["title" "discovery_method"]}
    :bundle-gen (gen/fmap
                 (fn [bundle]
                   (update
                    bundle :incidents
                    (fn [incidents]
                      (utils/update-items
                       incidents
                       #(assoc % :discovery_method "Log Review", :title "title of test incident")))))
                 (bundle-gen-for :incidents))
    :check (fn [_ res]
             (is (->> res
                      :parsed-body
                      (some #(and (-> % :discovery_method (= "Log Review"))
                                  (-> % :title (= "title of test incident")))))))
    :test-description "Should return an entity where multiple fields match"}])

(defn test-search-case
  [app
   {:keys [query-params
           bundle-gen
           check
           test-description]}]
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

    (doseq [plural ent-keys
            :let [entity (ffirst (entities/entity-plural->entity plural))
                  search-res (th.search/search-raw app entity query-params)]]
      (testing test-description (check bundle search-res)))))

(deftest fulltext-search-test
  (es-helpers/for-each-es-version
      "Extended Fullquery search"
      [#_5 7]
    #(es-index/delete! % "ctia_*")
    (helpers/fixture-ctia-with-app
     (fn [app]
       (helpers/set-capabilities! app "foouser" ["foogroup"] "user" (capabilities/all-capabilities))
       (whoami-helpers/set-whoami-response app "45c1f5e3f05d0" "foouser" "foogroup" "user")
       (doseq [test-case (test-cases)]
         (test-search-case app test-case))))))
