(ns ctia.http.routes.data-table-test
  (:refer-clojure :exclude [get])
  (:require [clj-momo.test-helpers.core :as mth]
            [clojure.test :refer [is join-fixtures testing use-fixtures]]
            [ctia.domain.entities :refer [schema-version]]
            [ctia.properties :refer [get-http-show]]
            [ctim.domain.id :as id]
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

(deftest-for-each-store test-data-table-routes
  (helpers/set-capabilities! "foouser" "user" all-capabilities)
  (whoami-helpers/set-whoami-response "45c1f5e3f05d0" "foouser" "user")

  (testing "POST /ctia/data-table"
    (let [{status :status
           data-table :parsed-body}
          (post "ctia/data-table"
                :body {:type "data-table"
                       :row_count 1
                       :external_ids ["http://ex.tld/ctia/data-table/data-table-123"
                                      "http://ex.tld/ctia/data-table/data-table-456"]
                       :schema_version c/ctim-schema-version
                       :columns [{:name "Column1"
                                  :type "string"}
                                 {:name "Column2"
                                  :type "string"}]
                       :rows [["foo"] ["bar"]]}
                :headers {"api_key" "45c1f5e3f05d0"})

          data-table-id
          (id/long-id->id (:id data-table))
          data-table-external-ids
          (:external_ids data-table)]
      (is (= 201 status))
      (is (deep=
           {:type "data-table"
            :row_count 1
            :external_ids ["http://ex.tld/ctia/data-table/data-table-123"
                           "http://ex.tld/ctia/data-table/data-table-456"]
            :schema_version c/ctim-schema-version
            :columns [{:name "Column1"
                       :type "string"}
                      {:name "Column2"
                       :type "string"}]
            :rows [["foo"] ["bar"]]}
           (dissoc data-table
                   :id
                   :created
                   :modified)))

      (testing "the data-table ID has correct fields"
        (let [show-props (get-http-show)]
          (is (= (:hostname    data-table-id)      (:hostname    show-props)))
          (is (= (:protocol    data-table-id)      (:protocol    show-props)))
          (is (= (:port        data-table-id)      (:port        show-props)))
          (is (= (:path-prefix data-table-id) (seq (:path-prefix show-props))))))

      (testing "GET /ctia/data-table/:id"
        (let [response (get (str "ctia/data-table/" (:short-id data-table-id))
                            :headers {"api_key" "45c1f5e3f05d0"})
              data-table (:parsed-body response)]
          (is (= 200 (:status response)))
          (is (deep=
               {:id (id/long-id data-table-id)
                :type "data-table"
                :row_count 1
                :external_ids ["http://ex.tld/ctia/data-table/data-table-123"
                               "http://ex.tld/ctia/data-table/data-table-456"]
                :schema_version c/ctim-schema-version
                :columns [{:name "Column1"
                           :type "string"}
                          {:name "Column2"
                           :type "string"}]
                :rows [["foo"] ["bar"]]}
               (dissoc data-table
                       :created
                       :modified)))))

      (testing "GET /ctia/data-table/external_id"
        (let [response (get "ctia/data-table/external_id"
                            :headers {"api_key" "45c1f5e3f05d0"}
                            :query-params {"external_id" (rand-nth data-table-external-ids)})
              data-tables (:parsed-body response)]
          (is (= 200 (:status response)))
          (is (deep=
               [{:id (id/long-id data-table-id)
                 :type "data-table"
                 :row_count 1
                 :external_ids ["http://ex.tld/ctia/data-table/data-table-123"
                                "http://ex.tld/ctia/data-table/data-table-456"]
                 :schema_version c/ctim-schema-version
                 :columns [{:name "Column1"
                            :type "string"}
                           {:name "Column2"
                            :type "string"}]
                 :rows [["foo"] ["bar"]]}]
               (map #(dissoc % :created :modified) data-tables)))))

      (testing "DELETE /ctia/data-table/:id"
        (let [response (delete (str "ctia/data-table/" (:short-id data-table-id))
                               :headers {"api_key" "45c1f5e3f05d0"})]
          (is (= 204 (:status response)))
          (let [response (get (str "ctia/data-table/" (:short-id data-table-id))
                              :headers {"api_key" "45c1f5e3f05d0"})]
            (is (= 404 (:status response)))))))))
