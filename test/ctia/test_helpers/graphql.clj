(ns ctia.test-helpers.graphql
  (:require [clojure.test :refer [is testing]]
            [ctia.schemas.graphql.sorting :as sorting]
            [ctia.test-helpers.core :as helpers]
            [clojure.string :as str]
            [clojure.tools.logging :as log]))

(defn create-object [type obj]
  (let [{status :status
         body :parsed-body}
        (helpers/post (str "ctia/" type)
                      :body obj
                      :headers {"api_key" "45c1f5e3f05d0"})]
    (when (not= status 201)
      (throw (Exception. (format "Failed to create %s obj: %s Status: %s Response:%s"
                                 (pr-str type)
                                 (pr-str obj)
                                 status
                                 body))))
    body))

(defn query
  "Requests the GraphQL endpoint"
  [query
   variables
   operation-name]
  (let [{status :status
         body :parsed-body}
        (helpers/post "ctia/graphql"
                      :body {:query query
                             :variables variables
                             :operationName operation-name}
                      :headers {"api_key" "45c1f5e3f05d0"})]
    (into body
          {:status status})))

(defn nodes->edges
  [nodes]
  (mapv (fn [node]
          {:node node})
        nodes))

(defn sort-kw->path
  ":valid_date.start_time -> (:valid_date :start_time)"
  [kw]
  (map
   keyword
   (some-> kw
           name
           (str/split #"\."))))

(defn min-value
  "Takes the min value if the value is sequential.
  Equivalent to the min Sort mode in ElasticSearch"
  [v]
  (if (sequential? v)
    (first (sort v))
    v))

(defn by-min-value-and-id
  [sort-field-path]
  (fn [x y]
    (let [c (compare (min-value (get-in x sort-field-path))
                     (min-value (get-in y sort-field-path)))]
      (if (not= c 0)
        c
        (compare (:id x) (:id y))))))

(defn connection-sort-test
  "Test a connection with a list of sort-fields.
  It uses the $sort_field variable to specify a sort field.
  The specified query/operation-name should provide it."
  [operation-name
   graphql-query
   variables
   connection-path
   sort-fields]
  ;; Free text fields are currently not sortable. Remove them
  (doseq [sort-field (disj (set sort-fields) :reason :description :title)]
    (let [{:keys [data errors status]}
          (query graphql-query
                 variables
                 operation-name)
          connection-data (get-in data connection-path)
          sort-field-path (sort-kw->path sort-field)
          nodes-ref (->> (:nodes connection-data)
                         (sort (by-min-value-and-id sort-field-path)))
          edges-ref (->> (:edges connection-data)
                         (map :node)
                         (sort (by-min-value-and-id sort-field-path)))]
      (is (= 200 status))
      (is (empty? errors) "No errors")
      (let [{:keys [data errors status]}
            (query graphql-query
                   (into variables
                         {:sort_field (sorting/sorting-kw->enum-name sort-field)})
                   operation-name)
            connection-data (get-in data connection-path)
            nodes (:nodes connection-data)
            edges (map :node (:edges connection-data))]
        (is (= 200 status))
        (is (empty? errors) "No errors")
        (is (not-empty (:nodes connection-data))
            (str "Result not empty when sorted by " sort-field))
        (is (= nodes-ref nodes)
            (str "Nodes should be correctly sorted by " sort-field))
        (is (not-empty (:edges connection-data)))
        (is (= edges-ref edges)
            (str "Edges should be correctly sorted by " sort-field)))
      (let [{data :data}
            (query graphql-query
                   (into variables
                         {:sort_field (sorting/sorting-kw->enum-name sort-field)
                          :sort_direction "desc"})
                   operation-name)
            connection-data (get-in data connection-path)]
        (is (not= nodes-ref (:nodes connection-data))
            (str "Nodes should not be sorted the same way in asc and desc direction - "
                 sort-field))
        (is (not= nodes-ref (map :node (:edges connection-data)))
            (str "Edges should not be sorted the same way in asc and desc direction - "
                 sort-field))))))

(defn connection-test
  "Test a connection with more than one edge.
  It uses the $first and $after variables to paginate.
  The specified query/operation-name should provide them."
  [operation-name
   graphql-query
   variables
   connection-path
   expected-nodes]
  ;; page 1
  (let [{:keys [data errors status]}
        (query graphql-query
                  (into variables
                        {:first 1})
                  operation-name)]
    (is (= 200 status))
    (is (empty? errors) "No errors")
    (let [{nodes-p1 :nodes
           edges-p1 :edges
           page-info-p1 :pageInfo
           total-count-p1 :totalCount}
          (get-in data connection-path)]
      (is (= (count expected-nodes)
             total-count-p1))
      (is (= (count nodes-p1) 1)
          "The first page contains 1 node")
      (is (= (count edges-p1) 1)
          "The first page contains 1 edge")
      ;; page 2
      (let [{:keys [data errors status]}
            (query graphql-query
                   (into variables
                         {:first 50
                          :after (:endCursor page-info-p1)})
                   operation-name)]
        (is (= 200 status))
        (is (empty? errors) "No errors")
        (let [{nodes-p2 :nodes
               edges-p2 :edges
               page-info-p2 :pageInfo
               total-count-p2 :totalCount}
              (get-in data connection-path)]
          (is (= (count expected-nodes)
                 total-count-p2))
          (is (= (count nodes-p2) (- (count expected-nodes)
                                     (count nodes-p1)))
              "The second page contains all remaining nodes")
          (is (= (count edges-p2) (- (count expected-nodes)
                                     (count edges-p1)))
              "The second page contains all remaining edges")
          (is (= (set expected-nodes)
                 (set (concat nodes-p1 nodes-p2)))
              "All nodes have been retrieved")
          (is (= (set (nodes->edges expected-nodes))
                 (set (concat edges-p1 edges-p2)))
              "All edges have been retrieved"))))))
