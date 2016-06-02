(ns ctia.import.threatgrid.feed
  (:require [cheshire.core :as cjson]
            [clojure.java.io :as io]
            [clojure.data.json :as json]
            [clojure.string :as str]
            [clj-time.core :refer [date-time days now] :as time]
            [clj-time.format :refer [parse unparse formatter] :as tf]
            [ctia.lib.time :refer [date-range after?] :as ctime]
            [ctia.import.threatgrid.http :as http]
            [ctia.import.threatgrid.feed.indicators :as fi]
            [ctia.import.threatgrid.feed.sightings :as fs]
            [ctia.import.threatgrid.feed.judgements :as fj]
            [clojure.tools.cli :refer [parse-opts]]))

(defn tg-feed-names []
  [;;"autorun-registry" ;; always empty
   "banking-dns"
   "dll-hijacking-dns"
   "doc-net-com-dns"
   "downloaded-pe-dns"
   "dynamic-dns"
   "irc-dns"
   "modified-hosts-dns"
   "parked-dns"
   "public-ip-check-dns"
   "rat-dns"
   ;;"scheduled-tasks" ;; always empty
   "sinkholed-ip-dns"
   "stolen-cert-dns"])

(defn date->str [dt]
  (unparse (formatter "yyyy-MM-dd") dt))

(defn dates-in-range [sdate edate]
  (mapv date->str (date-range sdate edate (days 1))))

(defn post-judgements-to-ctia [feed ctia-uri]
  (let [judgements (fj/feed->judgements feed)]
    (println "Posting" (count judgements) "judgements to ctia")
    (http/post-to-ctia judgements :judgements ctia-uri)))

(defn post-sightings-to-ctia [feed ctia-uri]
  (let [sightings (fs/feed->sightings feed)]
    (println "Posting" (count sightings) "sightings to ctia")
    (http/post-to-ctia sightings :sightings ctia-uri)))

(defn file->feed [indicators feed-path feed-name file-name ctia-uri]
  (let [uri (http/tg-uri file-name)
        entries (cjson/parse-string (slurp (str feed-path "/" file-name)) true)
        description (-> entries first :description)
        indicator (fi/feed-indicator feed-name description ctia-uri indicators)
        feed {:title feed-name
              :description description
              :source file-name
              :source_uri uri
              :indicator indicator
              :observable {:type "domain" :field :domain}
              :entries entries}]
    feed))

(comment
  (let [indicators (atom {})
        feed-path "/var/tg-feeds"
        feed-name "rat-dns"
        file-name "rat-dns_2016-03-01.json"
        ctia-uri "http://localhost:3000"
        feed (file->feed indicators
                         feed-path
                         feed-name
                         file-name
                         ctia-uri)]
    (post-sightings-to-ctia feed ctia-uri)))

(defn load-feed-to-ctia [{:keys [source] :as feed} ctia-uri]
  (println "Start loading " source)
  (post-judgements-to-ctia feed ctia-uri)
  (post-sightings-to-ctia feed ctia-uri)
  (println "Done loading " source))

(defn process-feeds
  [{:keys [feed-path
           feed-names
           dates
           ctia-uri] :as options}]
  (let [indicators (atom {})]
    (doseq [date dates
            feed-name feed-names
            file-name (filter #(.exists (io/as-file (str feed-path "/" %)))
                              (http/feed-file-names [feed-name] [date]))]
      (let [feed (file->feed indicators feed-path feed-name file-name ctia-uri)]
        (load-feed-to-ctia feed ctia-uri)))))

(def cli-options
  [["-h" "--help" "Print help menu"
    :default false]
   [nil "--feed-name STRING" "Name of feed. "
    :default nil
    :validate [#(.contains (tg-feed-names) %)
               "Invalid feed name.  See --help for list of valid feed names."]]
   [nil "--ctia-uri STRING" (str "URI for the CTIA server. ")
    :default "http://localhost:3000"]
   ["-d" "--download" (str "Download feeds from Threat Grid. "
                           "Skips feeds that are empty or already downloaded. "
                           "Requires --tg-api-key or \"APIKEY\" env variable")
    :default false
    :required false]
   [nil "--tg-api-key STRING" (str "Threat Grid API key. "
                                   "Required when downloading feeds from "
                                   "Threat Grid, unless \"APIKEY\" environment "
                                   "variable is set.")
    :default nil]
   ["-s" "--start-date STRING" (str "Date string representing start of date range. "
                                    "Must be in \"yyyy-MM-dd\" format. ")
    :default (date->str (now))]
   ["-e" "--end-date STRING" (str "Date string representing end of date range. "
                                  "Must be in \"yyyy-MM-dd\" format. "
                                  "Defaults to one day after :start-date")
    :default nil]
   ["-a" "--all" (str "Perform action on all known feeds.")
    :default false
    :required false]
   [nil "--feed-path STRING" (str "Absolute path to local directory "
                                  "containing feed files. ")
    :default "/var/tg-feeds"
    :validate [#(.isDirectory (io/file %))
               "Directory does not exist."]]])

(defn usage [options-summary]
  (str "Import Threat Grid feeds into CTIA.

Usage: lein run -m ctia.import.threatgrid.feed [options]

Options:
"
       options-summary
"

Valid Feed Names:
  banking-dns
  dll-hijacking-dns
  doc-net-com-dns
  downloaded-pe-dns
  dynamic-dns
  irc-dns
  modified-hosts-dns
  parked-dns
  public-ip-check-dns
  rat-dns
  sinkholed-ip-dns
  stolen-cert-dns"))

(defn exit [status msg]
  (println msg)
  (System/exit status))

(defn error-msg [errors]
  (str "The following errors occurred while parsing your command:\n\n"
              (str/join \newline errors)))

(defn -main [& args]
  (let [{:keys [options arguments errors summary]} (parse-opts args cli-options)]
    ;; Handle help and error conditions
    (cond
      (:help options) (exit 0 (usage summary))
      errors (exit 1 (error-msg errors)))
    ;; Post-parse assertions
    (try (assert (or (:feed-name options)
                     (:all options))
                 (str "Either --feed-name OR --all option must be specified." options))
         (assert (or (:tg-api-key options)
                     (System/getenv "APIKEY")
                     (not (:download options)))
                 (str "If --download is true, either --tg-api-key "
                      "or \"APIKEY\" environment variable is required."))
         (catch java.lang.AssertionError e (exit 1 (error-msg [e]))))
    (let [date-formatter (formatter "yyyy-MM-dd")
          sdate (parse date-formatter (:start-date options))
          end-date (if (empty? (:end-date options))
                     (date->str (time/plus sdate (time/days 1)))
                     (:end-date options))
          edate (parse date-formatter end-date)
          feed-names (if (:all options)
                       (tg-feed-names)
                       [(:feed-name options)])
          tg-api-key (or (:tg-api-key options)
                         (System/getenv "APIKEY"))
          dates (dates-in-range sdate edate)
          options (assoc options
                         :end-date end-date
                         :dates dates
                         :feed-names feed-names
                         :tg-api-key tg-api-key)]
      (println "Executing with options:" options)
      (if (:download options)
        (http/download-feeds options))
      (process-feeds options))))
