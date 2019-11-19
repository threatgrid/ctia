(ns ctia.test-helpers.search
  (:refer-clojure :exclude [get])
  (:require [clojure.test :refer [is testing]]
            [clojure.tools.logging :refer [log*]]
            [ctim.domain.id :refer [long-id->id]]
            [ctia.properties :refer [properties]]
            [ctia.test-helpers.core :as helpers :refer [get post delete]]))

(defn unique-word
  [base-word]
  (-> (java.util.UUID/randomUUID)
      str
      (clojure.string/replace "-" "")
      (->> (str base-word))))

(defn search
  ([search-uri query] (search search-uri
                              query
                              {"Authorization" "45c1f5e3f05d0"}))
  ([search-uri query headers]
   (get search-uri
        :headers headers
        :query-params {:query query})))

(defn search-ids
  ([search-uri query] (search-ids search-uri
                                  query
                                  {"Authorization" "45c1f5e3f05d0"}))
  ([search-uri query headers]
   (->> (search search-uri query headers)
        :parsed-body
        (map :id)
        set)))

(defn test-describable-search [entity example]
  (let [;; generate matched and unmatched terms / entities
        search-uri (format "ctia/%s/search" entity)
        capital-word (unique-word "CAPITAL")
        base-possessive-word "possessive"
        possessive-word (str base-possessive-word "'s")
        base-possessive-word (clojure.string/replace possessive-word "'s" "")
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
        matched-ids (->> #(post (str "ctia/" entity)
                                :body matched-entity
                                :headers {"Authorization" "45c1f5e3f05d0"})
                         (repeatedly 3)
                         (map (comp :id :parsed-body))
                         set)
        ;; 2 unmatched entities
        unmatched-ids (->> #(post (str "ctia/" entity)
                                  :body unmatched-entity
                                  :headers {"Authorization" "45c1f5e3f05d0"})
                           (repeatedly 2)
                           (map (comp :id :parsed-body))
                           set)
        default_operator (or (get-in @properties [:ctia :store :es (keyword entity) :default_operator])
                             (get-in @properties [:ctia :store :es :default :default_operator])
                             "AND")]

    (if (= "AND" default_operator)
      (is (empty? (search-ids search-uri (format "%s %s"
                                                 "word"
                                                 (unique-word "unmatched"))))
          (format "AND is default_operator for %s, it must match all words!"
                  entity))
      (is (seq (search-ids search-uri (format "%s %s"
                                              "word"
                                              (unique-word "unmatched"))))
          (format "OR is default_operator for %s, it must match all words!"
                  entity)))

    (testing "all field should match simple query"
      (let [{:keys [status parsed-body]} (is (search search-uri "word"))]
        (is (= 200 status))
        (is matched-ids (map :_id parsed-body))))

    (testing "lowercase filter should be properly applied on describable fields"
      (let [all-upper-ids (search-ids search-uri capital-word)
            all-lower-ids (search-ids search-uri (clojure.string/lower-case capital-word))
            description-upper-ids (search-ids search-uri (format "description:(%s)" capital-word))
            description-lower-ids (search-ids search-uri (format "description:(%s)" capital-word))]
        (is (= matched-ids
               all-upper-ids
               all-lower-ids
               description-upper-ids
               description-lower-ids))))

    (testing "plural/single analysis should be properly applied on describable fields"
      (is (= matched-ids (search-ids search-uri "word words wordS")))
      (is (= matched-ids (search-ids search-uri "country countries countri")))
      (is (empty? (search-ids search-uri "we")))
      (is (empty? (search-ids search-uri "wor"))))

    ;; test stop word filtering
    (is (= matched-ids (search-ids search-uri "the word"))
        "\"the\" is not in text but should be filtered out from query as a stop word")
    (is (empty? (search-ids search-uri "description:\"property that attack\""))
        "search_quote analyzer in describabble fields shall preserve stop words")
    (is (empty? (search-ids search-uri "\"property attack\""))
        "filtering out stop words preserves tokens positions, thus \"property\" that does not precede \"attack\" with distance 1 as expected in query \"property attack\"")
    ;; test possessive filtering
    (let [all-ids-1 (search-ids search-uri possessive-word)
          all-ids-2 (search-ids search-uri base-possessive-word)
          description-ids-1 (search-ids search-uri (format "description:(%s)" possessive-word))
          description-ids-2 (search-ids search-uri (format "description:(%s)" base-possessive-word))]
      (is (= matched-ids
             all-ids-1
             all-ids-2
             description-ids-1
             description-ids-2)
          "possessive filter should be properly applied on describable fields")

      (is (= matched-ids
             (search-ids search-uri (str possessive-word " words")))
          "possessive filter should be applied before stop word filter to remove s"))

    ;; test search on url
    (let [escaped-url (-> (clojure.string/replace url-word "/" "\\/")
                          (clojure.string/replace ":" "\\:"))
          found-ids-escaped (search-ids search-uri escaped-url)]

      (with-redefs [log* (fn [& _] nil)]
        (is (= 400 (:status (search search-uri url-word)))
            "the following characters are reserved in lucene queries [+ - = && || > < ! ( ) { } [ ] ^ \" ~ * ? : \\ /], thus queries that do not escape them should fail"))

      (is (= matched-ids
             (search-ids search-uri (format "\"%s\"" url-word)))
          "surrounding with parenthesis should avoid parsing errors and forces to have all words in exact order without interleaved word")
      (if (= "AND" default_operator)
        (is (= matched-ids found-ids-escaped)
            "escaping reserved characters should avoid parsing errors and preserve behavior of AND")
        (is (clojure.set/subset? (clojure.set/union matched-ids unmatched-ids)
                                 found-ids-escaped)
            ;; OR could match other test documents matching "http"
            "escaping reserved characters should avoid parsing errors and preserve behavior of OR"))
      (is (= matched-ids
             (search-ids search-uri (str "description:" domain-word)))
          "tokenization of :description field should split urls on characters like \\/ \\: and thus enables search on url components")
      (is (= matched-ids
             (search-ids search-uri (str "description:" base-domain))
             (search-ids search-uri base-domain))
          "word delimiter filtering of :description should split tokens on \\. and thus enables search on domain components"))

    ;; test number fitering
    (is (= matched-ids
           (search-ids search-uri "description:127.0.0.1")
           (search-ids search-uri "127.0.0.1"))
        "word delimiter filtering should preserve ips")
    (is (empty? (search-ids search-uri "127"))
        "word delimiter filtering should preserve ips")
    (testing "word delimiter filtering should not split words on numbers"
      (is (= matched-ids
             (search-ids search-uri "description:j2ee")
             (search-ids search-uri "j2ee")))
      (is (empty? (search-ids search-uri "j OR ee"))))

    ;; clean
    (doseq [_id (concat matched-ids unmatched-ids)]
      (let [delete-uri (->> (long-id->id _id)
                            :short-id
                            (format "ctia/%s/%s" entity))]
        (delete delete-uri
                :headers {"Authorization" "45c1f5e3f05d0"})))))

