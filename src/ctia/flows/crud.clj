(ns ctia.flows.crud
  "This namespace handle all necessary flows for creating, updating and deleting objects.

  (Cf. #159)."
  (:import java.util.UUID)
  (:require [ctia.flows.hooks :refer :all]
            [ctia.events.obj-to-event :refer [to-create-event
                                              to-update-event
                                              to-delete-event]]))

(defn make-id
  "Apprently `make-id` is the same for all stores."
  [type-name _]
  (str (name type-name) "-" (UUID/randomUUID)))

(defn create-flow
  "This function centralize the create workflow.
  It is helpful to easily add new hooks name

  To be noted:
    - `:before-create` hooks can modify the object stored.
    - `:after-create` hooks are read only"
  [& {:keys [realize-fn store-fn object-type login object]}]
  (let [id (make-id object-type object)
        realized (realize-fn object id login)
        pre-hooked (apply-hooks :type-name       object-type
                                :realized-object realized
                                :hook-type       :before-create)
        event (try (to-create-event pre-hooked)
                   (catch Exception e
                     (do (clojure.pprint/pprint object)
                         (prn e))))
        _ (apply-hooks :type-name       object-type
                       :realized-object event
                       :hook-type       :before-create-ro
                       :read-only?      true)
        stored (store-fn pre-hooked)]
    (apply-hooks :type-name       object-type
                 :realized-object stored
                 :hook-type       :after-create
                 :read-only?      true)
    stored))

(defn update-flow
  "This function centralize the update workflow.
  It is helpful to easily add new hooks name

  To be noted:
    - `:before-update` hooks can modify the object stored.
    - `:after-update` hooks are read only"
  [& {:keys [get-fn
             realize-fn
             update-fn
             object-type
             id
             login
             object]}]
  (let [old-object (get-fn id)
        realized (realize-fn object id login old-object)
        pre-hooked (apply-hooks :type-name       object-type
                                :realized-object realized
                                :prev-object     old-object
                                :hook-type       :before-update)
        event (try (to-update-event pre-hooked old-object)
                   (catch Exception e
                     (do (clojure.pprint/pprint object)
                         (prn e))))
        _ (apply-hooks :type-name       object-type
                       :realized-object event
                       :prev-object     old-object
                       :hook-type       :before-update-ro
                       :read-only?      true)
        stored (update-fn pre-hooked)]
    (apply-hooks :type-name       object-type
                 :realized-object stored
                 :prev-object     old-object
                 :hook-type       :after-update
                 :read-only?      true)
    stored))

(defn delete-flow
  "This function centralize the deletion workflow.
  It is helpful to easily add new hooks name

  To be noted:
    - the flow get the object from the store to be used by hooks.
    - `:before-delete` hooks can modify the object stored.
    - `:after-delete` hooks are read only"
  [& {:keys [get-fn
             delete-fn
             object-type
             id]}]
  (let [object (get-fn id)
        pre-hooked (apply-hooks :type-name       object-type
                                :realized-object object
                                :prev-object     object
                                :hook-type       :before-delete
                                :read-only?      false)
        event (try (to-delete-event pre-hooked)
                   (catch Exception e
                     (do (clojure.pprint/pprint object)
                         (prn e))))
        _ (apply-hooks :type-name       object-type
                       :realized-object event
                       :hook-type       :before-delete-ro
                       :read-only?      true)
        existed? (delete-fn id)]
    (apply-hooks :type-name       object-type
                 :realized-object pre-hooked
                 :prev-object     object
                 :hook-type       :after-delete
                 :read-only?      true)
    existed?))
