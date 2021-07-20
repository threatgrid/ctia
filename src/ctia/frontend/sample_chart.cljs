(ns ctia.frontend.sample-chart
  (:require
   ["recharts" :as charts]
   [ajax.core :as ajax]
   [day8.re-frame.http-fx]
   [re-frame.core :refer [dispatch
                          reg-event-fx
                          reg-event-db
                          reg-sub
                          subscribe]]
   [reagent.core :as reagent]))

(reg-event-fx
 ::fetch-data
 (fn [{:keys [db]} _]
   {:http-xhrio {:method :get
                 :uri "incident-top5-orgs-per-source-title-sample.json"
                 :format (ajax/json-request-format)
                 :response-format (ajax/json-response-format {:keywords? true})
                 :on-success [::process-data]}
    :db db}))

(reg-event-db
 ::process-data
 (fn [db [_ result]]
   (js/console.log "-----------> result" result)
   (assoc db ::data result)))

(reg-sub
 ::data
 (fn [db _]
   (get db ::data)))

(reg-sub
 ::chart-data
 (fn [db _]
   [{:name "Alex" :num 1000 :num2 5000}
    {:name "Betty" :num 3000 :num2 5500}
    {:name "Charlie" :num 1300 :num2 6000}
    {:name "Dean" :num 1000 :num2 500}]))

(def BarChart (reagent/adapt-react-class charts/BarChart))
(def CartesianGrid (reagent/adapt-react-class charts/CartesianGrid))
(def XAxis (reagent/adapt-react-class charts/XAxis))
(def YAxis (reagent/adapt-react-class charts/YAxis))
(def Tooltip (reagent/adapt-react-class charts/Tooltip))
(def Legend (reagent/adapt-react-class charts/Legend))
(def Bar (reagent/adapt-react-class charts/Bar))

(defn bar-chart []
  (let [chart-data @(subscribe [::chart-data])]
   [BarChart
    {:width  800
     :height 500
     :margin {:top    20
              :right  20
              :left   20
              :bottom 20}
     :data   chart-data}
    [CartesianGrid {:strokeDasharray "3 3"}]
    [XAxis {:dataKey "name"}]
    [YAxis]
    [Tooltip]
    [Legend]
    [Bar {:dataKey "num" :stackId "a" :fill"#8884d8"}]
    [Bar {:dataKey "num2" :stackId "b" :fill "#82ca9c"}]]))

(defn root []
  (dispatch [::fetch-data])
  (fn []
    [:div
     [:h1 "CTIA Incidents"]
     [bar-chart]
     ]))

(js/console.log "charts" charts)