(defn test-non-describable-search
  [entity query query-field]
  (let [search-uri (format "ctia/%s/search" (name entity))]
    (testing (format "GET %s" search-uri)
      (let [response (search search-uri query)]
        (is (= 200 (:status response)))
        (is (= query (first (map query-field (:parsed-body response))))
            "query term does not works"))

      (with-redefs [log* (fn [& _] nil)]
        ;; avoid unnecessary verbosity
        (let [response (search search-uri "2607:f0d0:1002:0051:0000:0000:0000:0004")]
          (is (= 400 (:status response)))))

      (let [response (get search-uri
                          :headers {"Authorization" "45c1f5e3f05d0"}
                          :query-params {"query" query
                                         "tlp" "red"})]
        (is (= 200 (:status response)))
        (is (empty? (:parsed-body response))
            "filters should be applied, and should discriminate"))

      (let [{:keys [status parsed-body]} (get search-uri
                                              :headers {"Authorization" "45c1f5e3f05d0"}
                                              :query-params {"query" query
                                                             "tlp" "green"})]
        (is (= 200 status))
        (is (= 1 (count parsed-body))
            "filters are applied, and match properly")))))

(defn test-filter-by-id
  [entity]
  (let [search-uri (format "ctia/%s/search" (name entity))
        {:keys [parsed-body status]} (search search-uri "*")
        first-entity (some-> parsed-body first)]
    (is (= 200 status))
    (is (some? first-entity))
    (testing "filter by long ID"
      (let [response
            (get search-uri
                 :headers {"Authorization" "45c1f5e3f05d0"}
                 :query-params {"query" "*"
                                "id" (:id first-entity)})]
        (is (= 200 (:status response)))
        (is (= first-entity (some-> response :parsed-body first)))))
    (testing "filter by short ID"
      (let [response
            (get search-uri
                 :headers {"Authorization" "45c1f5e3f05d0"}
                 :query-params {"query" "*"
                                "id" (-> (:id first-entity)
                                         long-id->id
                                         :short-id)})]
        (is (= 200 (:status response)))
        (is (= first-entity (some-> response :parsed-body first)))))))

(defn test-query-string-search
  [entity query query-field example]
      ;; only when ES store
  (when (= "es" (get-in @properties [:ctia :store (keyword entity)]))
    (if (= :description query-field)
      (test-describable-search entity example)
      (test-non-describable-search entity query query-field))
    (test-filter-by-id entity)))
