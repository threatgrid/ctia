(ns ctia.test-helpers.search
  (:require [clojure.string :as str]
            [clojure.test :refer [is testing]]
            [clojure.tools.logging :refer [log*]]
            [ctim.domain.id :refer [long-id->id]]
            [ctia.properties :refer [properties]]
            [clj-momo.lib.clj-time.coerce :as tc]
            [ctia.test-helpers.core :as helpers]))

(defn unique-word
  [base-word]
  (-> (java.util.UUID/randomUUID)
      str
      (str/replace "-" "")
      (->> (str base-word))))

(defn create-doc
  [entity doc]
  (helpers/post (str "ctia/" (name entity))
        :body doc
        :headers {"Authorization" "45c1f5e3f05d0"}))

(defn delete-doc
  [entity full-id]
  (let [short-id (:short-id (long-id->id full-id))]
    (helpers/delete (format "ctia/%s/%s" (name entity) short-id)
            :headers {"Authorization" "45c1f5e3f05d0"})))

(defn search-raw
  [entity query-params]
  (let [search-uri (format "ctia/%s/search" (name entity))]
    (helpers/get search-uri
         :headers {"Authorization" "45c1f5e3f05d0"}
         :query-params query-params)))

(defn search-text
  [entity text]
  (search-raw entity {:query text}))

(defn search-ids
  [entity query]
  (->> (search-text entity query)
       :parsed-body
       (map :id)
       set))

(defn count-raw
  [entity query-params]
  (let [count-uri (format "ctia/%s/search/count" (name entity))]
    (helpers/get count-uri
                 :headers {"Authorization" "45c1f5e3f05d0"}
                 :query-params query-params)))

(defn count-text
  [entity text]
  (count-raw entity {:query text}))

