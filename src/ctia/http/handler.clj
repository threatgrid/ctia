(ns ctia.http.handler
  (:require [clj-momo.ring.middleware.metrics :as metrics]
            [compojure.api
             [core :refer [middleware]]
             [routes :as routes]
             [sweet :refer [context defapi]]]
            [ctia.http.exceptions :as ex]
            [ctia.http.middleware
             [auth :as auth]
             [cache-control :refer [wrap-cache-control]]]
            [ctia.http.routes
             [actor :refer [actor-routes]]
             [bulk :refer [bulk-routes]]
             [campaign :refer [campaign-routes]]
             [coa :refer [coa-routes]]
             [data-table :refer [data-table-routes]]
             [documentation :refer [documentation-routes]]
             [exploit-target :refer [exploit-target-routes]]
             [feedback :refer [feedback-routes]]
             [incident :refer [incident-routes]]
             [indicator :refer [indicator-routes]]
             [judgement :refer [judgement-routes]]
             [metrics :refer [metrics-routes]]
             [observable :refer [observable-routes]]
             [properties :refer [properties-routes]]
             [relationship :refer [relationship-routes]]
             [sighting :refer [sighting-routes]]
             [ttp :refer [ttp-routes]]
             [graphql :refer [graphql-routes]]
             [version :refer [version-routes]]]
            [ring.middleware.not-modified :refer [wrap-not-modified]]))

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

(defapi api-handler
  {:exceptions
   {:handlers
    {:compojure.api.exception/request-parsing ex/request-parsing-handler
     :compojure.api.exception/request-validation ex/request-validation-handler
     :compojure.api.exception/response-validation ex/response-validation-handler
     :clj-momo.lib.es.conn/es-query-parsing-error ex/es-query-parsing-error-handler
     :compojure.api.exception/default ex/default-error-handler}}

   :swagger {:ui "/"
             :spec "/swagger.json"
             :data {:info {:title "CTIA"
                           :license {:name "All Rights Reserved",
                                     :url ""}
                           :contact {:name "Cisco Security Business Group -- Advanced Threat "
                                     :url "http://github.com/threatgrid/ctia"
                                     :email "cisco-intel-api-support@cisco.com"}
                           :description api-description}

                    :tags [{:name "Actor" :description "Actor operations"}
                           {:name "Campaign" :description "Campaign operations"}
                           {:name "COA" :description "COA operations"}
                           {:name "DataTable" :description "DataTable operations"}
                           {:name "Events" :description "Events operations"}
                           {:name "ExploitTarget" :description "ExploitTarget operations"}
                           {:name "Feedback" :description "Feedback operations"}
                           {:name "Incident" :description "Incident operations"}
                           {:name "Indicator", :description "Indicator operations"}
                           {:name "Judgement", :description "Judgement operations"}
                           {:name "Relationship", :description "Relationship operations"}
                           {:name "Properties", :description "Properties operations"}
                           {:name "Sighting", :description "Sighting operations"}
                           {:name "TTP", :description "TTP operations"}
                           {:name "Bulk", :description "Bulk operations"}
                           {:name "Metrics", :description "Performance Statistics"}
                           {:name "Version", :description "Version Information"}]}}}

  (middleware [auth/wrap-authentication
               wrap-not-modified
               wrap-cache-control
               ;; always last
               (metrics/wrap-metrics "ctia" routes/get-routes)]

              documentation-routes
              (context "/ctia" []
                       actor-routes
                       bulk-routes
                       campaign-routes
                       coa-routes
                       data-table-routes
                       exploit-target-routes
                       feedback-routes
                       incident-routes
                       indicator-routes
                       judgement-routes
                       metrics-routes
                       observable-routes
                       properties-routes
                       sighting-routes
                       ttp-routes
                       relationship-routes
                       graphql-routes
                       version-routes)))
