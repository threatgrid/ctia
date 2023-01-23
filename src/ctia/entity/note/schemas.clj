(ns ctia.entity.note.schemas
  (:require [ctia.domain.entities :refer [default-realize-fn]]
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

(s/defschema PartialStoredNote
  (st/optional-keys-schema StoredNote))

(def realize-note
  (default-realize-fn "note" NewNote StoredNote))

(def note-fields
  (concat sorting/base-entity-sort-fields
          sorting/sourcable-entity-sort-fields
          [:entity_id
           :content]))

(s/defschema NoteFieldsParam
  {(s/optional-key :fields) [(apply s/enum note-fields)]})

(def note-histogram-fields [:timestamp])

(def note-enumerable-fields [:content :entity_id])
