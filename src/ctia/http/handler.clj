(ns ctia.http.handler
  (:require [clj-momo.ring.middleware.metrics :as metrics]
            [clojure.string :as string]
            [ctia.entity.entities :as entities]
            [ctia.entity.feed :refer [feed-view-routes]]
            [ctia.entity.relationship :refer [incident-link-route]]
            [ctia.schemas.core :refer [APIHandlerServices Entity]]
            [compojure.api
             [core :refer [middleware]]
             [routes :as api-routes]
             [sweet :refer  [routes api context undocumented]]]
            [compojure.route :as rt]
            [ctia.bundle.routes :refer [bundle-routes]]
            [ctia.bulk.routes :refer [bulk-routes]]
            [ctia.documentation.routes :refer [documentation-routes]]
            [ctia.graphql.routes :refer [graphql-ui-routes
                                         graphql-routes]]
            [ctia.http.exceptions :as ex]
            [ctia.http.middleware
             [ratelimit :refer [wrap-rate-limit]]
             [auth :refer :all]
             [cache-control :refer [wrap-cache-control]]
             [unknown :as unk]
             [version :refer [wrap-version]]]
            [ctia.metrics.routes :refer [metrics-routes]]
            [ctia.observable.routes :refer [observable-routes]]
            [ctia.properties :as p
             :refer [get-http-swagger]]
            [ctia.properties.routes :refer [properties-routes]]
            [ctia.schemas.core :refer [APIHandlerServices]]
            [ctia.version :refer [current-version]]
            [ctia.version.routes :refer [version-routes]]
            [ctia.status.routes :refer [status-routes]]
            [ring.middleware.not-modified :refer [wrap-not-modified]]
            [ring.util.http-response :refer [ok]]
            [schema.core :as s]))

