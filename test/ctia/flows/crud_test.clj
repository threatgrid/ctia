(ns ctia.flows.crud-test
  (:require [clj-momo.lib.map :refer [deep-merge-with]]
            [clojure.test :refer [deftest is testing]]
            [ctia.auth.threatgrid :refer [map->Identity]]
            [ctia.entity.sighting.schemas :as ss]
            [ctia.flows.crud :as flows.crud]
            [ctia.http.exceptions :as exceptions]
            [ctia.http.handler :as handler]
            [ctia.lib.collection :as coll]
            [ctia.store :refer [query-string-search]]
            [ctia.test-helpers.core :as helpers]
            [ctim.examples.sightings :refer [sighting-minimal]]
            [ctia.domain.entities :refer [short-id->long-id]]
            [java-time.api :as jt]
            [puppetlabs.trapperkeeper.app :as app]))

(deftest deep-merge-with-add-colls-test
  (let [fixture {:foo {:bar ["one" "two" "three"]
                       :lorem ["ipsum" "dolor"]}}]
    (is (= {:foo {:bar ["one" "two" "three" "four"]
                  :lorem ["ipsum" "dolor"]}}
           (deep-merge-with coll/add-colls
                            fixture
                            {:foo {:bar ["four"]}})))))

(deftest deep-merge-with-remove-colls-test
  (let [fixture {:foo {:bar #{"one" "two" "three"}
                       :lorem ["ipsum" "dolor"]}}]
    (is (= {:foo {:bar #{"one" "three"}
                  :lorem ["ipsum" "dolor"]}}
           (deep-merge-with coll/remove-colls
                            fixture
                            {:foo {:bar ["two"]}})))))

