(ns ctia.http.routes.package-test
  (:refer-clojure :exclude [get])
  (:require [clj-momo.test-helpers.core :as mth]
            [clojure.test :refer [is join-fixtures testing use-fixtures]]
            [ctia.domain.entities :refer [schema-version]]
            [ctim.schemas.common :as c]
            [ctia.test-helpers
             [auth :refer [all-capabilities]]
             [core :as helpers :refer [delete get post put]]
             [fake-whoami-service :as whoami-helpers]
             [store :refer [deftest-for-each-store]]]))

(use-fixtures :once (join-fixtures [mth/fixture-schema-validation
                                    helpers/fixture-properties:clean
                                    whoami-helpers/fixture-server]))

(use-fixtures :each whoami-helpers/fixture-reset-state)

(deftest-for-each-store test-package-routes
  (helpers/set-capabilities! "foouser" "user" all-capabilities)
  (whoami-helpers/set-whoami-response "45c1f5e3f05d0" "foouser" "user")

  (testing "POST /ctia/package"
    (let [indicator {:id "https://tenzin/indicator/indicator-srizbi-domain-list"
                     :created #inst "2017-05-11T00:40:48.212-00:00"
                     :type "indicator"
                     :title "Srizbi Botnet Domains"
                     :description "A list of domains associated with the Srizbi botnet"
                     :source "threatgrid"
                     :valid_time {:start_time #inst "2016-07-01"
                                  :end_time #inst "2016-08-01"}
                     :producer "threatgrid"
                     :schema_version schema-version
                     :likely_impact "Compomised Host, member of Botnet"
                     :owner "testuser"
                     :indicator_type ["Domain Watchlist"]
                     :indicated_TTP [{:ttp_id "https://tenzin/ttp/ttp-srizbi"}]}

          ttp {:id "https://tenzin/ttp/ttp-metasploit"
               :created #inst "2017-05-11T00:40:48.212-00:00"
               :owner "testuser"
               :type "ttp"
               :ttp_type "test"
               :title "Metasploit"
               :indicators [{:indicator_id "indicator-srizbi-domain-list"}]
               :description "Metasploit is a popular and venerable penetration testing tool, which provides point and click scanning of targets, identification of vulnerabilities, crafting of exploits, and custom payloads."
               :schema_version schema-version
               :valid_time {:start_time #inst "2016-07-01"
                            :end_time #inst "2016-08-01"}
               :resources {:tools [{:description "some description"
                                    :type ["Penetration Testing"]
                                    :references ["https://www.metasploit.com"]
                                    :vendor "Rapid7"}]}
               :source "threatgrid"}
          judgement {:type "judgement"
                     :id "http://tenzin/judgement/judgement-file-faf0F"
                     :created #inst "2016-07-01"
                     :source "threatgrid"
                     :schema_version schema-version
                     :disposition 3
                     :disposition_name "Malicious"
                     :severity 80
                     :confidence "High"
                     :owner "testuser"
                     :priority 90
                     :observable {:type "sha256"
                                  :value "FA0F..."}
                     :valid_time {:start_time #inst "2016-07-01"}}

          actor-refs  (repeat 10 "http://tenzin/actor/actor-1234")
          verdict-refs (repeat 10 "http://tenzin/verdict/verdict-1234")
          indicators (repeat 10 indicator)
          judgements (repeat 10 judgement)
          ttps (repeat 10 ttp)
          new-package {:valid_time {:start_time #inst "2016-02-11T00:40:48.212-00:00"
                                    :end_time #inst "2016-07-11T00:40:48.212-00:00"}
                       :ttps ttps
                       :source "iroh"
                       :schema_version schema-version
                       :type "package"
                       :judgements judgements
                       :indicators indicators
                       :actor_refs actor-refs
                       :verdict_refs verdict-refs}

          response (post "ctia/package"
                         :body new-package
                         :headers {"api_key" "45c1f5e3f05d0"})
          package (:parsed-body response)]

      (is (= 201 (:status response)))
      (is (deep=
           new-package
           (dissoc package
                   :id
                   :created
                   :modified
                   :tlp
                   :owner)))

      (testing "GET /ctia/package/:id"
        (let [response (get (str "ctia/package/" (:id package))
                            :headers {"api_key" "45c1f5e3f05d0"})
              package (:parsed-body response)]
          (is (= 200 (:status response)))
          (is (deep=
               new-package
               (dissoc package
                       :id
                       :created
                       :modified
                       :tlp
                       :owner)))))

      (testing "DELETE /ctia/package/:id"
        (let [response (delete (str "ctia/package/" (:id package))
                               :headers {"api_key" "45c1f5e3f05d0"})]
          (is (= 204 (:status response)))
          (let [response (get (str "ctia/package/" (:id package))
                              :headers {"api_key" "45c1f5e3f05d0"})]
            (is (= 404 (:status response)))))))))
