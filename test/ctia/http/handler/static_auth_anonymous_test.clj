(ns ctia.http.handler.static-auth-anonymous-test
  (:require [ctia.test-helpers.core :as helpers :refer [GET POST with-properties]]
            [ctia.test-helpers.es :as es-helpers]
            [ctim.domain.id :as id]
            [clojure.test :refer [deftest is testing use-fixtures]]
            [schema.test :refer [validate-schemas]]))

(defn fixture-anonymous-readonly-access
  [t]
  (with-properties
    ["ctia.auth.static.readonly-for-anonymous" true
     ;; Pin the default so `anonymous-verdict-no-org-404-test` is non-vacuous:
     ;; the anonymous 404 must be caused by verdict org-scoping, not by the Green
     ;; judgement being invisible to an anonymous document read. Under `everyone`
     ;; the document IS anonymously readable, so a 404 can only come from the
     ;; org-less verdict guard.
     "ctia.access-control.max-record-visibility" "everyone"]
    (t)))

(use-fixtures :each
  validate-schemas
  es-helpers/fixture-properties:es-store
  (helpers/fixture-properties:static-auth "kitara" "tearbending")
  fixture-anonymous-readonly-access
  helpers/fixture-ctia)

(deftest anonymous-readonly-access-test
  (let [app (helpers/get-current-app)
        {status :status}
        (GET app
             (str "ctia/judgement/search")
             :query-params {:query "*"}
             :headers {"Authorization" "bloodbending"})
        _ (is (= 200 status))

        {status :status}
        (GET app
             (str "ctia/judgement/search")
             :query-params {:query "*"})]
    (is (= 200 status))))

(deftest anonymous-verdict-no-org-404-test
  ;; XFV-20 (route-level): the one path where the fix changes *unauthenticated*
  ;; behaviour. `ReadOnlyIdentity` keeps `:read-verdict`, so pre-fix an anonymous
  ;; caller received a verdict for any Green/White judgement under the default
  ;; `max-record-visibility=everyone`; post-fix the anonymous identity is org-less
  ;; (`auth/orgless-ident?`) and must get 404. The secret holder (whose static
  ;; group is set by `fixture-properties:static-auth`) still gets the verdict.
  ;; This is also the only end-to-end proof the sentinel branch is wired to a real
  ;; `IIdentity`, not a hand-built map.
  (let [app (helpers/get-current-app)
        secret "tearbending"
        observable {:type "ip" :value "10.0.0.1"}
        {create-status :status judgement :parsed-body}
        (POST app
              "ctia/judgement?wait_for=true"
              :body {:observable observable
                     :disposition 2
                     :disposition_name "Malicious"
                     :source "static-auth-test"
                     :priority 99
                     :severity "High"
                     :confidence "High"
                     :tlp "green"
                     :valid_time {:start_time "2016-02-12T00:00:00.000-00:00"}}
              :headers {"Authorization" secret})
        judgement-short-id (some-> (:id judgement) id/long-id->id :short-id)]
    (is (= 201 create-status)
        "the secret holder (a static write identity with a group) may create a judgement")

    (testing "the anonymous document read IS allowed (so the verdict 404 below is org-scoping, not an unreadable judgement)"
      (let [{status :status}
            (GET app (str "ctia/judgement/" judgement-short-id))]
        (is (= 200 status)
            "under max-record-visibility=everyone a Green judgement is anonymously readable as a document")))

    (testing "the secret holder, whose static identity has an org, gets the verdict"
      (let [{status :status}
            (GET app
                 (str "ctia/" (:type observable) "/" (:value observable) "/verdict")
                 :headers {"Authorization" secret})]
        (is (= 200 status))))

    (testing "an anonymous (org-less) caller gets 404 even for a Green judgement under max-record-visibility=everyone"
      (let [{status :status}
            (GET app
                 (str "ctia/" (:type observable) "/" (:value observable) "/verdict"))]
        (is (= 404 status)
            "an org-less caller has no tenant to scope a verdict to")))))
