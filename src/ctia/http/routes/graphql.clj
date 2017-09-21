(ns ctia.http.routes.graphql
  (:require [clojure.tools.logging :as log]
            [compojure.api
             [core :as c]
             [sweet :refer :all]]
            [ctia.properties :refer [properties]]
            [ctia.schemas
             [graphql :as gql]]
            [ring-graphql-ui.core :refer [graphiql
                                          voyager]]
            [ring.util.http-response :refer :all]
            [schema.core :as s]))

(defn graphql-ui-routes []
  (let [jwt-storage-key
        (get-in @properties [:ctia :http :jwt :local-storage-key])]
    (c/undocumented
     ;; --- GraphiQL https://github.com/shahankit/custom-graphiql/
     (graphiql {:path "/graphiql"
                :endpoint "/ctia/graphql"
                :jwtLocalStorageKey jwt-storage-key})
     ;; --- GraphQL Voyager https://github.com/APIs-guru/graphql-voyager
     (voyager {:path "/voyager"
               :endpoint "/ctia/graphql"
               :jwtLocalStorageKey jwt-storage-key}))))

(defroutes graphql-routes
  (POST "/graphql" []
        :tags ["GraphQL"]
        :return gql/RelayGraphQLResponse
        :header-params [{Authorization :- (s/maybe s/Str) nil}]
        :body [body gql/RelayGraphQLQuery {:description "a Relay compatible GraphQL body"}]
        :summary "EXPERIMENTAL: Executes a Relay compatible GraphQL query"
        (let [request-context {}
              {:keys [query operationName variables]} body]
          (log/debug "Graphql call body: " (pr-str body) " variables: " (pr-str variables))
          (let [{:keys [errors data] :as result}
                (gql/execute query operationName variables)
                str-errors (map str errors)]
            (log/debug "Graphql result:" result)
            (cond
              (seq str-errors) (do (log/error "Graphql errors: " (pr-str str-errors))
                                   (bad-request (cond-> {:errors str-errors}
                                                  (some? data) (assoc :data data))))
              (some? data) (ok {:data data})
              :else (internal-server-error
                     {:error "No data or errors were returned by the GraphQL query"}))))))
