(ns ctia.http.routes.graphql
  (:require [clojure.tools.logging :as log])
  (:require 
   [compojure.api.sweet :refer :all]
   [compojure.api.sweet :refer :all]
   [schema.core :as s]
   [ring.util.request :refer [body-string]]
   [ring.util.http-response :refer :all]
   [ctia.schemas.graphql :as gql]
   [ctia.schemas.graphql2 :as gql2]))

(defroutes graphql-routes
  (POST "/graphql" []
        :tags ["GraphQL" "Relay"]
        :return gql/RelayGraphQLResponse
        :header-params [api_key :- (s/maybe s/Str)]
        :body [body gql/RelayGraphQLQuery {:description "a Relay compatible GraphQL body"}]
        :summary "EXPERIMENTAL: Executes a Relay compatible GraphQL query"
        (let [request-context {}
              {:keys [query variables]} body]
          (log/info "Graphql call body: " (pr-str body) " variables: " variables)
          (let [{:keys [errors data] :as result}
                (gql2/execute query variables)
                str-errors (map str errors)]
            (log/info "Graphql result:" result)
            (if data
              (if (seq str-errors)
                (do (log/info "Graphql errors: " (pr-str str-errors))
                    (internal-server-error))
                (ok {:data data}))
              (do (log/info "Graphql errors: " (pr-str str-errors))
                  (bad-request {:errors str-errors})))))))
