(ns ctia.entity.note
  (:require [ctia.domain.entities :refer [page-with-long-id]]
            [ctia.entity.note.schemas :as note-schemas]
            [ctia.http.routes.common :as routes.common]
            [ctia.http.routes.crud :refer [services->entity-crud-routes]]
            [ctia.lib.compojure.api.core :refer [GET routes]]
            [ctia.schemas.core :refer [APIHandlerServices]]
            [ctia.store :refer [list-records]]
            [ctia.stores.es.mapping :as em]
            [ctia.stores.es.store :refer [def-es-store]]
            [schema-tools.core :as st]
            [schema.core :as s]))

(def note-related-entity
  {:type "object"
   :properties
   {:entity_type em/token
    :entity_id em/token}})

(def note-mapping
  {"note"
   {:dynamic false
    :properties
    (merge em/base-entity-mapping
           em/sourcable-entity-mapping
           em/stored-entity-mapping
           {:related_entities note-related-entity
            :content em/text
            :note_class em/token
            :author em/token})}})

(def-es-store NoteStore
  :note
  note-schemas/StoredNote
  note-schemas/PartialStoredNote)

(def capabilities
  #{:create-note
    :read-note
    :delete-note
    :search-note
    :list-notes})

(s/defn note-routes [services :- APIHandlerServices]
  (routes
   (services->entity-crud-routes
    services
    {:entity                   :note
     :new-schema               note-schemas/NewNote
     :entity-schema            note-schemas/Note
     :get-schema               note-schemas/PartialNote
     :get-params               note-schemas/NoteGetParams
     :list-schema              note-schemas/PartialNoteList
     :search-schema            note-schemas/PartialNoteList
     :patch-schema             note-schemas/PartialNewNote
     :external-id-q-params     note-schemas/NoteByExternalIdQueryParams
     :search-q-params          note-schemas/NoteSearchParams
     :new-spec                 :new-note/map
     :can-patch?               true
     :can-aggregate?           true
     :realize-fn               note-schemas/realize-note
     :get-capabilities         :read-note
     :post-capabilities        :create-note
     :put-capabilities         :create-note
     :patch-capabilities       :create-note
     :delete-capabilities      :delete-note
     :search-capabilities      :search-note
     :external-id-capabilities :read-note
     :histogram-fields         note-schemas/note-histogram-fields
     :enumerable-fields        note-schemas/note-enumerable-fields
     :spec                     :new-note/map})))

(def note-entity
  {:route-context         "/note"
   :tags                  ["Note"]
   :entity                :note
   :plural                :notes
   :new-spec              :new-note/map
   :schema                note-schemas/Note
   :partial-schema        note-schemas/PartialNote
   :partial-list-schema   note-schemas/PartialNoteList
   :new-schema            note-schemas/NewNote
   :stored-schema         note-schemas/StoredNote
   :partial-stored-schema note-schemas/PartialStoredNote
   :realize-fn            note-schemas/realize-note
   :es-store              ->NoteStore
   :es-mapping            note-mapping
   :services->routes      (routes.common/reloadable-function note-routes)
   :can-patch?            true
   :capabilities          capabilities
   :patch-capabilities    :create-note
   :fields                note-schemas/note-fields
   :sort-fields           note-schemas/note-fields
   :searchable-fields     note-schemas/searchable-fields})
