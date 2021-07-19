(ns ctia.frontend.sample-chart
  (:require
   [ajax.core :as ajax]
   [day8.re-frame.http-fx]
   [re-frame.core :refer [dispatch
                          reg-event-fx
                          reg-event-db
                          reg-sub
                          subscribe]]))

(reg-event-fx
 ::fetch-data
 (fn [{:keys [db]} _]
   {:http-xhrio {:method          :get
                 :uri             "incident-top5-orgs-per-source-title-sample.json"
                 :format          (ajax/json-request-format)
                 :response-format (ajax/json-response-format {:keywords? true})
                 :on-success      [::process-data]}
    :db         db
    ;; :db         (assoc db :foo {:some-data "foo"})
    }))

(reg-event-db
 ::process-data
 (fn [db [_ result]]
   (js/console.log "-----------> result" result)
   (assoc db ::data result)))

(reg-sub
 ::data
 (fn [db _]
   (get db ::data)))

(defn root []
  (dispatch [::fetch-data])
  (fn []
    (let [data @(subscribe [::data])]
      [:div
       [:h1 "Upon this barren land I will place my shitty dragons"]
       [:pre (str data)]
       ])))
