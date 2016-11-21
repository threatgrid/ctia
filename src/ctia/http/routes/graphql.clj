(ns ctia.http.routes.graphql
  (:require 
   [compojure.api.sweet :refer :all]
   [compojure.api.sweet :refer :all]
   [schema.core :as s]
   [ring.util.http-response :refer :all]
   [ctia.schemas.graphql :as gql]))




(defroutes graphql-routes
  (POST "/graphql" []
        :tags ["GraphQL" "Relay"]
        :return gql/RelayGraphQLResponse
        :body [body gql/RelayGraphQLQuery {:description "a Relay compatible GraphQL body"}]
        :header-params [api_key :- (s/maybe s/Str)]
        :summary "EXPERIMENTAL: Executes a Relay compatible GraphQL query"
        (let [[data errors] (.execute gql/schema (:query body) nil (:variables body))]
          {:data data :errors errors})))
