(ns ctia.http.routes.observable-test
  (:refer-clojure :exclude [get])
  (:require
   [ring.util.codec :refer [url-encode]]
   [schema-generators.generators :as g]
   [clojure.test.check.generators :as gen]
   [ctia.schemas.common  :refer [Observable]]
   [ctia.schemas.sighting  :refer [NewSighting]]
   [ctia.schemas.indicator  :refer [NewIndicator]]
   [ctia.schemas.judgement  :refer [NewJudgement]]

   [clojure.test :refer [deftest is testing use-fixtures join-fixtures]]
   [ctia.test-helpers.core :refer [delete get post put] :as helpers]
   [ctia.test-helpers.fake-whoami-service :as whoami-helpers]
   [ctia.test-helpers.store :refer [deftest-for-each-store]]
   [ctia.test-helpers.auth :refer [all-capabilities]]))

(use-fixtures :once (join-fixtures [helpers/fixture-schema-validation
                                    helpers/fixture-properties:clean
                                    whoami-helpers/fixture-server]))

(use-fixtures :each whoami-helpers/fixture-reset-state)

(def api-key "45c1f5e3f05d0")
(defn redprintln [& s]
  (print "\u001b[31m")
  (apply println s)
  (print "\u001b[0m"))
(defn test-post
  "Helper which test a post request occurs with success and return the right object"
  [path new-entity]
  (testing (str "POST " path)
    (let [resp (post path :body new-entity :headers {"api_key" api-key})]
      (when (get-in resp [:parsed-body :message])
        (redprintln (get-in resp [:parsed-body :message])))
      (when (get-in resp [:parsed-body :errors])
        (redprintln (get-in resp [:parsed-body :errors])))
      (is (= 200 (:status resp)))
      (when (= 200 (:status resp))
        (is (= (dissoc new-entity :relations)
               (dissoc (:parsed-body resp) :id :created :modified :owner :relations)))
        (:parsed-body resp)))))

(defn test-delete
  "Helper which test a delete request occurs with success"
  [path]
  (testing (str "DELETE " path)
    (let [resp (delete path :headers {"api_key" api-key})]
      (is (= 204 (:status resp)))
      (= 204 (:status resp)))))

(defn test-get-list
  "Helper which test a get request occurs with success and return the right object"
  [path expected-entity]
  (testing (str "GET " path)
    (let [resp (get path :headers {"api_key" api-key})]
      (when (get-in resp [:parsed-body :message])
        (redprintln (get-in resp [:parsed-body :message])))
      (when (get-in resp [:parsed-body :errors])
        (redprintln (get-in resp [:parsed-body :errors])))
      (is (= 200 (:status resp)))
      (when (= 200 (:status resp))
        (is (= (set expected-entity)
               (set (:parsed-body resp))))
        (:parsed-body resp)))))

(deftest-for-each-store test-get-things-by-observable-routes
  "Generate observables.
  Then for each observable, generate judgements, indicators and sightings."
  (helpers/set-capabilities! "foouser" "user" all-capabilities)
  (whoami-helpers/set-whoami-response api-key "foouser" "user")
  (let [nb-judgements 5
        nb-indicators 5
        nb-sightings 5
        nb-observables 5]
    (doseq [observable (cons {:value "1.2.3.4" :type "ip"}
                             (take nb-observables
                                   (remove #(empty? (:value %))
                                           (g/sample (* 2 nb-observables)
                                                     Observable))))
            new-judgement (->> (g/sample nb-judgements NewJudgement)
                               ;; HARDCODED VALUES
                               (map #(merge % {:disposition 5
                                               :disposition_name "Unknown"
                                               ;; TODO: empty value isn't supported
                                               :observable observable})))]
      (let [judgement (test-post "ctia/judgement" new-judgement)]
        (when judgement
          (let [new-indicators (->> (g/sample nb-indicators NewIndicator)
                                    (map #(merge % {:judgements
                                                    [{:judgement_id (:id judgement)}]})))
                indicators     (remove nil?
                                       (map #(test-post "ctia/indicator" %)
                                            new-indicators))]
            (when (= (count indicators) nb-indicators)
              (let [add-sightings-fn #(-> %
                                          (assoc :indicators
                                                 (map (fn [i] {:indicator_id (:id i)})
                                                      indicators)
                                                 :observables [observable])
                                          ;; generated relations cause too much troubles
                                          (dissoc :relations))
                    new-sightings  (->> (g/sample nb-sightings NewSighting)
                                        (map add-sightings-fn))
                    sightings      (doall (map #(test-post "ctia/sighting" %)
                                               new-sightings))
                    route-pref (str "ctia/"
                                    (url-encode (get-in judgement
                                                        [:observable :type]))
                                    "/"
                                    (url-encode (get-in judgement
                                                        [:observable :value])))]
                (test-get-list (str route-pref "/judgements") [judgement])
                ;; TODO: fix it
                ;; (test-get-list (str route-pref "/indicators") indicators)
                (test-get-list (str route-pref "/sightings") sightings)
                (doseq [sighting sightings]
                  (test-delete (str "ctia/sighting/" (:id sighting)))))))
          (test-delete (str "ctia/judgement/" (:id judgement))))
        ;; Just to prevent getting out without any `is`.
        (is (= 1 1))))))


(deftest-for-each-store test-get-sightings-by-observable-tricky
  "Then for each observable, generate judgements, indicators and sightings."
  (helpers/set-capabilities! "foouser" "user" all-capabilities)
  (whoami-helpers/set-whoami-response api-key "foouser" "user")
  (let [nb-sightings 1
        observable {:value "1.2.3.4" :type "ip"}
        tricky-observables [{:type "url" :value "1.2.3.4"}
                            {:type "ip" :value "4.5.6.7"}]
        new-sightings-ip-1234 (->> (g/sample nb-sightings NewSighting)
                                   (map #(assoc % :observables [observable]))
                                   (map #(dissoc % :relations)))
        new-sightings-no-ip-1234 (->> (g/sample nb-sightings NewSighting)
                                      (map #(assoc % :observables tricky-observables))
                                      (map #(dissoc % :relations)))
        sightings-ip-1234 (doall (map #(test-post "ctia/sighting" %) new-sightings-ip-1234))
        sightings-no-ip-1234 (doall (map #(test-post "ctia/sighting" %) new-sightings-no-ip-1234))
        route-ip-1234 (str "ctia/"
                           (url-encode (:type observable))
                           "/"
                           (url-encode (:value observable))
                           "/sightings")]
    (test-get-list route-ip-1234 sightings-ip-1234)))
