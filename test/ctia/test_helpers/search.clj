(ns ctia.test-helpers.search
  (:require [clojure.set :as set]
            [clojure.string :as str]
            [clojure.test :refer [is testing]]
            [clojure.tools.logging :refer [log*]]
            [ctim.domain.id :refer [long-id->id]]
            [clj-momo.lib.clj-time.coerce :as tc]
            [ctia.test-helpers.core :refer [POST-bulk DELETE GET POST]])
  (:import [java.util UUID]))

(defn unique-word
  [base-word]
  (-> (UUID/randomUUID)
      str
      (str/replace "-" "")
      (->> (str base-word))))

(defn create-doc
  [app entity doc]
  (POST app
        (str "ctia/" (name entity))
        :body doc
        :headers {"Authorization" "45c1f5e3f05d0"}))

(defn delete-doc
  [app entity full-id]
  (let [short-id (:short-id (long-id->id full-id))]
    (DELETE app
            (format "ctia/%s/%s" (name entity) short-id)
            :accept :json
            :headers {"Authorization" "45c1f5e3f05d0"})))

(defn delete-search
  [app entity query-params]
  (let [delete-search-uri (format "ctia/%s/search" (name entity))]
    (DELETE app
            delete-search-uri
            :headers {"Authorization" "45c1f5e3f05d0"}
            :query-params query-params)))

(defn search-raw
  [app entity query-params]
  (let [search-uri (format "ctia/%s/search" (name entity))]
    (GET app
         search-uri
         :headers {"Authorization" "45c1f5e3f05d0"}
         :query-params query-params)))

(defn search-text
  [app entity text]
  (search-raw app entity {:query text}))

(defn search-ids
  [app entity query]
  (->> (search-text app entity query)
       :parsed-body
       (map :id)
       set))

(defn count-raw
  [app entity query-params]
  (let [count-uri (format "ctia/%s/search/count" (name entity))]
    (GET app
         count-uri
         :headers {"Authorization" "45c1f5e3f05d0"}
         :query-params query-params)))

(defn count-text
  [app entity text]
  (count-raw app entity {:query text}))

