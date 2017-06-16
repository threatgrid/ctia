(ns ctia.test-helpers.graphql
  (:require [ctia.test-helpers.core :as helpers]))

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
