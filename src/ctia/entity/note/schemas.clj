(ns ctia.entity.note.schemas
  (:require [ctia.domain.entities :refer [default-realize-fn]]
            [ctia.http.routes.common :as routes.common]
            [ctia.schemas.core :refer [def-acl-schema def-stored-schema]]
            [ctia.schemas.sorting :as sorting]
            [ctim.schemas.note :as note-schemas]
            [flanders.utils :as fu]
            [schema-tools.core :as st]
            [schema.core :as s]))

(def-acl-schema Note
  note-schemas/Note
  "note")

(def-acl-schema PartialNote
  (fu/optionalize-all note-schemas/Note)
  "partial-note")

(s/defschema PartialNoteList
  [PartialNote])

(def-acl-schema NewNote
  note-schemas/NewNote
  "new-note")

(s/defschema PartialNewNote
  (st/optional-keys-schema NewNote))

(def-stored-schema StoredNote Note)

(def realize-note
  (default-realize-fn "note" NewNote StoredNote))

(s/defschema PartialStoredNote
  (st/optional-keys-schema StoredNote))

(def note-fields
  (concat sorting/base-entity-sort-fields
          sorting/sourcable-entity-sort-fields
          [:entity_id
           :author
           :content]))

(def note-sort-fields
  (apply s/enum note-fields))

(def searchable-fields
  #{:id
    :source
    :entity_id
    :content})

(def note-histogram-fields [:timestamp])
(def note-enumerable-fields [:entity_id])

(s/defschema NoteFieldsParam
  {(s/optional-key :fields) [(apply s/enum note-fields)]})

(s/defschema NoteFieldsParam
  {(s/optional-key :fields) [note-sort-fields]})

(s/defschema NoteSearchParams
  (st/merge
   routes.common/PagingParams
   routes.common/BaseEntityFilterParams
   routes.common/SourcableEntityFilterParams
   routes.common/SearchableEntityParams
   (st/optional-keys
    {:entity_id s/Str
     :content s/Str})))

(s/defschema NoteQueryParams
  (st/merge
   NoteFieldsParam
   routes.common/PagingParams
   {:entity_id s/Str
    (s/optional-key :sort_by) note-sort-fields}))

(def NoteGetParams NoteFieldsParam)

(s/defschema NoteByExternalIdQueryParams
  (st/dissoc NoteQueryParams :entity_id))
