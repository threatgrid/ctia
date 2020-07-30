(ns ctia.test-helpers.access-control
  (:refer-clojure :exclude [get])
  (:require [clojure.set :as set]
            [clojure.test :refer [is testing]]
            [ctia.properties :as p]
            [ctia.test-helpers
             [auth :refer [all-capabilities]]
             [core :as helpers :refer [delete get post put]]
             [fake-whoami-service :as whoami-helpers]]
            [ctia.domain.access-control :as cdac]
            [ctim.domain.id :as id]))

;; === Access Control Test Scenario workflow: ===

;; - Player 1 is isolated, Player 2-3 in same group
;; - Each Player creates a Record with a given TLP
;; - Test Player 2 attempts access on Player 1 R/W
;; - Test Player 3 attempts access on Player 1 R/W
;; - Test a List query for Each Player
;; - Player 2 Attempts deleting Player 3 entity
;; - Repost Deleted entities

;; - Player 1 Allows Player 2

;; - Test Player 2 attempts access on Player 1 R/W
;; - Test Player 3 attempts access on Player 1 R/W
;; - Test a List query for Each Player
;; - Player 2 Attempts deleting Player 3 entity
;; - Repost Deleted entities

;; - Player 1 Allows Player 2-3 Group

;; - Test Player 2 attempts access on Player 1 R/W
;; - Test Player 3 attempts access on Player 1 R/W
;; - Test a List query for Each Player
;; - Player 2 Attempts deleting Player 3 entity


(defn green-entity [entity]
  (assoc entity :tlp "green"))

(defn amber-entity [entity]
  (assoc entity :tlp "amber"))

(defn red-entity [entity]
  (assoc entity :tlp "red"))

