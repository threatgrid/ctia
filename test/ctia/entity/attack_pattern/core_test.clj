(ns ctia.entity.attack-pattern.core-test
  (:require [clojure.test :refer [deftest is testing join-fixtures use-fixtures]]
            [ctia.entity.attack-pattern.core :as sut]
            [ctia.test-helpers.auth :refer [all-capabilities]]
            [ctia.test-helpers.core :as helpers]
            [ctia.test-helpers.fake-whoami-service :as whoami-helpers]
            [ctia.test-helpers.fixtures :as fixtures]
            [java-time.api :as jt]
            [puppetlabs.trapperkeeper.app :as app]
            [schema.test :refer [validate-schemas]]))

(use-fixtures :once (join-fixtures [validate-schemas
                                    whoami-helpers/fixture-server]))

(def auth "45c1f5e3f05d0")
(def user "foouser")
(def group "foogroup")

(def attack-patterns (fixtures/n-examples :attack-pattern 4 true))

(def mitre-attack-pattern-1 (first attack-patterns))
(def mitre-id-1 (get-in mitre-attack-pattern-1 [:external_references 0 :external_id]))

(def mitre-id-2 "T99999")
(def mitre-attack-pattern-2
  (-> attack-patterns
      second
      (assoc-in [:external_references 0 :external_id] mitre-id-2)
      (assoc-in [:external_references 0 :url] (str "https://attack.mitre.org/techniques/" mitre-id-2))))

(def non-mitre-attack-pattern
  (-> attack-patterns
      (nth 2)
      (assoc :external_references [{:source_name "TechTarget"
                                    :description "Dropper definition. TechTarget. 2020-10-31"
                                    :url "https://whatis.techtarget.com/definition/dropper"}])
      (dissoc :kill_chain_phases :x_mitre_data_sources :x_mitre_platforms :x_mitre_contributors)))

(defn compare-attack-patterns [& patterns]
  (letfn [(scrub-keys [pattern] (dissoc pattern :id :created :modified :groups :owner))]
    (apply = (map scrub-keys patterns))))

(deftest mitre-attack-pattern-test
  (helpers/fixture-ctia-with-app
   (fn [app]
     (helpers/set-capabilities! app user [group] "user" all-capabilities)
     (whoami-helpers/set-whoami-response app
                                         auth
                                         user
                                         group
                                         "user")

     (let [services (app/service-graph app)
           identity-map {:login user :groups [group]}

           near-duplicate-of-mitre-attack-pattern-1
           (-> attack-patterns
               (nth 3)
               (assoc :description (:description mitre-attack-pattern-1))
               (assoc-in [:external_references 0 :external_id]
                         (get-in mitre-attack-pattern-1 [:external_references 0 :external_id]))
               (update :timestamp #(-> % jt/instant (jt/adjust jt/plus (jt/days 1)) jt/to-java-date)))

           get-attack-pattern-by-external-id
           (fn [attack-pattern]
             (sut/mitre-attack-pattern services
                                       identity-map
                                       (get-in attack-pattern [:external_references 0 :external_id])))]

       (helpers/POST-bulk app {:attack_patterns [mitre-attack-pattern-1
                                                 mitre-attack-pattern-2
                                                 near-duplicate-of-mitre-attack-pattern-1
                                                 non-mitre-attack-pattern]})

       (testing "unknown mitre ids return nil"
         (is (nil? (sut/mitre-attack-pattern services identity-map "bogus-id"))))

       (testing "exact unique external_id match (mitre-attack-pattern-2) returns corresponding mitre attack record"
         (is (compare-attack-patterns mitre-attack-pattern-2
                                      (get-attack-pattern-by-external-id mitre-attack-pattern-2))))

       (testing "when two mitre attack records have the same external id, the latest is returned"
         (is (compare-attack-patterns near-duplicate-of-mitre-attack-pattern-1
                                      (get-attack-pattern-by-external-id near-duplicate-of-mitre-attack-pattern-1)
                                      (get-attack-pattern-by-external-id mitre-attack-pattern-1))))))))
