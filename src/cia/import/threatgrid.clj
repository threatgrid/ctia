(ns cia.import.threatgrid
  (:gen-class)
  (:import [javax.xml.bind DatatypeConverter])
  (:require [cheshire.core :as json]
            [cia.schemas.indicator :as si]
            [cia.schemas.external.ioc-indicators :as sei]
            [clojure.tools.cli :refer [parse-opts]]
            [clojure.string :refer [join]]
            [clj-time.core :as t]
            [clj-time.coerce :as tc]
            [clj-time.format :as f]
            [clj-http.client :as http]
            [schema.core :as s]))

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
                                                         disposition 2
                                                         }}]
  (let [formatter (f/formatters :date-time-no-ms)]
    (for [entry feed-entries
          :let [start (tc/from-date
                       (.getTime (DatatypeConverter/parseDateTime (:timestamp entry))))]]
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
       :reason_uri (:sample entry)})))

(defn load-judgements-from-feed-file
  [file cia-url observable-type observable-field & {:as options}]
  (let [entries (json/parse-string (slurp file))
        judgements (apply feed-judgements entries
                          observable-type observable-field options)
        target-url (str cia-url "/cia/judgement")]
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

(defn ioc-indicators->cia-indicators
  [ioc-indicators & {:keys [validate?]}]
  (let [validate-ioc (if validate?
                       (partial s/validate sei/IoCIndicator)
                       identity)
        validate-ind (if validate?
                       (partial s/validate si/Indicator)
                       identity)]
    (for [ioc-indicator ioc-indicators]
      (let [{:strs [title description confidence severity variables author]}
            (validate-ioc ioc-indicator)

            indicator
            {:title title
             :description description
             :confidence confidence
             :severity severity
             :owner author
             :producer {:type "ThreatBrain"
                        :query ""
                        :variables variables}}]
        (validate-ind indicator)))))

(defn load-indicators-from-ioc-file
  [file cia-url]
  (let [target-url (str cia-url "/cia/indicator")]
    (doseq [indicator (-> (slurp file)
                          json/parse-string
                          ioc-indicators->cia-indicators)]
      (http/post target-url
                 {:content-type :edn
                  :accept :edn
                  :throw-exceptions false
                  :socket-timeout 2000
                  :conn-timeout 2000
                  :body (pr-str indicator)}))))

(comment
  (load-judgements-from-feed-file "test/data/rat-dns.json"
                                  "http://localhost:3000/"
                                  "domain"
                                  :domain))

(def commands
  ;; FIXME replace with function
  ;; to support redefinition.
  {"import-feed" (constantly -1)})

(defn global-arguments []
  [["-?" "--help" "Print Usage"
    :default false]
   ["-d" "--debug" "Turn on debug logging"
    :default false]
   ["-v" "--verbose" "Turn on info logging"
    :default false]
   [nil "--cia URL" "CIA URL"
    :default "http://localhost:3000/"]])

(defn usage
  [summary text]
  (print (str text
              "\n\nAvailable Commands:\n  "
              (join ", " (keys commands))
              "\n\nGlobal Options:\n" summary))
  -1)

(defn -main [& args]
  (let [{:keys [options arguments errors summary]}
        (parse-opts args (global-arguments) :in-order true)

        [command & command-arguments]
        arguments

        usage
        (partial usage summary)]
    (let [command (or command "import-feed")]
      (cond
        errors ;; unknown arguments
        (usage (join "\n" errors))

        (:help options) ;; help
        (usage "Usage")

        (not command) ;; missing command
        (usage "No command provided.")

        (not (get commands command)) ;; unknown command
        (usage (str "Unknown command: '" command "'"))

        :else ;; continue processing
        ((get commands command) options command-arguments)))))