(defn test-describable-search [entity example]
  (let [;; generate matched and unmatched terms / entities
        capital-word (unique-word "CAPITAL")
        base-possessive-word "possessive"
        possessive-word (str base-possessive-word "'s")
        base-possessive-word (str/replace possessive-word "'s" "")
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
        matched-ids (->> #(create-doc entity matched-entity)
                         (repeatedly 3)
                         (map (comp :id :parsed-body))
                         set)
        ;; 2 unmatched entities
        unmatched-ids (->> #(create-doc entity unmatched-entity)
                           (repeatedly 2)
                           (map (comp :id :parsed-body))
                           set)
        default_operator (or (get-in @properties [:ctia :store :es (keyword entity) :default_operator])
                             (get-in @properties [:ctia :store :es :default :default_operator])
                             "AND")
        partially-matched-text (format "%s %s"
                                       "word"
                                       (unique-word "unmatched"))]
    (if (= "AND" default_operator)
      (testing
          (format "AND is default_operator for %s, it must match all words!"
                  entity)
        (let [searched-ids (search-ids entity partially-matched-text)
              counted (:parsed-body (count-text entity partially-matched-text))]
          (is (empty? searched-ids))
          (is (= 0 counted))))
      (testing
          (format "OR is default_operator for %s, it can match only one word!"
                  entity)
        (let [search-ids (search-ids entity partially-matched-text)
              counted (:parsed-body (count-text entity partially-matched-text))]
          (is (seq search-ids))
          (is (= (count search-ids)
                 counted)))))

    (testing "all field should match simple query"
      (let [{search-status :status
             search-body :parsed-body} (search-text entity "word")
            {count-status :status
             count-body :parsed-body} (count-text entity "word")]
        (is (= 200 search-status count-status))
        (is (= matched-ids (set (map :id search-body))))
        (is (= (count matched-ids) count-body))))

    (testing "lowercase filter should be properly applied on describable fields"
      (let [all-upper-ids (search-ids entity capital-word)
            all-lower-ids (search-ids entity (str/lower-case capital-word))
            description-upper-ids (search-ids entity (format "description:(%s)" capital-word))
            description-lower-ids (search-ids entity (format "description:(%s)" capital-word))]
        (is (= matched-ids
               all-upper-ids
               all-lower-ids
               description-upper-ids
               description-lower-ids))))

    (testing "plural/single analysis should be properly applied on describable fields"
      (is (= matched-ids (search-ids entity "word words wordS")))
      (is (= matched-ids (search-ids entity "country countries countri")))
      (is (empty? (search-ids entity "we")))
      (is (empty? (search-ids entity "wor"))))

    ;; test stop word filtering
    (is (= matched-ids (search-ids entity "the word"))
        "\"the\" is not in text but should be filtered out from query as a stop word")
    (is (empty? (search-ids entity "description:\"property that attack\""))
        "search_quote analyzer in describabble fields shall preserve stop words")
    (is (empty? (search-ids entity "\"property attack\""))
        "filtering out stop words preserves tokens positions, thus \"property\" that does not precede \"attack\" with distance 1 as expected in query \"property attack\"")
    ;; test possessive filtering
    (let [all-ids-1 (search-ids entity possessive-word)
          all-ids-2 (search-ids entity base-possessive-word)
          description-ids-1 (search-ids entity (format "description:(%s)" possessive-word))
          description-ids-2 (search-ids entity (format "description:(%s)" base-possessive-word))]
      (is (= matched-ids
             all-ids-1
             all-ids-2
             description-ids-1
             description-ids-2)
          "possessive filter should be properly applied on describable fields")

      (is (= matched-ids
             (search-ids entity (str possessive-word " words")))
          "possessive filter should be applied before stop word filter to remove s"))

    ;; test search on url
    (let [escaped-url (-> (str/replace url-word "/" "\\/")
                          (str/replace ":" "\\:"))
          found-ids-escaped (search-ids entity escaped-url)]

      (with-redefs [log* (fn [& _] nil)]
        (is (= 400 (:status (search-text entity url-word)))
            "the following characters are reserved in lucene queries [+ - = && || > < ! ( ) { } [ ] ^ \" ~ * ? : \\ /], thus queries that do not escape them should fail"))

      (is (= matched-ids
             (search-ids entity (format "\"%s\"" url-word)))
          "surrounding with parenthesis should avoid parsing errors and forces to have all words in exact order without interleaved word")
      (if (= "AND" default_operator)
        (is (= matched-ids found-ids-escaped)
            "escaping reserved characters should avoid parsing errors and preserve behavior of AND")
        (is (clojure.set/subset? (clojure.set/union matched-ids unmatched-ids)
                                 found-ids-escaped)
            ;; OR could match other test documents matching "http"
            "escaping reserved characters should avoid parsing errors and preserve behavior of OR"))
      (is (= matched-ids
             (search-ids entity (str "description:" domain-word)))
          "tokenization of :description field should split urls on characters like \\/ \\: and thus enables search on url components")
      (is (= matched-ids
             (search-ids entity (str "description:" base-domain))
             (search-ids entity base-domain))
          "word delimiter filtering of :description should split tokens on \\. and thus enables search on domain components"))

    ;; test number fitering
    (is (= matched-ids
           (search-ids entity "description:127.0.0.1")
           (search-ids entity "127.0.0.1"))
        "word delimiter filtering should preserve ips")
    (is (empty? (search-ids entity "127"))
        "word delimiter filtering should preserve ips")
    (testing "word delimiter filtering should not split words on numbers"
      (is (= matched-ids
             (search-ids entity "description:j2ee")
             (search-ids entity "j2ee")))
      (is (empty? (search-ids entity "j OR ee"))))

    ;; clean
    (doseq [full-id (concat matched-ids unmatched-ids)]
      (delete-doc entity full-id))))

(defn ensure-one-document
  [f example entity & args]
  (let [{{full-id :id} :parsed-body} (create-doc entity (dissoc example :id))]
       (apply (partial f entity) args)
       (delete-doc entity full-id)))

