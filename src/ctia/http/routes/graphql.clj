(ns ctia.http.routes.graphql
  (:require [clojure.tools.logging :as log]
            [compojure.api
             [core :as c]
             [sweet :refer :all]]
            [ctia.schemas
             [graphql2 :as gql2]]
            [ring-graphql-ui.core :refer [graphiql
                                          voyager]]
            [ring.util.http-response :refer :all]
            [schema.core :as s]))

(def graphql-ui-routes
  (c/undocumented
   ;; --- GraphiQL https://github.com/shahankit/custom-graphiql/
   (graphiql {:path "/graphiql"
              :endpoint "/ctia/graphql"})
   ;; --- GraphQL Voyager https://github.com/APIs-guru/graphql-voyager
   (voyager {:path "/voyager"
             :endpoint "/ctia/graphql"})))

(defroutes graphql-routes
  (POST "/graphql" []
        :tags ["GraphQL"]
        :return gql2/RelayGraphQLResponse
        :header-params [api_key :- (s/maybe s/Str)]
        :body [body gql2/RelayGraphQLQuery {:description "a Relay compatible GraphQL body"}]
        :summary "EXPERIMENTAL: Executes a Relay compatible GraphQL query"
        (let [request-context {}
              {:keys [query operationName variables]} body]
          (log/info "Graphql call body: " (pr-str body) " variables: " variables)
          (let [{:keys [errors data] :as result}
                (gql2/execute query operationName variables)
                str-errors (map str errors)]
            (log/info "Graphql result:" result)
            (cond
              (seq str-errors) (do (log/info "Graphql errors: " (pr-str str-errors))
                                   (bad-request {:data data
                                                 :errors str-errors}))
              (some? data) (ok {:data data})
              :else (internal-server-error))))))
