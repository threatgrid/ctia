(ns ctia.http.routes.graphql
  (:require [clojure.tools.logging :as log]
            [compojure.api
             [core :as c]
             [sweet :refer :all]]
            [ctia.schemas
             [graphql :as gql]
             [graphql2 :as gql2]]
            [ring-graphql-ui.core :refer [graphiql
                                          voyager]]
            [ring.util.http-response :refer :all]
            [schema.core :as s]))

(def graphql-ui-routes
  (c/undocumented
   (graphiql {:path "/graphiql"
              :endpoint "/ctia/graphql"})
   (voyager {:path "/voyager"
             :endpoint "/ctia/graphql"})))

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