(defn test-describable-search
  [{:keys [app entity example get-in-config]}]
  (let [;; generate matched and unmatched terms / entities
        capital-word (unique-word "CAPITAL")
        base-possessive-word "possessive"
        possessive-word (str base-possessive-word "'s")
        base-domain (unique-word "cisco")
        domain-word (format "www.%s.com" base-domain)
        url-word (unique-word (format "http://%s/" domain-word))
        ip "127.0.0.1"
        matched-text (format "a %s countries, %s words, %s, %s j2ee, property of attack"
                             capital-word
                             possessive-word
                             url-word
                             ip)
        unmatched-text "thiswillnotbe matchedbyourtest queriesandprobablyunique"
        matched-entity (-> (assoc example
                                  :description matched-text)
                           (dissoc :id))
        ;; insert entities
        unmatched-entity (-> (assoc example
                                    :description unmatched-text)
                             (dissoc :id))
        ;; 3 matched entities
        matched-ids (->> #(create-doc app entity matched-entity)
                         (repeatedly 3)
                         (map (comp :id :parsed-body))
                         set)
        ;; 2 unmatched entities
        unmatched-ids (->> #(create-doc app entity unmatched-entity)
                           (repeatedly 2)
                           (map (comp :id :parsed-body))
                           set)
        default_operator (or (get-in-config [:ctia :store :es (keyword entity) :default_operator])
                             (get-in-config [:ctia :store :es :default :default_operator])
                             "AND")
        partially-matched-text (format "%s %s"
                                       "word"
                                       (unique-word "unmatched"))]
    (if (= "AND" default_operator)
      (testing
          (format "AND is default_operator for %s, it must match all words!"
                  entity)
        (let [searched-ids (search-ids app entity partially-matched-text)
              counted (:parsed-body (count-text app entity partially-matched-text))]
          (is (empty? searched-ids))
          (is (= 0 counted))))
      (testing
          (format "OR is default_operator for %s, it can match only one word!"
                  entity)
        (let [search-ids (search-ids app entity partially-matched-text)
              counted (:parsed-body (count-text app entity partially-matched-text))]
          (is (seq search-ids))
          (is (= (count search-ids)
                 counted)))))

    (testing "all field should match simple query"
      (let [{search-status :status
             search-body :parsed-body} (search-text app entity "word")
            {count-status :status
             count-body :parsed-body} (count-text app entity "word")]
        (is (= 200 search-status count-status))
        (is (= matched-ids (set (map :id search-body))))
        (is (= (count matched-ids) count-body))))

    (testing "lowercase filter should be properly applied on describable fields"
      (let [all-upper-ids (search-ids app entity capital-word)
            all-lower-ids (search-ids app entity (str/lower-case capital-word))
            description-upper-ids (search-ids app entity (format "description:(%s)" capital-word))
            description-lower-ids (search-ids app entity (format "description:(%s)" capital-word))]
        (is (= matched-ids
               all-upper-ids
               all-lower-ids
               description-upper-ids
               description-lower-ids))))

    (testing "plural/single analysis should be properly applied on describable fields"
      (is (= matched-ids (search-ids app entity "word words wordS")))
      (is (= matched-ids (search-ids app entity "country countries countri")))
      (is (empty? (search-ids app entity "we")))
      (is (empty? (search-ids app entity "wor"))))

    ;; test stop word filtering
    #_(is (= matched-ids (search-ids app entity "the word"))
        "\"the\" is not in text but should be filtered out from query as a stop word")
    (is (empty? (search-ids app entity "description:\"property that attack\""))
        "search_quote analyzer in describabble fields shall preserve stop words")
    (is (empty? (search-ids app entity "\"property attack\""))
        "filtering out stop words preserves tokens positions, thus \"property\" that does not precede \"attack\" with distance 1 as expected in query \"property attack\"")
    ;; test possessive filtering
    (let [all-ids-1 (search-ids app entity possessive-word)
          all-ids-2 (search-ids app entity base-possessive-word)
          description-ids-1 (search-ids app entity (format "description:(%s)" possessive-word))
          description-ids-2 (search-ids app entity (format "description:(%s)" base-possessive-word))]
      (is (= matched-ids
             all-ids-1
             all-ids-2
             description-ids-1
             description-ids-2)
          "possessive filter should be properly applied on describable fields")

      (is (= matched-ids
             (search-ids app entity (str possessive-word " words")))
          "possessive filter should be applied before stop word filter to remove s"))

    ;; test search on url
    (let [escaped-url (-> (str/replace url-word "/" "\\/")
                          (str/replace ":" "\\:"))
          found-ids-escaped (search-ids app entity escaped-url)]

      (with-redefs [log* (fn [& _] nil)]
        (is (= 400 (:status (search-text app entity url-word)))
            "the following characters are reserved in lucene queries [+ - = && || > < ! ( ) { } [ ] ^ \" ~ * ? : \\ /], thus queries that do not escape them should fail"))

      (is (= matched-ids
             (search-ids app entity (format "\"%s\"" url-word)))
          "surrounding with parenthesis should avoid parsing errors and forces to have all words in exact order without interleaved word")
      (if (= "AND" default_operator)
        (is (= matched-ids found-ids-escaped)
            "escaping reserved characters should avoid parsing errors and preserve behavior of AND")
        #_(is (set/subset? (set/union matched-ids unmatched-ids)
                         found-ids-escaped)
            ;; OR could match other test documents matching "http"
            "escaping reserved characters should avoid parsing errors and preserve behavior of OR"))
      (is (= matched-ids
             (search-ids app entity (str "description:" domain-word)))
          "tokenization of :description field should split urls on characters like \\/ \\: and thus enables search on url components")
      (is (= matched-ids
             (search-ids app entity (str "description:" base-domain))
             (search-ids app entity base-domain))
          "word delimiter filtering of :description should split tokens on \\. and thus enables search on domain components"))

    ;; test number fitering
    (is (= matched-ids
           (search-ids app entity "description:127.0.0.1")
           (search-ids app entity "127.0.0.1"))
        "word delimiter filtering should preserve ips")
    (is (empty? (search-ids app entity "127"))
        "word delimiter filtering should preserve ips")
    (testing "word delimiter filtering should not split words on numbers"
      (is (= matched-ids
             (search-ids app entity "description:j2ee")
             (search-ids app entity "j2ee")))
      (is (empty? (search-ids app entity "j OR ee"))))

    ;; clean
    (doseq [full-id (concat matched-ids unmatched-ids)]
      (delete-doc app entity full-id))))

