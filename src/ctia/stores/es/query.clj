(ns ctia.stores.es.query
  (:require [ctia.domain.access-control
             :refer [public-tlps
                     max-record-visibility-everyone?]]
            [clojure.string :as str]))

(defn find-restriction-query-part
  [{:keys [login groups]}]
  ;; TODO do we really want to discard case on that?
  (let [login (str/lower-case login)
        groups (map str/lower-case groups)]
    {:bool
     {:should
      (cond->>
          [;; Document Owner
           {:bool {:filter [{:term {"owner" login}}
                            {:terms {"groups" groups}}]}}

           ;; or if user is listed in authorized_users or authorized_groups field
           {:term {"authorized_users" login}}
           {:terms {"authorized_groups" groups}}

           ;; CTIM records with TLP equal or below amber that are owned by org BAR
           {:bool {:must [{:terms {"tlp" (conj public-tlps "amber")}}
                          {:terms {"groups" groups}}]}}

           ;; CTIM records with TLP red that is owned by user FOO
           {:bool {:must [{:term {"tlp" "red"}}
                          {:term {"owner" login}}
                          {:terms {"groups" groups}}]}}]

        ;; Any Green/White TLP if max-visibility is set to `everyone`
        (max-record-visibility-everyone?)
        (cons {:terms {"tlp" public-tlps}}))}}))


(defn- unexpired-time-range
  "ES filter that matches objects which
  valid time range is not expired"
  [time-str]
  [{:range
    {"valid_time.start_time" {"lte" time-str}}}
   {:range
    {"valid_time.end_time" {"gt" time-str}}}])

(defn active-judgements-by-observable-query
  "a filtered query to get judgements for the specified
  observable, where valid time is in now range"
  [{:keys [value type]} time-str]

  (concat
   (unexpired-time-range time-str)
   [{:term {"observable.type" type}}
    {:term {"observable.value" value}}]))

