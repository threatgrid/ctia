(ns ctia.task.migration.migrations.describe)

(defn describe
  [{:keys [name title description short_description]
    :as entity}]
  (let [new-title (or name title "No title provided")
        new-description (or description name title "No description provided")
        new-short-description (or short_description name title "No description provided")]
    (-> entity
        (dissoc :name)
        (assoc :title new-title
               :description new-description
               :short_description new-short-description))))

(def migrate-describe
  (map describe))