(deftest deep-merge-with-replace-colls-test
  (let [fixture {:foo {:bar {:foo {:bar ["something" "or" "something" "else"]}}
                       :lorem ["ipsum" "dolor"]}}]
    (is (= {:foo {:bar {:foo {:bar ["else"]}}
                  :lorem ["ipsum" "dolor"]}}
           (deep-merge-with coll/replace-colls
                            fixture
                            {:foo  {:bar {:foo {:bar #{"else"}}}}})))))

(deftest preserve-errors-test
  (testing "with enveloped result"
    (let [f (fn [_]
              {:entities
               [{:id "4"}
                {:id "2"}]
               :enveloped-result? true})
          entities [{:id "1"}
                    {:id "2"}
                    {:id "3"
                     :error "msg"}
                    {:id "4"}]]
      (is (= {:entities
              [{:id "2"}
               {:id "3"
                :error "msg"}
               {:id "4"}]
              :enveloped-result? true}
             (flows.crud/preserve-errors {:entities entities
                                          :enveloped-result? true}
                                         f)))))
  (testing "without enveloped result"
    (is (= {:entities
            [{:id "1"
              :title "title"}]}
           (flows.crud/preserve-errors
            {:entities [{:id "1"}]}
            (fn [_]
              {:entities
               [{:id "1"
                 :title "title"}]}))))))

(deftest apply-create-store-fn-test
  (let [store-fn-create (partial map #(assoc % :applied-store-create true))
        flow-base {:entity-type :indicator
                   :identity :whatever
                   :create-event-fn identity
                   :flow-type :create
                   :store-fn store-fn-create}
        flow-empty-entities (assoc flow-base :entities '())
        flow-with-entities (assoc flow-base
                                  :entities '({:type :fake
                                               :id 1}
                                              {:type :fake
                                               :id 2}))]
    (is (= flow-empty-entities
           (flows.crud/apply-create-store-fn flow-empty-entities))
        "when entities are empty, apply-create-store-fn should not apply store-fn")
    (is (every? #(:applied-store-create %)
                (:entities (flows.crud/apply-create-store-fn flow-with-entities)))
        "store-fn shall be applied to every entities")))

(deftest create-events-test
  (testing "create-events shall filter errored entities and return passed flow with corresponding events owned by current user"
    (let [login "test-user"
          ident (map->Identity {:login login})
          fake-event (fn [entity]
                       {:owner login
                        :entity entity})
          to-create-event (fn [entity _ _]
                            (fake-event entity))
          to-update-event (fn [entity _ _ _]
                            (fake-event entity))
          to-delete-event (fn [entity _ _]
                            (fake-event entity))
          valid-entities [{:id 1 :owner "Huey"}
                          {:id 2 :owner "Dewey"}
                          {:id 3 :owner "Louie"}]
          get-prev-entity (fn [id]
                            (first
                             (filter #(= id (:id %))
                                     valid-entities)))
          entities-with-error (conj valid-entities {:error "something bad happened"})
          base-flow-map {:services {:ConfigService {:get-in-config (constantly true)}}
                         :identity ident
                         :entities entities-with-error}
          create-flow-map (assoc base-flow-map
                                 :flow-type :create
                                 :create-event-fn to-create-event)
          update-flow-map (assoc base-flow-map
                                 :flow-type :update
                                 :get-prev-entity get-prev-entity
                                 :create-event-fn to-update-event)
          delete-flow-map (assoc base-flow-map
                                 :flow-type :delete
                                 :create-event-fn to-delete-event)
          expected-events (map fake-event valid-entities)]

      (doseq [flow-map [create-flow-map
                        update-flow-map
                        delete-flow-map]]
          (is (= (assoc flow-map :events expected-events)
                 (#'flows.crud/create-events flow-map))
              (format "create-events shall properly handle %s flow type"
                      (:flow-type flow-map)))))))

(defn search-events
  [event-store
   ident
   timestamp
   event-type
   entity-id]
  (:data (query-string-search
          event-store
          {:search-query {:filter-map {:entity.id entity-id
                                       :event_type event-type}
                          :range {:timestamp {:gte timestamp}}}
           :ident ident
           :params {}})))

(defn mk-sighting [id]
  (assoc sighting-minimal
         :title (str "sighting " id)
         :id id
         :tlp "green"
         :groups ["groups"]))

(deftest crud-flow-test
  (helpers/with-properties
    ["ctia.store.es.event.refresh" "true"] ;; force refresh for events created in flow
    (helpers/fixture-ctia-with-app
     (fn [app]
       (let [services (app/service-graph app)
             sighting-ids (repeatedly 4 #(flows.crud/make-id :sighting))
             [sighting-id-1 sighting-id-2 sighting-id-3 sighting-id-4] sighting-ids
             store (atom {sighting-id-1 (mk-sighting sighting-id-1)
                          sighting-id-2 (mk-sighting sighting-id-2)
                          sighting-id-3 (mk-sighting sighting-id-3)
                          sighting-id-4 (mk-sighting sighting-id-4)})
             ident {:login "login"
                    :groups ["group1"]}
             event-store (helpers/get-store app :event)
             get-fn (fn [ids] (vals (select-keys @store ids)))
             update-fn (fn [patches]
                         (mapv
                          (fn [{:keys [id] :as patch}]
                            (let [[_old new]
                                  (swap-vals!
                                   store
                                   (fn [s]
                                     (when (contains? s id)
                                       (assoc s id patch))))]
                              (get new id)))
                          patches))
             check-update (fn [res now source-value expected]
                            (let [not-found-ids (set (:not-found res))]
                              (doseq [[id updated?] expected]
                                (is (= (not updated?)
                                       (contains? not-found-ids id)))
                                (is (= updated?
                                       (= source-value
                                          (:source (get @store id))))
                                    (format "the expected update result for %s is %s (%s)" id updated? (pr-str (get @store id))))
                                (is (= updated?
                                       (->> (search-events
                                             event-store
                                             ident
                                             now
                                             :record-updated
                                             id)
                                            first
                                            some?))
                                    (format "The expected update event for %s should be %s" id updated?)))))
             make-result #(select-keys % [:entities :not-found])
             update-flow (fn [msg expected]
                           (testing (str msg "\ntested: " (pr-str expected))
                             (let [now (jt/instant)
                                   entity-ids (seq (keys expected))
                                   docs (map (fn [id]
                                                  (assoc (mk-sighting id)
                                                         :source "updated"))
                                             entity-ids)
                                   res (flows.crud/update-flow
                                        :entity-type :sighting
                                        :services services
                                        :get-fn get-fn
                                        :realize-fn  ss/realize-sighting
                                        :update-fn update-fn
                                        :identity (map->Identity ident)
                                        :entities docs
                                        :long-id-fn identity
                                        :spec :new-sighting/map
                                        :get-success-entities :entities
                                        :make-result make-result)]
                               (check-update res now "updated" expected))))
             patch-flow (fn [msg expected]
                           (testing (str msg "\ntested: " (pr-str expected))
                             (let [now (jt/instant)
                                   entity-ids (seq (keys expected))
                                   patches (map (fn [id]
                                                  {:id id
                                                   :source "patched"})
                                                entity-ids)
                                   res (flows.crud/patch-flow
                                        :entity-type :sighting
                                        :get-fn get-fn
                                        :partial-entities patches
                                        :realize-fn  ss/realize-sighting
                                        :update-fn update-fn
                                        :patch-operation :replace
                                        :long-id-fn identity
                                        :services services
                                        :spec :new-sighting/map
                                        :get-success-entities :entities
                                        :identity (map->Identity ident)
                                        :make-result make-result)]
                               (check-update res
                                             now
                                             "patched"
                                             expected))))
             delete-fn (fn [ids]
                         (into {}
                               (map (fn [id]
                                      (array-map
                                       id
                                       (let [[old _new] (swap-vals! store dissoc id)]
                                         (contains? old id)))))
                               ids))
             delete-flow (fn [msg expected]
                           (testing (str msg "\ntested: " (pr-str expected))
                             (let [entity-ids (seq (keys expected))
                                   now (jt/instant)
                                   res
                                   (flows.crud/delete-flow
                                    :entity-type :sighting
                                    :get-fn get-fn
                                    :delete-fn delete-fn
                                    :entity-ids entity-ids
                                    :long-id-fn identity
                                    :services services
                                    :get-success-entities :entities
                                    :identity (map->Identity ident))
                                   deleted-events? (into {}
                                                         (map #(->> (search-events event-store
                                                                                   ident
                                                                                   now
                                                                                   :record-deleted
                                                                                   %)
                                                                    first
                                                                    some?
                                                                    (array-map %)))
                                                         entity-ids)]
                               (is (= expected (into {} res)))
                               (is (= expected deleted-events?)
                                   (str "flow shall return " expected)))))
             missing-id-1 (short-id->long-id (flows.crud/make-id "missing") services)
             missing-id-2 (short-id->long-id (flows.crud/make-id "missing") services)]
         (patch-flow
          "patch-flow patches existing entities and create events accordingly"
          {sighting-id-1 true sighting-id-2 true})
         (patch-flow
          "patche-flow patches entities and creates events only for existing entities when some are not found"
          {sighting-id-3 true missing-id-1 false missing-id-2 false})
         (update-flow
          "update-flow patches existing entities and create events accordingly"
          {sighting-id-1 true sighting-id-2 true})
         (update-flow
          "update-flow updates entities and creates events only for existing entities when some are not found"
          {sighting-id-3 true missing-id-1 false missing-id-2 false})
         (delete-flow "delete-flow deletes existing entities and create events accordingly"
                      {sighting-id-1 true sighting-id-2 true})
         (delete-flow "delete-flow must not create events for deleted entities"
                      {sighting-id-1 false sighting-id-2 false})
         (delete-flow "delete flow deletes entities and creates events only for existing entities when some are not found"
                      {sighting-id-3 true missing-id-1 false missing-id-2 false}))))))

(defn mk-fake-store
  [entity size]
  (into {}
        (map (fn [id] [id (mk-sighting id)]))
        (repeatedly size #(flows.crud/make-id entity))))

(deftest prev-entity-test
  (let [store (mk-fake-store :sighting 3)
        sighting-short-ids (keys store)
        sighting-long-ids (map #(str "http://localhost:3000/ctia/sighting/" %) sighting-short-ids)
        sighting-short-id-1 (first sighting-short-ids)
        sighting-long-id-1 (first sighting-long-ids)
        _ (assert (every? map? (vals store))
                  "fake-get-fn is not properly initialized, stopping test here")
        fake-get-fn (fn [ids] (map store ids))
        prev-entity-fn (flows.crud/prev-entity fake-get-fn sighting-short-ids)]
    (is (= (store sighting-short-id-1)
           (prev-entity-fn sighting-short-id-1))
        "generated prev-entity-fn shall properly return entities from short ids")
    (is (= (store sighting-short-id-1)
           (prev-entity-fn sighting-long-id-1))
        "generated prev-entity-fn shall properly return entities from long ids")
    (is (nil? (prev-entity-fn "not-found")))))

(deftest patch-entities-test
  (helpers/fixture-ctia-with-app
   (fn [app]
     (let [services (app/service-graph app)
           store (mk-fake-store :sighting 5)
           sighting-short-ids (keys store)
           patched-sighting-ids (take 3 sighting-short-ids)
           partial-entities (map #(array-map :id % :source "patched")
                                 patched-sighting-ids)
           not-found-patch {:id "not-found" :source "patched"}
           entities (conj partial-entities not-found-patch)
           patch-flow-map {:create-event-fn identity
                           :entities entities
                           :entity-type :indicator
                           :flow-type :update
                           :services services
                           :identity (map->Identity {:login "user1" :groups ["g1"]})
                           :store-fn identity
                           :get-prev-entity store
                           :patch-operation :replace}
           expected-entities (conj (map #(-> (store %)
                                             (assoc :source "patched")
                                             (dissoc :schema_version))
                                        patched-sighting-ids)
                                   not-found-patch)
           expected (assoc patch-flow-map :entities expected-entities)]
       (is (= expected
              (flows.crud/patch-entities patch-flow-map)))))))

(deftest authorized-groups-validation-test
  (let [get-in-config (helpers/build-get-in-config-fn)
        services {:ConfigService {:get-in-config get-in-config}}
        validate-entities #'flows.crud/validate-entities]
    (testing "validate-entities rejects entities with foreign authorized_groups on create"
      (let [attacker-ident (map->Identity {:login "attacker"
                                           :groups ["attacker-org"]
                                           :capabilities #{}})
            entity {:tlp "green"
                    :groups ["attacker-org"]
                    :authorized_groups ["attacker-org" "victim-org"]}
            fm {:services services
                :identity attacker-ident
                :entities [entity]
                :spec nil}
            result (validate-entities fm)
            validated-entity (first (:entities result))]
        (is (:error validated-entity)
            "entity with foreign authorized_groups should be rejected")
        (is (= :invalid-authorized-groups-error (:type validated-entity)))
        (is (re-find #"victim-org" (:msg validated-entity)))))

    (testing "validate-entities allows entities where authorized_groups is subset of user's groups"
      (let [legit-ident (map->Identity {:login "legit-user"
                                        :groups ["org-a" "org-b"]
                                        :capabilities #{}})
            entity {:tlp "green"
                    :groups ["org-a"]
                    :authorized_groups ["org-a" "org-b"]}
            fm {:services services
                :identity legit-ident
                :entities [entity]
                :spec nil}
            result (validate-entities fm)
            validated-entity (first (:entities result))]
        (is (nil? (:error validated-entity))
            "entity with valid authorized_groups should pass validation")))

    (testing "validate-entities allows entities with no authorized_groups"
      (let [ident (map->Identity {:login "user"
                                  :groups ["org-a"]
                                  :capabilities #{}})
            entity {:tlp "green"
                    :groups ["org-a"]}
            fm {:services services
                :identity ident
                :entities [entity]
                :spec nil}
            result (validate-entities fm)
            validated-entity (first (:entities result))]
        (is (nil? (:error validated-entity))
            "entity without authorized_groups should pass validation")))

    (testing "validate-entities rejects cross-tenant poisoning attempt on create"
      (let [attacker-ident (map->Identity {:login "attacker"
                                           :groups ["evil-corp"]
                                           :capabilities #{}})
            entity {:tlp "amber"
                    :groups ["evil-corp"]
                    :authorized_groups ["target-tenant"]}
            fm {:services services
                :identity attacker-ident
                :entities [entity]
                :spec nil}
            result (validate-entities fm)
            validated-entity (first (:entities result))]
        (is (:error validated-entity)
            "cross-tenant poisoning should be rejected")
        (is (= :invalid-authorized-groups-error (:type validated-entity)))
        (is (re-find #"target-tenant" (:msg validated-entity)))))

    (testing "validate-entities rejects foreign authorized_groups on update too"
      (let [owner-ident (map->Identity {:login "owner"
                                        :groups ["my-org"]
                                        :capabilities #{}})
            entity {:tlp "green"
                    :groups ["my-org"]
                    :authorized_groups ["foreign-org"]}
            fm {:services services
                :identity owner-ident
                :entities [entity]
                :spec nil}
            result (validate-entities fm)
            validated-entity (first (:entities result))]
        (is (:error validated-entity)
            "foreign authorized_groups should be rejected on update too")
        (is (= :invalid-authorized-groups-error (:type validated-entity)))))

    (testing "validate-entities rejects entities with foreign authorized_users"
      (let [attacker-ident (map->Identity {:login "attacker"
                                           :groups ["attacker-org"]
                                           :capabilities #{}})
            entity {:tlp "green"
                    :groups ["attacker-org"]
                    :authorized_users ["victim-login"]}
            fm {:services services
                :identity attacker-ident
                :entities [entity]
                :spec nil}
            result (validate-entities fm)
            validated-entity (first (:entities result))]
        (is (:error validated-entity)
            "entity with foreign authorized_users should be rejected")
        (is (= :invalid-authorized-users-error (:type validated-entity)))
        (is (re-find #"victim-login" (:msg validated-entity)))))

    (testing "validate-entities allows authorized_users containing only the caller's login"
      (let [legit-ident (map->Identity {:login "legit-user"
                                        :groups ["org-a"]
                                        :capabilities #{}})
            entity {:tlp "green"
                    :groups ["org-a"]
                    :authorized_users ["legit-user"]}
            fm {:services services
                :identity legit-ident
                :entities [entity]
                :spec nil}
            result (validate-entities fm)
            validated-entity (first (:entities result))]
        (is (nil? (:error validated-entity))
            "entity with only caller's own login in authorized_users should pass")))

    (testing "validate-entities rejects cross-tenant poisoning via authorized_users"
      (let [attacker-ident (map->Identity {:login "attacker"
                                           :groups ["evil-corp"]
                                           :capabilities #{}})
            entity {:tlp "amber"
                    :groups ["evil-corp"]
                    :authorized_users ["attacker" "victim-login"]}
            fm {:services services
                :identity attacker-ident
                :entities [entity]
                :spec nil}
            result (validate-entities fm)
            validated-entity (first (:entities result))]
        (is (:error validated-entity)
            "cross-tenant poisoning via authorized_users should be rejected")
        (is (= :invalid-authorized-users-error (:type validated-entity)))
        (is (re-find #"victim-login" (:msg validated-entity)))))

    (testing "validate-entities handles case-insensitive authorized_groups comparison"
      (let [ident (map->Identity {:login "user"
                                  :groups ["My-Org"]
                                  :capabilities #{}})
            entity {:tlp "green"
                    :groups ["My-Org"]
                    :authorized_groups ["my-org"]}
            fm {:services services
                :identity ident
                :entities [entity]
                :spec nil}
            result (validate-entities fm)
            validated-entity (first (:entities result))]
        (is (nil? (:error validated-entity))
            "case-insensitive match should allow authorized_groups")))))

(deftest authorized-groups-validation-diff-on-update-test
  ;; Regression tests for CR1: on update/patch the check must only reject
  ;; authorized_* values the caller *introduces* relative to the stored entity.
  ;; Pre-existing foreign values (legacy multi-value shares, or values echoed
  ;; back verbatim via PUT) must not trigger a false 400.
  (let [get-in-config (helpers/build-get-in-config-fn)
        services {:ConfigService {:get-in-config get-in-config}}
        validate-entities #'flows.crud/validate-entities]

    (testing "editing a record that already carried foreign authorized_groups does not 400"
      (let [ident (map->Identity {:login "bob" :groups ["b"] :capabilities #{}})
            prev {:id "actor-x" :owner "alice" :groups ["a"]
                  :authorized_groups ["b" "c"]}
            entity {:id "actor-x" :owner "alice" :groups ["a"]
                    :authorized_groups ["b" "c"] :title "new"}
            fm {:services services
                :identity ident
                :entities [entity]
                :get-prev-entity (fn [_] prev)
                :spec nil}
            validated (first (:entities (validate-entities fm)))]
        (is (nil? (:error validated))
            "retaining a pre-existing foreign authorized_group must be allowed")))

    (testing "adding a new foreign authorized_group on update is still rejected"
      (let [ident (map->Identity {:login "bob" :groups ["b"] :capabilities #{}})
            prev {:id "actor-x" :owner "alice" :groups ["a"]
                  :authorized_groups ["b"]}
            entity {:id "actor-x" :owner "alice" :groups ["a"]
                    :authorized_groups ["b" "d"]}
            fm {:services services
                :identity ident
                :entities [entity]
                :get-prev-entity (fn [_] prev)
                :spec nil}
            validated (first (:entities (validate-entities fm)))]
        (is (:error validated)
            "introducing a new foreign authorized_group must be rejected")
        (is (= :invalid-authorized-groups-error (:type validated)))
        (is (re-find #"\bd\b" (:msg validated)))))

    (testing "editing a record that already carried a foreign authorized_user does not 400"
      (let [ident (map->Identity {:login "bob" :groups ["b"] :capabilities #{}})
            prev {:id "actor-x" :owner "alice" :groups ["a"]
                  :authorized_users ["alice" "bob"]}
            entity {:id "actor-x" :owner "alice" :groups ["a"]
                    :authorized_users ["alice" "bob"] :title "new"}
            fm {:services services
                :identity ident
                :entities [entity]
                :get-prev-entity (fn [_] prev)
                :spec nil}
            validated (first (:entities (validate-entities fm)))]
        (is (nil? (:error validated))
            "retaining a pre-existing foreign authorized_user must be allowed")))

    (testing "adding a new foreign authorized_user on update is still rejected"
      (let [ident (map->Identity {:login "bob" :groups ["b"] :capabilities #{}})
            prev {:id "actor-x" :owner "alice" :groups ["a"]
                  :authorized_users ["bob"]}
            entity {:id "actor-x" :owner "alice" :groups ["a"]
                    :authorized_users ["bob" "victim"]}
            fm {:services services
                :identity ident
                :entities [entity]
                :get-prev-entity (fn [_] prev)
                :spec nil}
            validated (first (:entities (validate-entities fm)))]
        (is (:error validated)
            "introducing a new foreign authorized_user must be rejected")
        (is (= :invalid-authorized-users-error (:type validated)))
        (is (re-find #"victim" (:msg validated)))))))

(deftest url-scheme-check-test
  ;; XFV-135: defense-in-depth against stored XSS via threat-intel URL fields.
  ;; Ingest must reject dangerous URL schemes (javascript:, data:, vbscript:)
  ;; in URL-typed fields while allowing http/https and scheme-less values.
  ;; These cases exercise the create path (no prev-entity), so bind a 1-arg
  ;; wrapper passing prev-entity = nil (everything is checked) and ident-map = nil;
  ;; the prev-entity diff behavior is covered by url-scheme-check-prev-entity-diff-test
  ;; and caller attribution by url-scheme-check-caller-attribution-test below.
  (let [url-scheme-check #(#'flows.crud/url-scheme-check % nil nil)]
    (testing "rejects a javascript: scheme in a URL-typed field"
      (let [result (url-scheme-check {:source_uri "javascript:alert(document.cookie)"})]
        (is (= :unsafe-url-scheme-error (:type result)))
        ;; The `:error` key is load-bearing: throw-validation-error selects error
        ;; maps with (filter :error entities) (crud.clj) and remove-errors drops
        ;; them, so deleting `:error "Entity validation Error"` from url-scheme-check
        ;; would silently disable the gate (the payload would be stored) while
        ;; `:type` still passes. Pin the whole contract, not just `:type`.
        (is (= "Entity validation Error" (:error result)))
        ;; LOW2: pin the exact message shape once, not just substrings.
        (is (= "Disallowed URL scheme in field(s): source_uri (javascript:). Allowed schemes: http, https"
               (:msg result)))))

    (testing "rejects a dangerous scheme under every URL-typed key"
      ;; Loops all five keys in `url-typed-keys` so dropping any one (e.g.
      ;; :origin_uri) from the set breaks a test rather than silently going
      ;; unchecked.
      (doseq [k [:url :source_uri :origin_uri :reason_uri :identity]]
        (is (= :unsafe-url-scheme-error
               (:type (url-scheme-check {k "javascript:alert(1)"})))
            (str "must flag a javascript: scheme under " k))))

    (testing "rejects a data: scheme in an external_reference url (nested)"
      (let [result (url-scheme-check
                    {:title "x"
                     :external_references [{:source_name "s"
                                            :url "data:text/html;base64,PHNjcmlwdD4="}]})]
        (is (= :unsafe-url-scheme-error (:type result)))
        (is (re-find #"data" (:msg result)))))

    (testing "rejects a vbscript: scheme"
      (is (= :unsafe-url-scheme-error
             (:type (url-scheme-check {:reason_uri "vbscript:msgbox(1)"})))))

    (testing "rejects control-char-obfuscated javascript scheme (browser strips \\t/\\n)"
      (is (= :unsafe-url-scheme-error
             (:type (url-scheme-check {:url "java\tscript:alert(1)"}))))
      (is (= :unsafe-url-scheme-error
             (:type (url-scheme-check {:url "  JAVASCRIPT:alert(1)"})))))

    (testing "rejects HTML-numeric-entity-obfuscated javascript scheme"
      ;; &#106; = 'j'; a consumer that HTML-decodes before rendering would
      ;; otherwise resolve this to javascript:.
      (is (= :unsafe-url-scheme-error
             (:type (url-scheme-check {:url "&#106;avascript:alert(1)"}))))
      ;; hex entity for the colon: java&#x3a;script -> javascript:
      (is (= :unsafe-url-scheme-error
             (:type (url-scheme-check {:url "javascript&#x3a;alert(1)"}))))
      ;; entity-encoded control char between letters: java&#9;script:
      (is (= :unsafe-url-scheme-error
             (:type (url-scheme-check {:url "java&#9;script:alert(1)"}))))
      ;; numeric ref without the optional trailing semicolon: &#106 -> 'j'
      (is (= :unsafe-url-scheme-error
             (:type (url-scheme-check {:url "&#106avascript:alert(1)"})))))

    (testing "rejects HTML-named-entity-obfuscated javascript scheme"
      ;; &colon; = ':' — the scheme delimiter is the one structural char that
      ;; HAS a named reference; browsers HTML-decode it at the href sink.
      (is (= :unsafe-url-scheme-error
             (:type (url-scheme-check {:url "javascript&colon;alert(document.domain)"}))))
      ;; &NewLine; / &Tab; decode to chars the URL parser strips, splitting the
      ;; scheme letters: java&NewLine;script: -> javascript:
      (is (= :unsafe-url-scheme-error
             (:type (url-scheme-check {:url "java&NewLine;script:alert(1)"}))))
      (is (= :unsafe-url-scheme-error
             (:type (url-scheme-check {:url "java&Tab;script:alert(1)"}))))
      ;; case-insensitive matching of the named references
      (is (= :unsafe-url-scheme-error
             (:type (url-scheme-check {:url "javascript&COLON;alert(1)"})))))

    (testing "HTML entities appearing after a safe scheme do not cause a false positive"
      ;; &#39; (apostrophe) and &amp; in a query string must not be flagged.
      (let [entity {:url "https://example.com/q?a=1&#39;&b=2"}]
        (is (= entity (url-scheme-check entity)))))

    (testing "an oversized numeric entity does not crash and is not treated as a scheme"
      ;; 21 digits overflow Integer/parseInt -> NumberFormatException catch.
      (let [entity {:url "&#999999999999999999999;oke"}]
        (is (= entity (url-scheme-check entity)))))

    (testing "a numeric entity past the max Unicode code point does not crash"
      ;; &#1114112; = 0x110000, one past Character/MAX_CODE_POINT. It parses as an
      ;; int (no NumberFormatException) but fails the (<= 0 n 0x10FFFF) range guard
      ;; in code-point->str, which must return nil rather than let Character/toChars
      ;; throw IllegalArgumentException (an uncaught throw would surface as HTTP 500).
      ;; Pins that range guard, which the 21-digit case above never exercises.
      (let [entity {:url "&#1114112;oke"}]
        (is (= entity (url-scheme-check entity)))))

    (testing "flags a dangerous scheme in a collection-valued URL field"
      (let [result (url-scheme-check {:url ["https://ok.example" "javascript:alert(1)"]})]
        (is (= :unsafe-url-scheme-error (:type result))))
      ;; set-valued field exercises the same coll? branch
      (let [result (url-scheme-check {:url #{"https://ok.example" "javascript:alert(1)"}})]
        (is (= :unsafe-url-scheme-error (:type result)))))

    (testing "collapses multiple distinct offending values in one field to one label"
      ;; Pins the `distinct` in url-scheme-check: two different javascript: values
      ;; under the same :url share field+scheme, so the message lists
      ;; "url (javascript:)" exactly once. Dropping `distinct` would duplicate it.
      (let [result (url-scheme-check {:url ["javascript:a(1)" "javascript:b(2)"]})
            n (count (re-seq #"url \(javascript:\)" (:msg result)))]
        (is (= :unsafe-url-scheme-error (:type result)))
        (is (= 1 n) "the field+scheme label must appear exactly once")))

    (testing "recurses into a nested map and flags a string :identity value"
      (let [result (url-scheme-check {:sighting {:identity "javascript:alert(1)"}})]
        (is (= :unsafe-url-scheme-error (:type result)))))

    (testing "allows a valid https: URL (control)"
      (let [entity {:source_uri "https://example.com/intel?q=1"
                    :external_references [{:source_name "s"
                                           :url "http://example.org/a"}]}]
        (is (= entity (url-scheme-check entity))
            "well-formed http/https URLs must pass unchanged")))

    (testing "allows an uppercase safe scheme (schemes are matched case-insensitively)"
      ;; Pins the `str/lower-case` before the allowlist lookup in unsafe-url-scheme:
      ;; dropping it would leave scheme "HTTPS"/"Http", miss the http/https set, and
      ;; 400 every uppercase-scheme URL. The reject-side already has an uppercase
      ;; case (" JAVASCRIPT:"); this is the safe-side twin.
      (let [entity {:url "HTTPS://example.com/a" :source_uri "Http://example.org"}]
        (is (= entity (url-scheme-check entity))
            "uppercase http/https schemes must pass unchanged")))

    (testing "reveals a double-encoded scheme delimiter (decode iterates to a fixed point)"
      ;; "javascript&#38;colon;alert(1)": &#38; is '&', so the decimal pass yields
      ;; "javascript&colon;alert(1)". A single decode pass (named-then-numeric)
      ;; would stop there and MISS the scheme; iterating re-runs the named pass and
      ;; unmasks "javascript:". Pins the fixed-point loop in decode-html-entities.
      (is (= :unsafe-url-scheme-error
             (:type (url-scheme-check {:url "javascript&#38;colon;alert(1)"})))))

    (testing "strips non-ASCII ignorable characters emitted by the decoder"
      ;; The strip class must cover more than ASCII controls: the decoder emits
      ;; Unicode format/separator chars a browser ignores when resolving a URL.
      ;; Each of these decodes to an ignorable that splits/prefixes the scheme.
      (doseq [v ["java&#8203;script:alert(1)"    ; U+200B ZERO WIDTH SPACE (Cf)
                 "&#65279;javascript:alert(1)"   ; U+FEFF BOM (Cf)
                 "&#160;javascript:alert(1)"     ; U+00A0 NO-BREAK SPACE (Zs)
                 "java&#173;script:alert(1)"]]   ; U+00AD SOFT HYPHEN (Cf)
        (is (= :unsafe-url-scheme-error
               (:type (url-scheme-check {:url v})))
            (str "must unmask ignorable-split scheme in: " v))))

    (testing "named-entity decode is locale-independent (no NPE under a tr locale)"
      ;; str/lower-case uses the JVM default locale; under Turkish/Azeri "NEWLINE"
      ;; lowercases to "newlıne" (dotless i), which would miss the map and, before
      ;; the fix, NPE str/replace out to a 500. The decoder uses Locale/ROOT and an
      ;; `or` fallback, so the scheme is still unmasked here.
      (let [default (java.util.Locale/getDefault)]
        (try
          (java.util.Locale/setDefault (java.util.Locale/forLanguageTag "tr"))
          (is (= :unsafe-url-scheme-error
                 (:type (url-scheme-check {:url "java&NEWLINE;script:alert(1)"}))))
          (finally (java.util.Locale/setDefault default)))))

    (testing "allows scheme-less / relative values"
      (let [entity {:source_uri "/relative/path" :url "example.com/a"}]
        (is (= entity (url-scheme-check entity)))))

    (testing "an ambiguous bare authority (host:port) parses as a scheme and is rejected"
      ;; Pins the documented behavior in `safe-url-schemes`: "example.com:8080/path"
      ;; matches url-scheme-re with scheme "example.com" (not allowlisted). CTIM
      ;; URI fields carry absolute http(s) URLs, not bare host:port authorities;
      ;; a future change to url-scheme-re must not silently alter this contract.
      (is (= :unsafe-url-scheme-error
             (:type (url-scheme-check {:url "example.com:8080/path"})))))

    (testing "allows transient: references in URL-typed fields (resolved during ingest)"
      ;; Bulk/bundle submissions cross-link entities with transient IDs before
      ;; real IDs exist; these land in URI-typed fields (e.g. :source_uri on a
      ;; relationship) and this check runs before they are resolved. transient:
      ;; is a CTIA-internal, non-executable scheme, so it must not be flagged.
      (let [entity {:source_uri "transient:0"
                    :url (str "transient:" (random-uuid))}]
        (is (= entity (url-scheme-check entity))
            "transient references must pass validation unchanged")))

    (testing "does not flag dangerous-looking free-text in non-URL fields"
      ;; threat-intel descriptions and observable IOC values legitimately mention
      ;; malicious URLs; only URL-typed fields are gated.
      (let [entity {:description "Attacker used javascript:alert(1) payload"
                    :observable {:type "url" :value "javascript:alert(1)"}}]
        (is (= entity (url-scheme-check entity)))))

    (testing "passes through an entity already marked as an error by a prior check"
      (let [err {:error "Entity validation Error" :type :invalid-tlp-error
                 :entity {:source_uri "javascript:alert(1)"}}]
        (is (= err (url-scheme-check err))
            "must not clobber a prior validation error")))))

(deftest url-scheme-check-prev-entity-diff-test
  ;; XFV-135 / CR1: on update/patch the gate flags only values the caller
  ;; *introduces* relative to prev-entity (mirroring authorized-groups-check),
  ;; so patch-entities' deep-merge of a pre-gate stored URI does not 400 an
  ;; unrelated update. Wrapper passes ident-map = nil (attribution is covered by
  ;; url-scheme-check-caller-attribution-test), so call sites stay (entity prev).
  (let [url-scheme-check #(#'flows.crud/url-scheme-check %1 %2 nil)]
    (testing "a newly-introduced dangerous scheme is rejected on update"
      (let [prev   {:source_uri "https://old.example"}
            entity {:source_uri "javascript:alert(1)"}]
        (is (= :unsafe-url-scheme-error
               (:type (url-scheme-check entity prev))))))

    (testing "a pre-existing dangerous value the caller did not change is NOT re-rejected"
      ;; models POST /incident/:id/status: patch-entities deep-merged the stored
      ;; source_uri back into the entity; it is byte-identical to prev-entity.
      (let [prev   {:source_uri "javascript:legacy(1)" :status "New"}
            entity {:source_uri "javascript:legacy(1)" :status "Closed"}]
        (is (= entity (url-scheme-check entity prev))
            "a value identical to the stored one must pass through unchanged")))

    (testing "changing a dangerous value to a different dangerous payload IS rejected"
      ;; diffing on the exact value (not just the scheme) catches a swapped payload.
      (let [prev   {:source_uri "javascript:legacy(1)"}
            entity {:source_uri "javascript:brand_new(1)"}]
        (is (= :unsafe-url-scheme-error
               (:type (url-scheme-check entity prev))))))

    (testing "a nil prev-entity (create) checks every value"
      (is (= :unsafe-url-scheme-error
             (:type (url-scheme-check {:source_uri "javascript:alert(1)"} nil)))))))

(deftest url-scheme-check-caller-attribution-test
  ;; XFV-135: a rejected dangerous-scheme write is a security-relevant anomaly
  ;; logged at :warn "to help operators correlate a poisoning campaign". Carry the
  ;; caller's :login/:groups into the error map (threaded via ident-map, as
  ;; authorized-groups-check/authorized-users-check do) so an operator seeing a
  ;; burst of rejected javascript: writes can attribute them to a caller. Pins that
  ;; the two keys are present with the ident-map values.
  (let [url-scheme-check #'flows.crud/url-scheme-check
        ident-map {:login "attacker" :groups ["evil-corp"]}]
    (testing "the error map carries the caller's login and groups"
      (let [result (url-scheme-check {:source_uri "javascript:alert(1)"} nil ident-map)]
        (is (= :unsafe-url-scheme-error (:type result)))
        (is (= "attacker" (:login result)))
        (is (= ["evil-corp"] (:groups result)))))
    (testing "a passing entity is returned unchanged (no attribution keys added)"
      (let [entity {:source_uri "https://example.com/intel"}]
        (is (= entity (url-scheme-check entity nil ident-map)))))))

(deftest url-scheme-validation-through-validate-entities-test
  ;; End-to-end through the shared write chokepoint used by API writes and
  ;; bundle/bulk import (create-flow/update-flow/patch-flow all call this).
  (let [get-in-config (helpers/build-get-in-config-fn)
        services {:ConfigService {:get-in-config get-in-config}}
        validate-entities #'flows.crud/validate-entities
        ident (map->Identity {:login "user" :groups ["org-a"] :capabilities #{}})]
    (testing "validate-entities rejects a javascript: source_uri on create"
      (let [fm {:services services
                :identity ident
                :entities [{:tlp "green" :groups ["org-a"]
                            :source_uri "javascript:alert(1)"}]
                :spec nil}
            validated (first (:entities (validate-entities fm)))]
        (is (= :unsafe-url-scheme-error (:type validated)))
        ;; End-to-end contract: the entity must carry the `:error` key that
        ;; throw-validation-error/remove-errors act on to actually reject the
        ;; write. Deleting that key from url-scheme-check leaves `:type` intact but
        ;; lets the javascript: payload through — this assertion fails the mutation.
        (is (= "Entity validation Error" (:error validated)))
        ;; Pin the ident-map wiring through the real chokepoint: validate-entities
        ;; must thread ident-map into url-scheme-check so :login/:groups reach the
        ;; :warn log for caller attribution. A regression that drops that arg would
        ;; leave :type/:error green here but silently break audit attribution.
        (is (= "user" (:login validated)))
        (is (= ["org-a"] (:groups validated)))))

    (testing "validate-entities allows an https: source_uri on create"
      (let [fm {:services services
                :identity ident
                :entities [{:tlp "green" :groups ["org-a"]
                            :source_uri "https://example.com/intel"}]
                :spec nil}
            validated (first (:entities (validate-entities fm)))]
        (is (nil? (:error validated)))))

    (testing "validate-entities rejects a NEWLY-INTRODUCED javascript: source_uri on update"
      ;; The entity carries an :id, so validate-entities actually invokes
      ;; get-prev-entity (it only does so when (:id entity) is present); assert
      ;; the stub fired so this genuinely exercises the update/diff path, not a
      ;; byte-identical duplicate of the create case.
      (let [called (atom false)
            fm {:services services
                :identity ident
                :entities [{:id "x" :tlp "green" :groups ["org-a"]
                            :source_uri "javascript:alert(1)"}]
                :get-prev-entity (fn [_]
                                   (reset! called true)
                                   {:tlp "green" :groups ["org-a"]
                                    :source_uri "https://old.example"})
                :spec nil}
            validated (first (:entities (validate-entities fm)))]
        (is (true? @called) "get-prev-entity must be consulted on update")
        (is (= :unsafe-url-scheme-error (:type validated))
            "a newly-introduced dangerous scheme must be rejected on update")))

    (testing "validate-entities does NOT re-reject a pre-existing dangerous source_uri on update"
      ;; CR1: models POST /incident/:id/status — patch-entities deep-merges the
      ;; stored (pre-gate) :source_uri back into the entity; the caller never
      ;; touched it, so it must not 400 an unrelated field change.
      (let [fm {:services services
                :identity ident
                :entities [{:id "x" :tlp "green" :groups ["org-a"]
                            :source_uri "javascript:legacy(1)"}]
                :get-prev-entity (fn [_] {:tlp "green" :groups ["org-a"]
                                          :source_uri "javascript:legacy(1)"})
                :spec nil}
            validated (first (:entities (validate-entities fm)))]
        (is (nil? (:error validated))
            "a pre-existing value the caller did not introduce must pass")))))

(deftest unsafe-url-scheme-error-is-registered-test
  ;; XFV-135 regression: url-scheme-check emits :type :unsafe-url-scheme-error.
  ;; If that key is missing from the compojure-api exception handlers, the throw
  ;; falls through to default-error-handler and surfaces as HTTP 500 instead of
  ;; 400 (a rejected payload then looks like a server bug). Pin the registration.
  (testing "the :unsafe-url-scheme-error type maps to the bad-request handler"
    (is (= exceptions/unsafe-url-scheme-error-handler
           (:unsafe-url-scheme-error handler/exception-handlers)))))