(defn ensure-one-document
  [f app example entity & args]
  (let [{{full-id :id} :parsed-body} (create-doc app entity (dissoc example :id))]
    (apply f app entity args)
    (delete-doc app entity full-id)))

(defn test-non-describable-search
  [{:keys [app entity query query-field]}]
  (testing "search term filter"
    (let [query-string (format "%s:\"%s\"" (name query-field) query)]
      (let [{search-status :status
             search-body :parsed-body} (search-text app entity query-string)
            {count-status :status
             count-body :parsed-body} (count-text app entity query-string)]
        (is (= 200 search-status count-status))
        (is (pos? (count search-body)))
        (is (= (count search-body) count-body))
        (doseq [res search-body]
          (is (= query (get res query-field))
              "query term must properly match values")))
      (with-redefs [log* (fn [& _] nil)]
        ;; avoid unnecessary verbosity
        (let [{search-status :status} (search-text app entity "2607:f0d0:1002:0051:0000:0000:0000:0004")
              {count-status :status} (count-text app entity "2607:f0d0:1002:0051:0000:0000:0000:0004")]
          (is (= 400 search-status count-status))))

      (let [query-params {"query" query-string
                          "tlp"   "red"}
            {search-status :status
             search-body :parsed-body} (search-raw app entity query-params)
            {count-status :status
             count-body :parsed-body} (count-raw app entity query-params)]
        (is (= 200 search-status count-status))
        (is (= 0 (count search-body) count-body)
            "filters must be applied, and should discriminate"))

      (let [query-params {:query query-string
                          :tlp "green"}
            {search-status :status
             search-body :parsed-body} (search-raw app entity query-params)
            {count-status :status
             count-body :parsed-body} (count-raw app entity query-params)
            matched-fields {:tlp "green"
                            (keyword query-field) query}]
        (is (= 200 search-status count-status))
        (is (<= 1 (count search-body)))
        (is (= (count search-body) count-body))
        (doseq [res search-body]
          (is (= (select-keys res [(keyword query-field) :tlp])
                 matched-fields)
              "filters must be applied, and match properly"))))))

(defn test-filter-by-id
  [app entity]
  (let [{:keys [parsed-body status]} (search-text app entity "*")
        first-entity (some-> parsed-body first)]
    (is (= 200 status))
    (is (some? first-entity))
    (testing "filter by long ID"
      (let [response
            (search-raw app entity {"id" (:id first-entity)})]
        (is (= 200 (:status response)))
        (is (= first-entity (some-> response :parsed-body first)))))
    (testing "filter by short ID"
      (let [response
            (search-raw app entity {"id" (-> (:id first-entity)
                                             long-id->id
                                             :short-id)})]
        (is (= 200 (:status response)))
        (is (= first-entity (some-> response :parsed-body first)))))))

(defn test-date-range
  [app entity date-range expected-ids msg]
  (let [{:keys [status parsed-body]} (search-raw app entity date-range)]
    (testing msg
      (is (= 200 status))
      (is (= (set expected-ids)
             (set (map :id parsed-body)))))))