(def api-description
  "A Threat Intelligence API service

  This API provides a mechanism for making Judgements on the Disposition
  of Observables, which are then distilled into a final Verdict.  A
  Disposition is a statement regarding the malicious, or otherwise,
  nature of an Observable.

  The Judgements can be grouped into Indicators, which can be associated
  with Campaigns, Actors and TTPs.  Feedback can be given on specific
  Judgements, indicating agreement or disagreement, or clarification.

  When an Observable with a malicious Verdict is seen, it can be recorded as
  a Sighting, and the Relations that Observable had with other Observables can
  be recorded as well.

  We support a pre-defined set of Observable Types.  Each Observable Type has a
  specific form of natural identifier, its ID, which is almost always
  the default way it is represented when observed in the wild.

  * Ipv4/IPv6  -- 192.168.1.1
  * Domain/Hostname -- foo.com, www.bar.com
  * SHA256  -- the sha256 checksum of a file, or other data blob
  * MD5 -- the md5 checksum of a file, or other data blob
  * SHA1 -- the sha1 checksum of a file, or other data blob
  * URL -- A minimal form of the URL

  The Verdict is derived from all of the Judgements on that Observable which
  have not yet expired.  The highest priority Judgement becomes the
  active verdict.  If there is more than one Judgement with that
  priority, than Clean disposition has priority over all others, then
  Malicious disposition, and so on down to Unknown.

  <a href='/doc/README.md'>CTIA Documentation</a>")

(s/defn ^:private entity->routes
  "Implementation detail of entity-routes."
  [entity :- Entity
   services-map :- APIHandlerServices]
  {:pre [entity
         (map? services-map)]
   :post [%]}
  (let [{:keys [services->routes]} entity]
    (assert services->routes (str "Missing :services->routes for " (:entity entity)))
    (services->routes services-map)))

(defn add-dynamic-tags [route tags]
  {:pre [(instance? compojure.api.routes.Route route)]
   :post [(instance? compojure.api.routes.Route %)]}
  (update-in route [:info :tags] (comp set into) tags))

(s/defn entity-routes
  [entity :- Entity
   services :- APIHandlerServices]
  (routes
    (when-not (:no-api? entity)
      (let [{:keys [route-context tags]} entity]
        (context
          route-context []
          (-> (entity->routes entity services)
              (add-dynamic-tags tags)))))))

(s/defn entities-routes
  [services :- APIHandlerServices
   entities :- [Entity]]
  (apply routes
         (map #(entity-routes % services)
              entities)))

(def exception-handlers
  {:compojure.api.exception/request-parsing ex/request-parsing-handler
   :compojure.api.exception/request-validation ex/request-validation-handler
   :compojure.api.exception/response-validation ex/response-validation-handler
   :ductile.conn/es-query-parsing-error ex/es-query-parsing-error-handler
   :access-control-error ex/access-control-error-handler
   :invalid-tlp-error ex/invalid-tlp-error-handler
   :realize-entity-error ex/realize-entity-error-handler
   :spec-validation-error ex/spec-validation-error-handler
   :compojure.api.exception/default ex/default-error-handler})

(s/defn api-tags
  [{{:keys [enabled?]} :FeaturesService} :- APIHandlerServices]
  (->>
   [[:actor               "Actor"               "Actor operations"]
    [:asset               "Asset"               "Asset operations"]
    [:asset-mapping       "Asset Mapping"       "Asset Mapping operations"]
    [:asset-properties    "Asset Properties"    "Asset Properties operations"]
    [:attack-pattern      "Attack Pattern"      "Attack Pattern operations"]
    [:bulk                "Bulk"                "Bulk operations"]
    [:bundle              "Bundle"              "Bundle operations"]
    [:campaign            "Campaign"            "Campaign operations"]
    [:casebook            "Casebook"            "Casebook operations"]
    [:coa                 "COA"                 "COA operations"]
    [:datatable           "DataTable"           "DataTable operations"]
    [:event               "Event"               "Events operations"]
    [:feed                "Feed"                "Feed operations"]
    [:feedback            "Feedback"            "Feedback operations"]
    [:graphql             "GraphQL"             "GraphQL operations"]
    [:identity-assertion  "Identity Assertion"  "Identity Assertion operations"]
    [:incident            "Incident"            "Incident operations"]
    [:indicator           "Indicator"           "Indicator operations"]
    [:judgement           "Judgement"           "Judgement operations"]
    [:malware             "Malware"             "Malware operations"]
    [:metrics             "Metrics"             "Performance Statistics"]
    [:properties          "Properties"          "Properties operations"]
    [:relationship        "Relationship"        "Relationship operations"]
    [:sighting            "Sighting"            "Sighting operations"]
    [:status              "Status"              "Status Information"]
    [:target-record       "Target Record"       "Target Record operations"]
    [:tool                "Tool"                "Tool operations"]
    [:verdict             "Verdict"             "Verdict operations"]
    [:version             "Version"             "Version Information"]]
   (keep (fn [[k n desc]]
          (when (enabled? k)
            {:name n :description desc})))))

(defn apply-oauth2-swagger-conf
  [swagger-base-conf
   {:keys [client-id
           app-name
           authorization-url
           token-url
           flow
           realm
           scopes
           entry-key]}]
  (let [scope-map
        (when scopes
          (into {}
                (-> scopes
                    (string/split #",")
                    (->> (map #(string/split % #"\|"))))))
        scopes
        (keys scope-map)]

    (-> swagger-base-conf
        (assoc-in [:options :ui :oauth2]
                  {:clientId client-id
                   :appName app-name
                   :realm realm})
        (update-in [:data :security] concat
                   [{entry-key scopes}])
        (update-in [:data :securityDefinitions] assoc entry-key
                   {:type "oauth2"
                    :scopes scope-map
                    :authorizationUrl authorization-url
                    :tokenUrl token-url
                    :flow flow}))))

(defn- mark-disabled-entities
  [{{:keys [enabled?]} :FeaturesService}
   {:keys [entity] :as entity-map}]
  (cond-> entity-map
    (not (enabled? entity)) (assoc :no-api? true)))

(s/defn api-handler [{{:keys [get-in-config]} :ConfigService
                      :as services} :- APIHandlerServices]
  (let [{:keys [oauth2]}
        (get-http-swagger get-in-config)]
    (api {:exceptions {:handlers exception-handlers}
          :swagger
          (cond-> {:ui "/"
                   :spec "/swagger.json"
                   :options {:ui {:jwtLocalStorageKey
                                  (get-in-config
                                    [:ctia :http :jwt :local-storage-key])}}
                   :data {:info {:title "CTIA"
                                 :version (string/replace (current-version) #"\n" "")
                                 :license {:name "All Rights Reserved",
                                           :url ""}
                                 :contact {:name "Cisco Security Business Group -- Advanced Threat "
                                           :url "http://github.com/threatgrid/ctia"
                                           :email "cisco-intel-api-support@cisco.com"}
                                 :description api-description}
                          :security [{"JWT" []}]
                          :securityDefinitions
                          {"JWT" {:type "apiKey"
                                  :in "header"
                                  :name "Authorization"
                                  :description "Ex: Bearer \\<token\\>"}}
                          :tags (api-tags services)}}
            (:enabled oauth2)
            (apply-oauth2-swagger-conf
             oauth2))}

         (middleware [#(wrap-rate-limit % get-in-config)
                      wrap-not-modified
                      wrap-cache-control
                      #(wrap-version % get-in-config)
                      ;; always last
                      (metrics/wrap-metrics "ctia" api-routes/get-routes)]
           documentation-routes
           (graphql-ui-routes services)
           (context
               "/ctia" []
             (context "/feed" []
               :tags ["Feed"]
               (feed-view-routes services))
             ;; The order is important here for version-routes
             ;; must be before the middleware fn
             (version-routes services)
             (middleware [wrap-authenticated]
               (->>
                (entities/all-entities)
                vals
                (map (partial mark-disabled-entities services))
                (entities-routes services))
               status-routes
               (context
                   "/bulk" []
                 :tags ["Bulk"]
                 (bulk-routes services))
               (context
                   "/incident" []
                 :tags ["Incident"]
                 (incident-link-route services))
               (bundle-routes services)
               (observable-routes services)
               metrics-routes
               (properties-routes services)
               (graphql-routes services))))
         (undocumented
          (rt/not-found (ok (unk/err-html)))))))
