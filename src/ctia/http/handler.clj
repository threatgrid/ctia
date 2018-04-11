(ns ctia.http.handler
  (:require [clj-momo.ring.middleware.metrics :as metrics]
            [ctia.http.middleware.auth :refer :all]
            [compojure.api
             [core :refer [middleware]]
             [routes :as routes]
             [sweet :refer  [api context undocumented]]]
            [compojure.route :as rt]
            [ctia.actor.routes :refer [actor-routes]]
            [ctia.attack-pattern.routes :refer [attack-pattern-routes]]
            [ctia.bulk.routes :refer [bulk-routes]]
            [ctia.bundle.routes :refer [bundle-routes]]
            [ctia.campaign.routes :refer [campaign-routes]]
            [ctia.casebook.routes
             :refer
             [casebook-operation-routes casebook-routes]]
            [ctia.coa.routes :refer [coa-routes]]
            [ctia.data-table.routes :refer [data-table-routes]]
            [ctia.documentation.routes :refer [documentation-routes]]
            [ctia.exploit-target.routes :refer [exploit-target-routes]]
            [ctia.feedback.routes :refer [feedback-by-entity-route feedback-routes]]
            [ctia.graphql.routes :refer [graphql-ui-routes
                                         graphql-routes]]
            [ctia.http.exceptions :as ex]
            [ctia.http.middleware
             [cache-control :refer [wrap-cache-control]]
             [unknown :as unk]
             [version :refer [wrap-version]]]
            [ctia.incident.routes :refer [incident-routes]]
            [ctia.indicator.routes :refer [indicator-routes]]
            [ctia.investigation.routes :refer [investigation-routes]]
            [ctia.judgement.routes :refer [judgement-routes]]
            [ctia.malware.routes :refer [malware-routes]]
            [ctia.metrics.routes :refer [metrics-routes]]
            [ctia.observable.routes :refer [observable-routes]]
            [ctia.properties :refer [properties]]
            [ctia.properties.routes :refer [properties-routes]]
            [ctia.relationship.routes :refer [relationship-routes]]
            [ctia.sighting.routes :refer [sighting-routes]]
            [ctia.tool.routes :refer [tool-routes]]
            [ctia.version.routes :refer [version-routes]]
            [ring.middleware.not-modified :refer [wrap-not-modified]]
            [ring.util.http-response :refer [ok]]))

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
  the default way it it represented when observed in the wild.

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

(defn api-handler []
  (api {:exceptions
        {:handlers
         {:compojure.api.exception/request-parsing ex/request-parsing-handler
          :compojure.api.exception/request-validation ex/request-validation-handler
          :compojure.api.exception/response-validation ex/response-validation-handler
          :clj-momo.lib.es.conn/es-query-parsing-error ex/es-query-parsing-error-handler
          :access-control-error ex/access-control-error-handler
          :compojure.api.exception/default ex/default-error-handler}}
        :swagger {:ui "/"
                  :spec "/swagger.json"
                  :options {:ui {:jwtLocalStorageKey
                                 (get-in @properties
                                         [:ctia :http :jwt :local-storage-key])}}
                  :data {:info {:title "CTIA"
                                :license {:name "All Rights Reserved",
                                          :url ""}
                                :contact {:name "Cisco Security Business Group -- Advanced Threat "
                                          :url "http://github.com/threatgrid/ctia"
                                          :email "cisco-intel-api-support@cisco.com"}
                                :description api-description}

                         :tags [{:name "Actor" :description "Actor operations"}
                                {:name "Attack Pattern" :description "Attack Pattern operations"}
                                {:name "Bundle" :description "Bundle import (Beta)"}
                                {:name "Campaign" :description "Campaign operations"}
                                {:name "COA" :description "COA operations"}
                                {:name "DataTable" :description "DataTable operations"}
                                {:name "Events" :description "Events operations"}
                                {:name "ExploitTarget" :description "ExploitTarget operations"}
                                {:name "Feedback" :description "Feedback operations"}
                                {:name "GraphQL" :description "GraphQL operations"}
                                {:name "Incident" :description "Incident operations"}
                                {:name "Indicator", :description "Indicator operations"}
                                {:name "Judgement", :description "Judgement operations"}
                                {:name "Malware", :description "Malware operations"}
                                {:name "Relationship", :description "Relationship operations"}
                                {:name "Properties", :description "Properties operations"}
                                {:name "Casebook", :description "Casebook operations"}
                                {:name "Sighting", :description "Sighting operations"}
                                {:name "Bulk", :description "Bulk operations"}
                                {:name "Metrics", :description "Performance Statistics"}
                                {:name "Tool", :description "Tool operations"}
                                {:name "Verdict", :description "Verdict operations"}
                                {:name "Version", :description "Version Information"}]}}}

       (middleware [wrap-not-modified
                    wrap-cache-control
                    wrap-version
                    ;; always last
                    (metrics/wrap-metrics "ctia" routes/get-routes)]
                   documentation-routes
                   (graphql-ui-routes)
                   (context "/ctia" []
                            (context "/actor" []
                                     :tags ["Actor"]
                                     actor-routes)
                            (context "/attack-pattern" []
                                     :tags ["Attack Pattern"]
                                     attack-pattern-routes)
                            (context "/bulk" []
                                     :tags ["Bulk"]
                                     bulk-routes)
                            bundle-routes
                            (context "/campaign" []
                                     :tags ["Campaign"]
                                     campaign-routes)
                            (context "/casebook" []
                                     :tags ["Casebook"]
                                     casebook-routes
                                     casebook-operation-routes)
                            (context "/coa" []
                                     :tags ["COA"]
                                     coa-routes)
                            (context "/data-table" []
                                     :tags ["DataTable"]
                                     data-table-routes)
                            (context "/exploit-target" []
                                     :tags ["ExploitTarget"]
                                     exploit-target-routes)
                            (context "/feedback" []
                                     :tags ["Feedback"]
                                     feedback-routes
                                     feedback-by-entity-route)
                            (context "/incident" []
                                     :tags ["Incident"]
                                     incident-routes)
                            (context "/indicator" []
                                     :tags ["Indicator"]
                                     indicator-routes)
                            (context "/investigation" []
                                     :tags ["Investigation"]
                                     investigation-routes)
                            (context "/judgement" []
                                     :tags ["Judgement"]
                                     judgement-routes)
                            (context "/malware" []
                                     :tags ["Malware"]
                                     malware-routes)
                            (context "/relationship" []
                                     :tags ["Relationship"]
                                     relationship-routes)
                            (context "/sighting" []
                                     :tags ["Sighting"]
                                     sighting-routes)
                            (context "/tool" []
                                     :tags ["Tool"]
                                     tool-routes)
                            observable-routes
                            metrics-routes
                            properties-routes
                            graphql-routes
                            version-routes))
       (undocumented
        (rt/not-found (ok (unk/err-html))))))