(defn test-from-to
  [app entity example]
  (testing "check date range [from, to["
    (let [;; insert first document
          new-actor (dissoc example :id :timestamp)
          {{id-1 :id timestamp-1 :timestamp} :parsed-body
           :as create-result}
          (create-doc app entity new-actor)
          date-time-1 (str (tc/to-date-time timestamp-1))

          _ (test-date-range app
                             entity
                             {:from date-time-1}
                             [id-1]
                             "date range should include from")

          _ (test-date-range app
                             entity
                             {:id id-1 ;; avoid previous results
                              :to date-time-1}
                             []
                             "date range should exclude to")

          ;; add another document
          {{id-2 :id timestamp-2 :timestamp}  :parsed-body}
          (create-doc app entity  new-actor)
          date-time-2 (str (tc/to-date-time timestamp-2))]
      (test-date-range app
                       entity
                       {:from date-time-1}
                       [id-1 id-2]
                       "date range should include from")
      (test-date-range app
                       entity
                       {:from date-time-1
                        :to date-time-2}
                       [id-1]
                       "date range should include from and exclude to")
      (delete-doc app entity id-1)
      (delete-doc app entity id-2))))

(defn test-delete-search
  [{:keys [app entity bundle-key example]}]
  (let [docs (->> (dissoc example :id)
                  (repeat 100)
                  (map #(assoc % :tlp (rand-nth ["green" "amber" "red"]))))
        {green-docs "green"
         amber-docs "amber"
         red-docs "red"} (group-by :tlp docs)
        count-fn #(:parsed-body (count-raw app entity %))
        delete-search-fn (fn [q confirm?]
                           (let [query (if (boolean? confirm?)
                                         (assoc q :REALLY_DELETE_ALL_THESE_ENTITIES confirm?)
                                         q)]
                             (-> (delete-search app entity query)
                                 :body
                                 read-string)))
        filter-green {:tlp "green"}
        filter-amber {:tlp "amber"}
        filter-red {:tlp "red"}]
    (POST-bulk app {bundle-key docs})
    (is (= 403
           (:status (delete-search app entity {:REALLY_DELETE_ALL_THESE_ENTITIES true})))
        "at least one search filter must be provided.")
    (assert (= (count green-docs)
               (count-fn filter-green)))
    (assert (= (count amber-docs)
               (count-fn filter-amber)))
    (assert (= (count red-docs)
               (count-fn filter-red)))
    (testing "delete with :REALLY_DELETE_ALL_THESE_ENTITIES set to false or nil must not delete entities but return the actual number of matched entities"
      (is (= (count green-docs)
             (delete-search-fn filter-green false)
             (delete-search-fn filter-green nil)
             (count-fn filter-green)))
      (is (= (count amber-docs)
             (delete-search-fn filter-amber false)
             (delete-search-fn filter-amber nil)
             (count-fn filter-amber)))
      (is (= (count red-docs)
             (delete-search-fn filter-red false)
             (delete-search-fn filter-red nil)
             (count-fn filter-red))))
    (testing "delete with :REALLY_DELETE_ALL_THESE_ENTITIES set to true must really delete entities"
      (is (= (count green-docs)
             (delete-search-fn filter-green true)))
      (is (= (count amber-docs)
             (delete-search-fn filter-amber true)))
      (is (= (count red-docs)
             (delete-search-fn filter-red true)))
      (is (= 0
             (count-fn filter-green)
             (count-fn filter-amber)
             (count-fn filter-red))))))

(defn test-query-string-search
  [{:keys [app entity query query-field example get-in-config]}]
  (let [{{full-id :id} :parsed-body} (create-doc app entity (dissoc example :id))]
    (if (= :description query-field)
      (test-describable-search
       {:app           app
        :entity        entity
        :example       example
        :get-in-config get-in-config})
      (test-non-describable-search
       {:app         app
        :entity      entity
        :query       query
        :query-field query-field}))
    (test-filter-by-id app entity)
    (test-from-to app entity example)
    (delete-doc app entity full-id)))
