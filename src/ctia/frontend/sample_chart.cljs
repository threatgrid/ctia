(ns ctia.frontend.sample-chart
  (:require
   ["react" :as React]
   ["recharts" :as charts]
   [ajax.core :as ajax]
   [cljs-bean.core :refer [->clj ->js]]
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
   (assoc db ::data result)))

(reg-sub ::data (fn [db _] (get db ::data)))

(reg-sub
 ::chart-data
 :<- [::data]
 (fn [data _]
   (->> data :aggregations :org :buckets
        (map :source)
        (map :buckets)
        (flatten)
        (reduce
         (fn [bkt nxt]
           (conj
            bkt
            (->> nxt :titles :buckets
                 (reduce
                  (fn [a n]
                    (assoc
                     a
                     (:key n)
                     (+ (get a (:key n))
                        (-> n :doc_count))))
                  {}))))
         [])
        (apply merge)
        (map #(hash-map :name (first %)
                        :nb-docs (second %))))))

(def BarChart (reagent/adapt-react-class charts/BarChart))
(def CartesianGrid (reagent/adapt-react-class charts/CartesianGrid))
(def XAxis (reagent/adapt-react-class charts/XAxis))
(def YAxis (reagent/adapt-react-class charts/YAxis))
(def Tooltip (reagent/adapt-react-class charts/Tooltip))
(def Legend (reagent/adapt-react-class charts/Legend))
(def Bar (reagent/adapt-react-class charts/Bar))
(def Cell (reagent/adapt-react-class charts/Cell))

(defn customized-axis-tick [props]
  (let [{:keys [x y payload]} (->clj props)]
    (reagent/as-element
     [:g {:transform (str "translate(" (- x 12) "," y ")")}
      [:text
       {:x 0
        :y 0
        :dy 16
        :textAnchor "end"
        :transform "rotate(-55)"
        :font-size "5pt"
        :font-family "Helvetica"
        :font-weight 200}
       (:value payload)]])))

(defn- random-color [x]
  (str "#"
       (-> 16777215
           (* (js/Math.random))
           (js/Math.floor)
           (.toString 16)
           (subs 0 4))))

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
     [CartesianGrid {:strokeDasharray "2 2"
                     :stroke "#dcdcdc"}]
     [XAxis {:dataKey  :name
             :interval 0
             :height   200
             :tick     customized-axis-tick}]
     [YAxis {:scale             :log
             :domain            [0.01 :auto]
             :allowDataOverflow true
             :tick              {:font-family "Helvetica"}}]
     [Tooltip {:formatter (fn [_ _ props]
                            (js/Array (aget props "payload" "nb-docs")
                                      "Number of docs"))}]
     [Legend]
     [Bar {:dataKey "nb-docs"
           :stackId "a"}
      (for [{:keys [nb-docs]} chart-data]
        [Cell {:fill (random-color nb-docs)}])]]))

(defn root []
  (dispatch [::fetch-data])
  (fn []
    [:div
     [:h1 {:style {:font-family "Helvetica"}} "CTIA Incidents"]
     [bar-chart]]))
