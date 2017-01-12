(ns ctia.http.routes.graphql
  (:require [clojure.tools.logging :as log])
  (:require 
   [compojure.api.sweet :refer :all]
   [compojure.api.sweet :refer :all]
   [schema.core :as s]
   [ring.util.request :refer [body-string]]
   [ring.util.http-response :refer :all]
   [ctia.schemas.graphql :as gql])
  (:import graphql.GraphQL))



(defroutes graphql-routes
  (POST "/graphql" []
        :tags ["GraphQL" "Relay"]
        :return gql/RelayGraphQLResponse
        :header-params [api_key :- (s/maybe s/Str)]
        :body [body gql/RelayGraphQLQuery {:description "a Relay compatible GraphQL body"}]
        :summary "EXPERIMENTAL: Executes a Relay compatible GraphQL query"
        (let [request-context {}
              query (get body :query)
              varmap (java.util.HashMap.
                      (into {} 
                            (for [[k v] (get body :variables {})] 
                              [(name k) v])))]
          (log/info "Graphql call body: " (pr-str body) " variables: " varmap)
          (let [result (.execute (new GraphQL gql/schema)
                                 query
                                 nil
                                 varmap)
                data (.getData result)
                errors (map str (.getErrors result))]
            (if data
              (if (not (empty? errors))
                (do (log/info "Graphql errors: " (pr-str errors))
                    (internal-server-error))
                (ok {:data data}))
              (do (log/info "Graphql errors: " (pr-str errors))
                  (bad-request {:errors errors})))))))