(defn test-non-describable-search
  [entity query query-field]
  (testing "search term filter"
    (let [{search-status :status
           search-body :parsed-body} (search-text entity query)
          {count-status :status
           count-body :parsed-body} (count-text entity query)]
      (is (= 200 search-status count-status))
      (is (pos? (count search-body)))
      (is (= (count search-body) count-body))
      (doseq [res search-body]
        (is (= query (get res query-field))
            "query term must properly match values")))
    (with-redefs [log* (fn [& _] nil)]
      ;; avoid unnecessary verbosity
      (let [{search-status :status} (search-text entity "2607:f0d0:1002:0051:0000:0000:0000:0004")
            {count-status :status} (count-text entity "2607:f0d0:1002:0051:0000:0000:0000:0004")]
        (is (= 400 search-status count-status))))

    (let [query-params {"query" query
                        "tlp" "red"}
          {search-status :status
           search-body :parsed-body} (search-raw entity query-params)
          {count-status :status
           count-body :parsed-body} (count-raw entity query-params)]
      (is (= 200 search-status count-status))
      (is (= 0 (count search-body) count-body)
          "filters must be applied, and should discriminate"))

    (let [query-params {:query query
                        :tlp "green"}
          {search-status :status
           search-body :parsed-body} (search-raw entity query-params)
          {count-status :status
           count-body :parsed-body} (count-raw entity query-params)
          matched-fields {:tlp "green"
                          (keyword query-field) query}]
      (is (= 200 search-status count-status))
      (is (<= 1 (count search-body)))
      (is (= (count search-body) count-body))
      (doseq [res search-body]
        (is (= (select-keys res [(keyword query-field) :tlp])
               matched-fields)
            "filters must be applied, and match properly")))))

(defn test-filter-by-id
  [entity]
  (let [{:keys [parsed-body status]} (search-text entity "*")
        first-entity (some-> parsed-body first)]
    (is (= 200 status))
    (is (some? first-entity))
    (testing "filter by long ID"
      (let [response
            (search-raw entity {"id" (:id first-entity)})]
        (is (= 200 (:status response)))
        (is (= first-entity (some-> response :parsed-body first)))))
    (testing "filter by short ID"
      (let [response
            (search-raw entity {"id" (-> (:id first-entity)
                                         long-id->id
                                         :short-id)})]
        (is (= 200 (:status response)))
        (is (= first-entity (some-> response :parsed-body first)))))))

(defn test-date-range
  [entity date-range expected-ids msg]
  (let [{:keys [status parsed-body]} (search-raw entity date-range)]
    (testing msg
      (is (= 200 status))
      (is (= (set expected-ids)
             (set (map :id parsed-body)))))))

(defn test-from-to
  [entity example]
  (testing "check date range [from, to["
    (let [;; insert first document
          new-actor (dissoc example :id :timestamp)
          {{id-1 :id timestamp-1 :timestamp} :parsed-body
           :as create-result}
          (create-doc entity new-actor)
          date-time-1 (str (tc/to-date-time timestamp-1))

          _ (test-date-range entity
                             {:from date-time-1}
                           [id-1]
                           "date range should include from")

          _ (test-date-range entity
                             {:id id-1 ;; avoid previous results
                              :to date-time-1}
                             []
                             "date range should exclude to")

          ;; add another document
          {{id-2 :id timestamp-2 :timestamp}  :parsed-body}
          (create-doc entity  new-actor)
          date-time-2 (str (tc/to-date-time timestamp-2))]
      (test-date-range entity
                       {:from date-time-1}
                       [id-1 id-2]
                       "date range should include from")
      (test-date-range entity
                       {:from date-time-1
                        :to date-time-2}
                       [id-1]
                       "date range should include from and exclude to")
      (delete-doc entity id-1)
      (delete-doc entity id-2))))

(defn test-query-string-search
  [entity query query-field example]
  ;; only when ES store
  (when (= "es" (get-in @properties [:ctia :store (keyword entity)]))
    (if (= :description query-field)
      (test-describable-search entity example)
      (ensure-one-document test-non-describable-search
                           example
                           entity
                           query
                           query-field))
    (test-filter-by-id entity)
    (test-from-to entity example)))
