(ns ctia.frontend.app
  (:require
   [reagent.dom :as re-dom]
   [ctia.frontend.sample-chart :as sample-chart]
   [re-frame.core :refer [dispatch
                          reg-event-db]]))

(reg-event-db
 ::init
 (fn [db _]
   (assoc db :defaults {})))

(defn root-view []
  [:div
   [sample-chart/root]])

(defn ^:dev/after-load render! []
  (re-dom/render
   [root-view]
   (js/document.getElementById "app")))

(defn init []
  (dispatch [::init])
  (render!))
