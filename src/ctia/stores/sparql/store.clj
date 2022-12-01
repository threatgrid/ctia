(ns ctia.stores.sparql.store
  (:require [clj-http.client :as client]
            [ctia.store :refer [IStore]]))

;;docker run --platform linux/amd64  --name my-virtuoso \
;;    -p 8890:8890 -p 1111:1111 \
;;    -e DBA_PASSWORD=myDbaPassword \
;;    -e SPARQL_UPDATE=true \
;;    -e DEFAULT_GRAPH=http://www.example.com/my-graph \
;;    -v ~/workspace/virtuoso:/data \
;;    -d tenforce/virtuoso

(defrecord SPARQLStore
    [endpoint]
    IStore
    (create-record [this new-records ident params])
    (read-record [this id ident params])
    (read-records [this ids ident params])
    (update-record [this id record ident params])
    (delete-record [this id ident params])
    (bulk-delete [this ids ident params])
    (bulk-update [this records ident params])
    (list-records [this filtermap ident params]
      (let [query "select * where {?x ?p ?y} LIMIT 1"]
         (client/get endpoint
                     {:as :json
                      :accept "application/sparql-results+json"
                      :query-params {:query query}})))
    (close [this]))


