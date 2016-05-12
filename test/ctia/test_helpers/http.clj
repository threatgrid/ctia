(ns ctia.test-helpers.http
  (:refer-clojure :exclude [get])
  (:require [clojure.tools.logging :as log]
            [ctia.test-helpers.core :refer [delete get post put] :as helpers]
            [clojure.test :refer [is testing]]
            ))

(def api-key "45c1f5e3f05d0")

(defn test-post
  "Like a simplified POST request but with testing points.
  It uses the standard `api-key`.
  It verify that the POST was successful and that the returned entity
  correpond the the one sent.
  In the end the test-post returns only the entity (parsed body)."
  [path new-entity]
  (let [resp (post path :body new-entity :headers {"api_key" api-key})]
    (when (get-in resp [:parsed-body :message])
      (log/error (get-in resp [:parsed-body :message])))
    (when (get-in resp [:parsed-body :errors])
      (log/error (get-in resp [:parsed-body :errors])))
    (is (= 200 (:status resp)))
    (when (= 200 (:status resp))
      (is (= new-entity (dissoc (:parsed-body resp) :id :created :modified :owner :entity_id)))
      (:parsed-body resp))))

(defn test-get
  "Helper which test a get request occurs with success and return the right object
  Returns the result of the GET call."
  [path expected-entity]
  (testing (str "GET " path)
    (let [resp (get path :headers {"api_key" api-key})]
      (when (get-in resp [:parsed-body :message])
        (log/error (get-in resp [:parsed-body :message])))
      (when (get-in resp [:parsed-body :errors])
        (log/error (get-in resp [:parsed-body :errors])))
      (is (= 200 (:status resp)))
      (when (= 200 (:status resp))
        (is (= expected-entity
               (:parsed-body resp)))
        (:parsed-body resp)))))

(defn test-get-list
  "Helper which test a get request occurs with success and returns
  the same list of objects in any order.
  Returns the result of the GET call."
  [path expected-entities]
  (testing (str "GET " path)
    (let [resp (get path :headers {"api_key" api-key})]
      (when (get-in resp [:parsed-body :message])
        (log/error (get-in resp [:parsed-body :message])))
      (when (get-in resp [:parsed-body :errors])
        (log/error (get-in resp [:parsed-body :errors])))
      (is (= 200 (:status resp)))
      (when (= 200 (:status resp))
        (is (= (set expected-entities)
               (set (:parsed-body resp))))
        (:parsed-body resp)))))

(defn test-delete
  "Helper which test a delete request occurs with success"
  [path]
  (testing (str "DELETE " path)
    (let [resp (delete path :headers {"api_key" api-key})]
      (is (= 204 (:status resp)))
      (= 204 (:status resp)))))
