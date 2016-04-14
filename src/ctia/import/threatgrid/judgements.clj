(ns ctia.import.threatgrid.judgements
  (:gen-class)
  (:import [javax.xml.bind DatatypeConverter])
  (:require [cheshire.core :as json]
            [clj-time.core :as t]
            [clj-time.format :as f]
            [clj-http.client :as http]))

(defn feed-judgements
  "Extract Judgement objects from a list of TG feed entries"
  [feed-entries observable-type observable-field & {:keys [confidence source
                                                           priority
                                                           severity
                                                           days-valid
                                                           disposition]
                                                    :or {confidence "High"
                                                         priority 90
                                                         severity 100
                                                         source "threatgrid-feed"
                                                         days-valid 30
                                                         disposition 2}}]
  (map
   (fn [entry]
     (let [formatter (f/formatters :date-time-no-ms)
           start (clj-time.coerce/from-date
                  (.getTime (DatatypeConverter/parseDateTime (:timestamp entry))))]
       {:observable {:type  observable-type
                     :value (get entry observable-field)}
        :disposition disposition
        :valid_time {:start_time (f/unparse formatter start)
                     :end_time (f/unparse formatter (t/plus start
                                                            (t/days days-valid)))}
        :confidence confidence
        :severity severity
        :source source
        :priority priority
        :source_uri (:info entry)
        :reason (:description entry)
        :reason_uri (:sample entry)
        }))
   feed-entries))

(defn load-judgements-from-feed-file [file ctia-url observable-type observable-field & {:as options}]
  (let [entries (json/parse-string (slurp file) true)
        judgements (apply feed-judgements entries
                          observable-type observable-field options)
        target-url (str ctia-url "/ctia/judgement")]
    (map (fn [judgement]
           (let [options {:content-type :edn
                          :accept :edn
                          :throw-exceptions false
                          :socket-timeout 2000
                          :conn-timeout 2000
                          :body (pr-str judgement)}
                 response (http/post target-url options)]
             response))
         judgements)))

(comment
  (load-judgements-from-feed-file "test/data/rat-dns.json" "http://localhost:4000/" "domain" :domain))