(def allowed-statuses #{200 204})
(def forbidden-statuses #{403})

(defn same-ownership? [entity-1 entity-2]
  (and (= (:owner entity-1)
          (:owner entity-2))
       (= (:groups entity-1)
          (:groups entity-2))))

(defn crud-access-control-test
  [{:keys [entity
           can-update?
           can-delete?
           player-1-creation
           player-2-creation
           player-3-creation
           player-2-1-expected-read-statuses
           player-2-1-expected-write-statuses
           player-2-3-expected-read-statuses
           player-2-3-expected-write-statuses
           player-3-1-expected-read-statuses
           player-3-1-expected-write-statuses
           list-query
           player-1-expected-entity-list
           player-2-expected-entity-list
           player-3-expected-entity-list]}]

  (let [{player-1-entity :parsed-body
         player-1-entity-status :status} player-1-creation
        {player-2-entity :parsed-body
         player-2-entity-status :status} player-2-creation
        {player-3-entity :parsed-body
         player-3-entity-status :status} player-3-creation
        player-1-entity-id
        (id/long-id->id (:id player-1-entity))

        player-2-entity-id
        (id/long-id->id (:id player-2-entity))

        player-3-entity-id
        (id/long-id->id (:id player-3-entity))

        ;; searches
        {player-1-entity-search :parsed-body}
        (get (format "ctia/%s/search" entity)
             :query-params {:query list-query}
             :headers {"Authorization" "player-1-token"})

        {player-2-entity-search :parsed-body}
        (get (format "ctia/%s/search" entity)
             :query-params {:query list-query}
             :headers {"Authorization" "player-2-token"})

        {player-3-entity-search :parsed-body}
        (get (format "ctia/%s/search" entity)
             :query-params {:query list-query}
             :headers {"Authorization" "player-3-token"})

        ;; attempts to access player 1 entity
        {player-2-1-entity-read-status :status}
        (get (format "ctia/%s/%s"
                     entity
                     (:short-id player-1-entity-id))
             :headers {"Authorization" "player-2-token"})

        {player-3-1-entity-read-status :status}
        (get (format "ctia/%s/%s"
                     entity
                     (:short-id player-1-entity-id))
             :headers {"Authorization" "player-3-token"})

        {player-2-1-entity-overwrite-status :status
         player-2-1-entity-overwrite-body :parsed-body}
        (put (format "ctia/%s/%s"
                     entity
                     (:short-id player-1-entity-id))
             :body (dissoc player-1-entity :id)
             :headers {"Authorization" "player-2-token"})

        {player-2-1-entity-delete-status :status}
        (delete (format "ctia/%s/%s" entity (:short-id player-1-entity-id))
                :headers {"Authorization" "player-2-token"})

        ;; attempts to access player 3 entity
        {player-2-3-entity-read-status :status}
        (get (format "ctia/%s/%s"
                     entity
                     (:short-id player-3-entity-id))
             :headers {"Authorization" "player-2-token"})

        {player-2-3-entity-overwrite-status :status
         player-2-3-entity-overwrite-body :parsed-body}
        (put  (format "ctia/%s/%s"
                      entity
                      (:short-id player-3-entity-id))
              :body (dissoc player-1-entity :id)
              :headers {"Authorization" "player-2-token"})

        {player-2-2-entity-delete-status :status}
        (delete (format "ctia/%s/%s" entity (:short-id player-2-entity-id))
                :headers {"Authorization" "player-2-token"})

        {player-2-3-entity-delete-status :status}
        (delete (format "ctia/%s/%s" entity (:short-id player-3-entity-id))
                :headers {"Authorization" "player-2-token"})]

    (testing "expected list outputs"
      (is (= (set player-1-expected-entity-list)
             (set player-1-entity-search)))

      (is (= (set player-2-expected-entity-list)
             (set player-2-entity-search)))

      (is (= (set player-3-expected-entity-list)
             (set player-3-entity-search))))

    (testing "player 3 try to access player 1 entities"
      ;; read
      (is (set/subset?
           #{player-3-1-entity-read-status}
           player-3-1-expected-read-statuses)))

    ;; initial restrictions control
    (testing "player 2 try to access player 1 entities"
      ;; read
      (is (set/subset?
           #{player-2-1-entity-read-status}
           player-2-1-expected-read-statuses))

      ;; update
      (when can-update?
        (is (set/subset?
             #{player-2-1-entity-overwrite-status}
             player-2-1-expected-write-statuses))

        (when (= player-2-1-expected-write-statuses
                 allowed-statuses)
          (is (same-ownership? player-1-entity
                               player-2-1-entity-overwrite-body))))

      ;; delete
      (when can-delete?
        (is (set/subset?
             #{player-2-1-entity-delete-status}
             player-2-1-expected-write-statuses))))

    (testing "player 2 try to access player 3 created entities"
      ;; read
      (is (set/subset?
           #{player-2-3-entity-read-status}
           player-2-3-expected-read-statuses))

      ;; update
      (when can-update?
        (is (set/subset?
             #{player-2-3-entity-overwrite-status}
             player-2-3-expected-write-statuses))

        ;; check we don't overwrite :owner or :groups
        (when (= player-2-3-expected-write-statuses
                 allowed-statuses)
          (is (same-ownership? player-3-entity
                               player-2-3-entity-overwrite-body))))

      ;; delete
      (when can-delete?
        (is (set/subset?
             #{player-2-3-entity-delete-status}
             player-2-3-expected-write-statuses))))))

(defn test-access-control-entity-tlp-green-max-record-visibility-group
  [{:keys [entity
           new-entity
           can-update?]
    :as args}]

  (with-redefs [cdac/max-record-visibility-everyone?
                (constantly false)]
    (testing "Green Max Record Visibility set to `Group`"
      (is (false? (cdac/max-record-visibility-everyone?)))

      (let [player-1-entity-post
            (post (format "ctia/%s" entity)
                  :body (assoc (green-entity new-entity)
                               :external_ids ["gmrvgpost"])
                  :headers {"Authorization" "player-1-token"})

            player-1-entity-repost
            (post (format "ctia/%s" entity)
                  :body (assoc (green-entity new-entity)
                               :external_ids ["gmrvgrepost"])
                  :headers {"Authorization" "player-1-token"})

            player-1-entity-repost2
            (post (format "ctia/%s" entity)
                  :body (assoc (green-entity new-entity)
                               :external_ids ["gmrvgrepost2"])
                  :headers {"Authorization" "player-1-token"})

            player-2-entity-post
            (post (format "ctia/%s" entity)
                  :body (assoc (green-entity new-entity)
                               :external_ids ["gmrvgpost"])
                  :headers {"Authorization" "player-2-token"})

            player-2-entity-repost
            (post (format "ctia/%s" entity)
                  :body (assoc (green-entity new-entity)
                               :external_ids ["gmrvgrepost"])
                  :headers {"Authorization" "player-2-token"})

            player-2-entity-repost2
            (post (format "ctia/%s" entity)
                  :body (assoc (green-entity new-entity)
                               :external_ids ["gmrvgrepost2"])
                  :headers {"Authorization" "player-2-token"})

            player-3-entity-post
            (post (format "ctia/%s" entity)
                  :body (assoc (green-entity new-entity)
                               :external_ids ["gmrvgpost"])
                  :headers {"Authorization" "player-3-token"})

            player-3-entity-repost
            (post (format "ctia/%s" entity)
                  :body (assoc (green-entity new-entity)
                               :external_ids ["gmrvgrepost"])
                  :headers {"Authorization" "player-3-token"})

            player-3-entity-repost2
            (post (format "ctia/%s" entity)
                  :body (assoc (green-entity new-entity)
                               :external_ids ["gmrvgrepost2"])
                  :headers {"Authorization" "player-3-token"})]

        (testing "green test setup is successful"
          (is (= 201 (:status player-1-entity-post))
              (str "HTTP status should be 201 "
                   (pr-str player-1-entity-post)))
          (is (= 201 (:status player-2-entity-post))
              (str "HTTP status should be 201 "
                   (pr-str player-2-entity-post)))
          (is (= 201 (:status player-3-entity-post))
              (str "HTTP status should be 201 "
                   (pr-str player-3-entity-post))))

        (crud-access-control-test
         (into args
               {:player-1-creation player-1-entity-post
                :player-2-creation player-2-entity-post
                :player-3-creation player-3-entity-post

                :player-2-1-expected-read-statuses forbidden-statuses
                :player-2-1-expected-write-statuses forbidden-statuses

                :player-2-3-expected-read-statuses allowed-statuses
                :player-2-3-expected-write-statuses allowed-statuses

                :player-3-1-expected-read-statuses forbidden-statuses
                :player-3-1-expected-write-statuses forbidden-statuses

                :list-query "external_ids:gmrvgpost"
                :player-1-expected-entity-list [(:parsed-body player-1-entity-post)]

                :player-2-expected-entity-list [(:parsed-body player-2-entity-post)
                                                (:parsed-body player-3-entity-post)]

                :player-3-expected-entity-list [(:parsed-body player-2-entity-post)
                                                (:parsed-body player-3-entity-post)]}))

        ;; player1 and player2 repost deleted entities
        (is (= 201 (:status player-1-entity-repost)))
        (is (= 201 (:status player-2-entity-repost)))
        (is (= 201 (:status player-3-entity-repost)))

        (when can-update?
          (let [player-1-entity-update
                (put (format "ctia/%s/%s"
                             entity
                             (-> player-1-entity-repost
                                 :parsed-body
                                 :id
                                 id/long-id->id
                                 :short-id))
                     :body (assoc (dissoc (:parsed-body player-1-entity-repost) :id)
                                  :authorized_users ["player2"])
                     :headers {"Authorization" "player-1-token"})]

            ;; player 1 allows player 2 (if record is updatable)
            (crud-access-control-test
             (into args
                   {:player-1-creation player-1-entity-update
                    :player-2-creation player-2-entity-repost
                    :player-3-creation player-3-entity-repost

                    :player-2-1-expected-read-statuses allowed-statuses
                    :player-2-1-expected-write-statuses allowed-statuses

                    :player-2-3-expected-read-statuses allowed-statuses
                    :player-2-3-expected-write-statuses allowed-statuses

                    :player-3-1-expected-read-statuses forbidden-statuses
                    :player-3-1-expected-write-statuses forbidden-statuses

                    :list-query "external_ids:gmrvgrepost"
                    :player-1-expected-entity-list [(:parsed-body player-1-entity-update)]

                    :player-2-expected-entity-list [(:parsed-body player-1-entity-update)
                                                    (:parsed-body player-2-entity-repost)
                                                    (:parsed-body player-3-entity-repost)]

                    :player-3-expected-entity-list [(:parsed-body player-2-entity-repost)
                                                    (:parsed-body player-3-entity-repost)]}))))

        ;; player1 and player2 repost deleted entities
        (is (= 201 (:status player-1-entity-repost2)))
        (is (= 201 (:status player-2-entity-repost2)))
        (is (= 201 (:status player-3-entity-repost2)))

        (when can-update?
          (let [player-1-entity-update2
                (put (format "ctia/%s/%s"
                             entity
                             (-> player-1-entity-repost2
                                 :parsed-body
                                 :id
                                 id/long-id->id
                                 :short-id))
                     :body (assoc (dissoc (:parsed-body player-1-entity-repost2) :id)
                                  :authorized_groups ["bargroup"])
                     :headers {"Authorization" "player-1-token"})]
            ;; player 1 allows player 2-3 group (if record is updatable)
            (crud-access-control-test
             (into args
                   {:player-1-creation player-1-entity-update2
                    :player-2-creation player-2-entity-repost2
                    :player-3-creation player-3-entity-repost2

                    :player-2-1-expected-read-statuses allowed-statuses
                    :player-2-1-expected-write-statuses allowed-statuses

                    :player-2-3-expected-read-statuses allowed-statuses
                    :player-2-3-expected-write-statuses allowed-statuses

                    :player-3-1-expected-read-statuses allowed-statuses
                    :player-3-1-expected-write-statuses allowed-statuses

                    :list-query "external_ids:gmrvgrepost2"
                    :player-1-expected-entity-list [(:parsed-body player-1-entity-update2)]

                    :player-2-expected-entity-list [(:parsed-body player-1-entity-update2)
                                                    (:parsed-body player-2-entity-repost2)
                                                    (:parsed-body player-3-entity-repost2)]

                    :player-3-expected-entity-list [(:parsed-body player-1-entity-update2)
                                                    (:parsed-body player-2-entity-repost2)
                                                    (:parsed-body player-3-entity-repost2)]}))))))))

(defn test-access-control-entity-tlp-green
  [{:keys [entity
           new-entity
           can-update?]
    :as args}]

  (testing "TLP Green"
    (let [player-1-entity-post
          (post (format "ctia/%s" entity)
                :body (assoc (green-entity new-entity)
                             :external_ids ["gpost"])
                :headers {"Authorization" "player-1-token"})

          player-1-entity-repost
          (post (format "ctia/%s" entity)
                :body (assoc (green-entity new-entity)
                             :external_ids ["grepost"])
                :headers {"Authorization" "player-1-token"})

          player-1-entity-repost2
          (post (format "ctia/%s" entity)
                :body (assoc (green-entity new-entity)
                             :external_ids ["grepost2"])
                :headers {"Authorization" "player-1-token"})

          player-2-entity-post
          (post (format "ctia/%s" entity)
                :body (assoc (green-entity new-entity)
                             :external_ids ["gpost"])
                :headers {"Authorization" "player-2-token"})

          player-2-entity-repost
          (post (format "ctia/%s" entity)
                :body (assoc (green-entity new-entity)
                             :external_ids ["grepost"])
                :headers {"Authorization" "player-2-token"})

          player-2-entity-repost2
          (post (format "ctia/%s" entity)
                :body (assoc (green-entity new-entity)
                             :external_ids ["grepost2"])
                :headers {"Authorization" "player-2-token"})

          player-3-entity-post
          (post (format "ctia/%s" entity)
                :body (assoc (green-entity new-entity)
                             :external_ids ["gpost"])
                :headers {"Authorization" "player-3-token"})

          player-3-entity-repost
          (post (format "ctia/%s" entity)
                :body (assoc (green-entity new-entity)
                             :external_ids ["grepost"])
                :headers {"Authorization" "player-3-token"})

          player-3-entity-repost2
          (post (format "ctia/%s" entity)
                :body (assoc (green-entity new-entity)
                             :external_ids ["grepost2"])
                :headers {"Authorization" "player-3-token"})]

      (testing "green test setup is successful"
        (is (= 201 (:status player-1-entity-post))
            (str "HTTP status should be 201 "
                 (pr-str player-1-entity-post)))
        (is (= 201 (:status player-2-entity-post))
            (str "HTTP status should be 201 "
                 (pr-str player-2-entity-post)))
        (is (= 201 (:status player-3-entity-post))
            (str "HTTP status should be 201 "
                 (pr-str player-3-entity-post))))

      (crud-access-control-test
       (into args
             {:player-1-creation player-1-entity-post
              :player-2-creation player-2-entity-post
              :player-3-creation player-3-entity-post

              :player-2-1-expected-read-statuses allowed-statuses
              :player-2-1-expected-write-statuses forbidden-statuses

              :player-2-3-expected-read-statuses allowed-statuses
              :player-2-3-expected-write-statuses allowed-statuses

              :player-3-1-expected-read-statuses allowed-statuses
              :player-3-1-expected-write-statuses forbidden-statuses

              :list-query "external_ids:gpost"
              :player-1-expected-entity-list [(:parsed-body player-1-entity-post)
                                              (:parsed-body player-2-entity-post)
                                              (:parsed-body player-3-entity-post)]

              :player-2-expected-entity-list [(:parsed-body player-1-entity-post)
                                              (:parsed-body player-2-entity-post)
                                              (:parsed-body player-3-entity-post)]

              :player-3-expected-entity-list [(:parsed-body player-1-entity-post)
                                              (:parsed-body player-2-entity-post)
                                              (:parsed-body player-3-entity-post)]}))

      ;; player1 and player2 repost deleted entities
      (is (= 201 (:status player-1-entity-repost)))
      (is (= 201 (:status player-2-entity-repost)))
      (is (= 201 (:status player-3-entity-repost)))

      (when can-update?
        (let [player-1-entity-update
              (put (format "ctia/%s/%s"
                           entity
                           (-> player-1-entity-repost
                               :parsed-body
                               :id
                               id/long-id->id
                               :short-id))
                   :body (assoc (dissoc (:parsed-body player-1-entity-repost) :id)
                                :authorized_users ["player2"])
                   :headers {"Authorization" "player-1-token"})]

          ;; player 1 allows player 2 (if record is updatable)
          (crud-access-control-test
           (into args
                 {:player-1-creation player-1-entity-update
                  :player-2-creation player-2-entity-repost
                  :player-3-creation player-3-entity-repost

                  :player-2-1-expected-read-statuses allowed-statuses
                  :player-2-1-expected-write-statuses allowed-statuses

                  :player-2-3-expected-read-statuses allowed-statuses
                  :player-2-3-expected-write-statuses allowed-statuses

                  :player-3-1-expected-read-statuses allowed-statuses
                  :player-3-1-expected-write-statuses forbidden-statuses

                  :list-query "external_ids:grepost"
                  :player-1-expected-entity-list [(:parsed-body player-1-entity-update)
                                                  (:parsed-body player-2-entity-repost)
                                                  (:parsed-body player-3-entity-repost)]

                  :player-2-expected-entity-list [(:parsed-body player-1-entity-update)
                                                  (:parsed-body player-2-entity-repost)
                                                  (:parsed-body player-3-entity-repost)]

                  :player-3-expected-entity-list [(:parsed-body player-1-entity-update)
                                                  (:parsed-body player-2-entity-repost)
                                                  (:parsed-body player-3-entity-repost)]}))))

      ;; player1 and player2 repost deleted entities
      (is (= 201 (:status player-1-entity-repost2)))
      (is (= 201 (:status player-2-entity-repost2)))
      (is (= 201 (:status player-3-entity-repost2)))

      (when can-update?
        (let [player-1-entity-update2
              (put (format "ctia/%s/%s"
                           entity
                           (-> player-1-entity-repost2
                               :parsed-body
                               :id
                               id/long-id->id
                               :short-id))
                   :body (assoc (dissoc (:parsed-body player-1-entity-repost2) :id)
                                :authorized_groups ["bargroup"])
                   :headers {"Authorization" "player-1-token"})]
          ;; player 1 allows player 2-3 group (if record is updatable)

          (crud-access-control-test
           (into args
                 {:player-1-creation player-1-entity-update2
                  :player-2-creation player-2-entity-repost2
                  :player-3-creation player-3-entity-repost2

                  :player-2-1-expected-read-statuses allowed-statuses
                  :player-2-1-expected-write-statuses allowed-statuses

                  :player-2-3-expected-read-statuses allowed-statuses
                  :player-2-3-expected-write-statuses allowed-statuses

                  :player-3-1-expected-read-statuses allowed-statuses
                  :player-3-1-expected-write-statuses forbidden-statuses

                  :list-query "external_ids:grepost2"
                  :player-1-expected-entity-list [(:parsed-body player-1-entity-update2)
                                                  (:parsed-body player-2-entity-repost2)
                                                  (:parsed-body player-3-entity-repost2)]

                  :player-2-expected-entity-list [(:parsed-body player-1-entity-update2)
                                                  (:parsed-body player-2-entity-repost2)
                                                  (:parsed-body player-3-entity-repost2)]

                  :player-3-expected-entity-list [(:parsed-body player-1-entity-update2)
                                                  (:parsed-body player-2-entity-repost2)
                                                  (:parsed-body player-3-entity-repost2)]})))))))

(defn test-access-control-entity-tlp-amber
  [{:keys [entity
           new-entity
           can-update?]
    :as args}]

  (testing "TLP amber"
    (let [player-1-entity-post
          (post (format "ctia/%s" entity)
                :body (assoc (amber-entity new-entity)
                             :external_ids ["apost"])
                :headers {"Authorization" "player-1-token"})

          player-1-entity-repost
          (post (format "ctia/%s" entity)
                :body (assoc (amber-entity new-entity)
                             :external_ids ["arepost"])
                :headers {"Authorization" "player-1-token"})

          player-1-entity-repost2
          (post (format "ctia/%s" entity)
                :body (assoc (amber-entity new-entity)
                             :external_ids ["arepost2"])
                :headers {"Authorization" "player-1-token"})

          player-2-entity-post
          (post (format "ctia/%s" entity)
                :body (assoc (amber-entity new-entity)
                             :external_ids ["apost"])
                :headers {"Authorization" "player-2-token"})

          player-2-entity-repost
          (post (format "ctia/%s" entity)
                :body (assoc (amber-entity new-entity)
                             :external_ids ["arepost"])
                :headers {"Authorization" "player-2-token"})

          player-2-entity-repost2
          (post (format "ctia/%s" entity)
                :body (assoc (amber-entity new-entity)
                             :external_ids ["arepost2"])
                :headers {"Authorization" "player-2-token"})

          player-3-entity-post
          (post (format "ctia/%s" entity)
                :body (assoc (amber-entity new-entity)
                             :external_ids ["apost"])
                :headers {"Authorization" "player-3-token"})

          player-3-entity-repost
          (post (format "ctia/%s" entity)
                :body (assoc (amber-entity new-entity)
                             :external_ids ["arepost"])
                :headers {"Authorization" "player-3-token"})

          player-3-entity-repost2
          (post (format "ctia/%s" entity)
                :body (assoc (amber-entity new-entity)
                             :external_ids ["arepost2"])
                :headers {"Authorization" "player-3-token"})]

      (testing "amber test setup is successful"
        (is (= 201 (:status player-1-entity-post)))
        (is (= 201 (:status player-2-entity-post)))
        (is (= 201 (:status player-3-entity-post))))

      (crud-access-control-test
       (into args
             {:player-1-creation player-1-entity-post
              :player-2-creation player-2-entity-post
              :player-3-creation player-3-entity-post

              :player-2-1-expected-read-statuses forbidden-statuses
              :player-2-1-expected-write-statuses forbidden-statuses

              :player-2-3-expected-read-statuses allowed-statuses
              :player-2-3-expected-write-statuses allowed-statuses

              :player-3-1-expected-read-statuses forbidden-statuses
              :player-3-1-expected-write-statuses forbidden-statuses

              :list-query "external_ids:apost"
              :player-1-expected-entity-list [(:parsed-body player-1-entity-post)]

              :player-2-expected-entity-list [(:parsed-body player-2-entity-post)
                                              (:parsed-body player-3-entity-post)]

              :player-3-expected-entity-list [(:parsed-body player-2-entity-post)
                                              (:parsed-body player-3-entity-post)]}))

      ;; player1 and player2 repost deleted entities
      (is (= 201 (:status player-1-entity-repost)))
      (is (= 201 (:status player-2-entity-repost)))
      (is (= 201 (:status player-3-entity-repost)))

      (when can-update?
        (let [player-1-entity-update
              (put (format "ctia/%s/%s"
                           entity
                           (-> player-1-entity-repost
                               :parsed-body
                               :id
                               id/long-id->id
                               :short-id))
                   :body (assoc (dissoc (:parsed-body player-1-entity-repost) :id)
                                :authorized_users ["player2"])
                   :headers {"Authorization" "player-1-token"})]

          ;; player 1 allows player 2 (if record is updatable)
          (crud-access-control-test
           (into args
                 {:player-1-creation player-1-entity-update
                  :player-2-creation player-2-entity-repost
                  :player-3-creation player-3-entity-repost

                  :player-2-1-expected-read-statuses allowed-statuses
                  :player-2-1-expected-write-statuses allowed-statuses

                  :player-2-3-expected-read-statuses allowed-statuses
                  :player-2-3-expected-write-statuses allowed-statuses

                  :player-3-1-expected-read-statuses forbidden-statuses
                  :player-3-1-expected-write-statuses forbidden-statuses

                  :list-query "external_ids:arepost"
                  :player-1-expected-entity-list [(:parsed-body player-1-entity-update)]

                  :player-2-expected-entity-list [(:parsed-body player-1-entity-update)
                                                  (:parsed-body player-2-entity-repost)
                                                  (:parsed-body player-3-entity-repost)]

                  :player-3-expected-entity-list [(:parsed-body player-2-entity-repost)
                                                  (:parsed-body player-3-entity-repost)]}))))

      ;; player1 and player2 repost deleted entities
      (is (= 201 (:status player-1-entity-repost2)))
      (is (= 201 (:status player-2-entity-repost2)))
      (is (= 201 (:status player-3-entity-repost2)))

      (when can-update?
        (let [player-1-entity-update2
              (put (format "ctia/%s/%s"
                           entity
                           (-> player-1-entity-repost2
                               :parsed-body
                               :id
                               id/long-id->id
                               :short-id))
                   :body (assoc (dissoc (:parsed-body player-1-entity-repost2) :id)
                                :authorized_groups ["bargroup"])
                   :headers {"Authorization" "player-1-token"})]

          ;; player 1 allows player 2-3 group (if record is updatable)
          (crud-access-control-test
           (into args
                 {:player-1-creation player-1-entity-update2
                  :player-2-creation player-2-entity-repost2
                  :player-3-creation player-3-entity-repost2

                  :player-2-1-expected-read-statuses allowed-statuses
                  :player-2-1-expected-write-statuses allowed-statuses

                  :player-2-3-expected-read-statuses allowed-statuses
                  :player-2-3-expected-write-statuses allowed-statuses

                  :player-3-1-expected-read-statuses allowed-statuses
                  :player-3-1-expected-write-statuses allowed-statuses

                  :list-query "external_ids:arepost2"
                  :player-1-expected-entity-list [(:parsed-body player-1-entity-update2)]

                  :player-2-expected-entity-list [(:parsed-body player-1-entity-update2)
                                                  (:parsed-body player-2-entity-repost2)
                                                  (:parsed-body player-3-entity-repost2)]

                  :player-3-expected-entity-list [(:parsed-body player-1-entity-update2)
                                                  (:parsed-body player-2-entity-repost2)
                                                  (:parsed-body player-3-entity-repost2)]})))))))


(defn test-access-control-entity-tlp-red
  [{:keys [entity
           new-entity
           can-update?]
    :as args}]

  (testing "TLP Red"
    (let [player-1-entity-post
          (post (format "ctia/%s" entity)
                :body (assoc (red-entity new-entity)
                             :external_ids ["rpost"])
                :headers {"Authorization" "player-1-token"})

          player-1-entity-repost
          (post (format "ctia/%s" entity)
                :body (assoc (red-entity new-entity)
                             :external_ids ["rrepost"])
                :headers {"Authorization" "player-1-token"})

          player-1-entity-repost2
          (post (format "ctia/%s" entity)
                :body (assoc (red-entity new-entity)
                             :external_ids ["rrepost2"])
                :headers {"Authorization" "player-1-token"})

          player-2-entity-post
          (post (format "ctia/%s" entity)
                :body (assoc (red-entity new-entity)
                             :external_ids ["rpost"])
                :headers {"Authorization" "player-2-token"})

          player-2-entity-repost
          (post (format "ctia/%s" entity)
                :body (assoc (red-entity new-entity)
                             :external_ids ["rrepost"])
                :headers {"Authorization" "player-2-token"})

          player-2-entity-repost2
          (post (format "ctia/%s" entity)
                :body (assoc (red-entity new-entity)
                             :external_ids ["rrepost2"])
                :headers {"Authorization" "player-2-token"})

          player-3-entity-post
          (post (format "ctia/%s" entity)
                :body (assoc (red-entity new-entity)
                             :external_ids ["rpost"])
                :headers {"Authorization" "player-3-token"})

          player-3-entity-repost
          (post (format "ctia/%s" entity)
                :body (assoc (red-entity new-entity)
                             :external_ids ["rrepost"])
                :headers {"Authorization" "player-3-token"})

          player-3-entity-repost2
          (post (format "ctia/%s" entity)
                :body (assoc (red-entity new-entity)
                             :external_ids ["rrepost2"])
                :headers {"Authorization" "player-3-token"})]

      (testing "red test setup is successful"
        (is (= 201 (:status player-1-entity-post)))
        (is (= 201 (:status player-2-entity-post)))
        (is (= 201 (:status player-3-entity-post))))

      (crud-access-control-test
       (into args
             {:player-1-creation player-1-entity-post
              :player-2-creation player-2-entity-post
              :player-3-creation player-3-entity-post

              :player-2-1-expected-read-statuses forbidden-statuses
              :player-2-1-expected-write-statuses forbidden-statuses

              :player-2-3-expected-read-statuses forbidden-statuses
              :player-2-3-expected-write-statuses forbidden-statuses

              :player-3-1-expected-read-statuses forbidden-statuses
              :player-3-1-expected-write-statuses forbidden-statuses

              :list-query "external_ids:rpost"
              :player-1-expected-entity-list [(:parsed-body player-1-entity-post)]

              :player-2-expected-entity-list [(:parsed-body player-2-entity-post)]

              :player-3-expected-entity-list [(:parsed-body player-3-entity-post)]}))

      ;; player1 and player2 repost deleted entities
      (is (= 201 (:status player-1-entity-repost)))
      (is (= 201 (:status player-2-entity-repost)))
      (is (= 201 (:status player-3-entity-repost)))

      (when can-update?
        (let [player-1-entity-update
              (put (format "ctia/%s/%s"
                           entity
                           (-> player-1-entity-repost
                               :parsed-body
                               :id
                               id/long-id->id
                               :short-id))
                   :body (assoc (dissoc (:parsed-body player-1-entity-repost) :id)
                                :authorized_users ["player2"])
                   :headers {"Authorization" "player-1-token"})]

          ;; player 1 allows player 2 (if record is updatable)
          (crud-access-control-test
           (into args
                 {:player-1-creation player-1-entity-update
                  :player-2-creation player-2-entity-repost
                  :player-3-creation player-3-entity-repost

                  :player-2-1-expected-read-statuses allowed-statuses
                  :player-2-1-expected-write-statuses allowed-statuses

                  :player-2-3-expected-read-statuses forbidden-statuses
                  :player-2-3-expected-write-statuses forbidden-statuses

                  :player-3-1-expected-read-statuses forbidden-statuses
                  :player-3-1-expected-write-statuses forbidden-statuses

                  :list-query "external_ids:rrepost"
                  :player-1-expected-entity-list [(:parsed-body player-1-entity-update)]

                  :player-2-expected-entity-list [(:parsed-body player-1-entity-update)
                                                  (:parsed-body player-2-entity-repost)]

                  :player-3-expected-entity-list [(:parsed-body player-3-entity-repost)]}))))

      ;; player1 and player2 repost deleted entities
      (is (= 201 (:status player-1-entity-repost2)))
      (is (= 201 (:status player-2-entity-repost2)))
      (is (= 201 (:status player-3-entity-repost2)))

      (when can-update?
        (let [player-1-entity-update2
              (put (format "ctia/%s/%s"
                           entity
                           (-> player-1-entity-repost2
                               :parsed-body
                               :id
                               id/long-id->id
                               :short-id))
                   :body (assoc (dissoc (:parsed-body player-1-entity-repost2) :id)
                                :authorized_groups ["bargroup"])
                   :headers {"Authorization" "player-1-token"})]
          ;; player 1 allows player 2-3 group (if record is updatable)
          (crud-access-control-test
           (into args
                 {:player-1-creation player-1-entity-update2
                  :player-2-creation player-2-entity-repost2
                  :player-3-creation player-3-entity-repost2

                  :player-2-1-expected-read-statuses allowed-statuses
                  :player-2-1-expected-write-statuses allowed-statuses

                  :player-2-3-expected-read-statuses forbidden-statuses
                  :player-2-3-expected-write-statuses forbidden-statuses

                  :player-3-1-expected-read-statuses allowed-statuses
                  :player-3-1-expected-write-statuses allowed-statuses

                  :list-query "external_ids:rrepost2"
                  :player-1-expected-entity-list [(:parsed-body player-1-entity-update2)
                                                  (:parsed-body player-1-entity-update2)]

                  :player-2-expected-entity-list [(:parsed-body player-1-entity-update2)
                                                  (:parsed-body player-2-entity-repost2)]

                  :player-3-expected-entity-list [(:parsed-body player-1-entity-update2)
                                                  (:parsed-body player-3-entity-repost2)]})))))))

(defn test-access-control-tlp-settings
  [entity new-entity]
  (testing "TLP Settings Enforcement"
    (swap! (p/global-properties-atom) assoc-in
           [:ctia :access-control]
           {:default-tlp "amber"
            :min-tlp "amber"})

    (let [{status-default-tlp :status
           body-default-tlp :parsed-body}
          (post (format "ctia/%s" entity)
                :body (dissoc new-entity :tlp)
                :headers {"Authorization" "player-1-token"})
          {status-disallowed-tlp :status
           body-disallowed-tlp :parsed-body}
          (post (format "ctia/%s" entity)
                :body (assoc new-entity :tlp "white")
                :headers {"Authorization" "player-1-token"})]

      (is (= 201 status-default-tlp))
      (is (= "amber" (:tlp body-default-tlp)))


      (is (= 400 status-disallowed-tlp))
      (is (= "Invalid document TLP white, allowed TLPs are: amber,red"
             (:message body-disallowed-tlp))))))

(defn access-control-test
  [entity
   new-entity
   can-update?
   can-delete?]
  (helpers/set-capabilities! "player1"
                             ["foogroup"]
                             "user"
                             all-capabilities)
  (helpers/set-capabilities! "player2"
                             ["bargroup"]
                             "user"
                             all-capabilities)
  (helpers/set-capabilities! "player3"
                             ["bargroup"]
                             "user"
                             all-capabilities)

  (whoami-helpers/set-whoami-response "player-1-token"
                                      "player1"
                                      "foogroup"
                                      "user")
  (whoami-helpers/set-whoami-response "player-2-token"
                                      "player2"
                                      "bargroup"
                                      "user")
  (whoami-helpers/set-whoami-response "player-3-token"
                                      "player3"
                                      "bargroup"
                                      "user")

  (test-access-control-entity-tlp-green
   {:entity entity
    :new-entity new-entity
    :can-update? can-update?
    :can-delete? can-delete?})

  (test-access-control-entity-tlp-green-max-record-visibility-group
   {:entity entity
    :new-entity new-entity
    :can-update? can-update?
    :can-delete? can-delete?})

  (test-access-control-entity-tlp-amber
   {:entity entity
    :new-entity new-entity
    :can-update? can-update?
    :can-delete? can-delete?})

  (test-access-control-entity-tlp-red
   {:entity entity
    :new-entity new-entity
    :can-update? can-update?
    :can-delete? can-delete?})

  (test-access-control-tlp-settings entity
                                    new-entity))
