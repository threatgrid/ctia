(ns ctia.import.threatgrid
  (:gen-class)
  (:import [javax.xml.bind DatatypeConverter])
  (:require [cheshire.core :as json]
            [clojure.tools.cli :refer [parse-opts]]
            [clj-time.core :as t]
            [clj-time.format :as f]
            [clj-http.client :as http]
            [clojure.edn :as edn]))

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
  (load-judgements-from-feed-file "test/data/rat-dns.json" "http://localhost:3000/" "domain" :domain))

(def commands                           ; FIXME replace with function
                                        ; to support redefinition.
  {"import-feed" import-feed-comment})

(defn global-arguments []
  [["-?" "--help" "Print Usage"
    :default false]
   ["-d" "--debug" "Turn on debug logging"
    :default false]
   ["-v" "--verbose" "Turn on info logging"
    :default false]
   [nil "--ctiaURL" "CTIA URL"
    :default "http://localhost:3000"]])

(defn -main [& args]
  (let [{:keys [options arguments errors summary]}
        (parse-opts args (global-arguments) :in-order true)
        [command & command-arguments]
        arguments
        usage (fn [text]
                (print (str text "\n\nAvailable Commands:\n  "
                            (join ", " (keys commands))
                            "\n\nGlobal Options:\n" summary))
                -1)]
    (let [command (or command "import-feed")]
      (cond errors                        ; unknown arguments
            (usage (join "\n" errors))
            (:help options)               ; help
            (usage "Usage")
            (not command)                 ; missing command
            (usage "No command provided.")
            (not (get commands command))  ; unknown command
            (usage (str "Unknown command: '" command "'"))
            true                          ; continue processing
            ((get commands command) global-options command-arguments)))))

