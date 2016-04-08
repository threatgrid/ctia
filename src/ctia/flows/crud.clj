(ns ctia.flows.crud
  "This namespace handle all necessary flows for creating, updating and deleting objects.

  (Cf. #159)."
  (:import java.util.UUID)
  (:require [ctia.flows.hooks :refer :all]))

(defn make-id
  "Apprently `make-id` is the same for all stores."
  [type-name _]
  (str type-name "-" (UUID/randomUUID)))

(defn create-flow
  "This function centralize the create workflow.
  It is helpful to easily add new hooks name

  To be noted:
    - `:before-create` hooks can modify the object stored.
    - `:after-create` hooks can't modify the object returned but
      a hook modifying an object will provide the modified object
      to the next `:after-create` hook and so on."
  [realize-fn
   store-fn
   object-type
   login
   object]
  (let [id (make-id object-type object)
        realized (realize-fn object id login)
        pre-hooked (apply-hooks object-type realized :before-create)
        stored (store-fn pre-hooked)]
    (apply-hooks object-type stored :after-create)
    stored))

(defn update-flow
  "This function centralize the update workflow.
  It is helpful to easily add new hooks name

  To be noted:
    - `:before-update` hooks can modify the object stored.
    - `:after-update` hooks can't modify the object returned but
      a hook modifying an object will provide the modified object
      to the next `:after-update` hook and so on."
  [get-fn
   realize-fn
   update-fn
   object-type
   id
   login
   object]
  (let [old-object (get-fn id)
        realized (realize-fn object id login old-object)
        ;; Remark: it might be nice to provide the stored object and the new object
        ;; To the hooks, but it will make hook code more complicated
        ;; Typical usage: check the update won't modify some field
        pre-hooked (apply-hooks object-type realized :before-update)
        stored (update-fn pre-hooked)]
    (apply-hooks object-type stored :after-update)
    stored))

(defn delete-flow
  "This function centralize the deletion workflow.
  It is helpful to easily add new hooks name

  To be noted:
    - the flow get the object from the store to be used by hooks.
    - `:before-delete` hooks can modify the object stored.
    - `:after-delete` hooks can't modify the object returned
      but a hook modifying an object will provide the modified object
      to the next `:after-delete` hooks and so on."
  [get-fn
   delete-fn
   object-type
   id]
  (let [object (get-fn id)
        pre-hooked (apply-hooks object-type object :before-delete)
        existed? (delete-fn id)]
    (apply-hooks object-type pre-hooked :after-delete)
    existed?))
