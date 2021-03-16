(ns ctia.schemas.sorting)

;; These are the fields that are sortable, per entity.  We place them
;; here since they are used by more than one entity's routes.  For
;; instance, the indicator route needs to know how to sort sightings
;; for the `ctia/indicator/:ID/sighting` handler
;;
(def base-entity-sort-fields [:id :schema_version :revision
                              :timestamp :language :tlp])

(def sourcable-entity-sort-fields [:source :source_uri])

(def describable-entity-sort-fields [:title])

(def default-entity-sort-fields
  (concat base-entity-sort-fields
          sourcable-entity-sort-fields
          describable-entity-sort-fields))
