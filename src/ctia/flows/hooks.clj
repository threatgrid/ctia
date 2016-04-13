(ns ctia.flows.hooks
  "Handle hooks (Cf. #159).")

(def default-hooks
  {:before-create {:doc "`before-create` hooks are triggered on create routes before the object is saved in the DB."
                   :list []}
   :after-create {:doc "`after-create` hooks are called after an object was created."
                  :list []}
   :before-update {:doc "`before-update` hooks are triggered on update before the object is saved in the DB."
                   :list []}
   :after-update {:doc "`after-update` hooks are called after an object was updated."
                  :list []}
   :before-delete {:doc "`before-delete` hooks are called before an object deletion"
                   :list []}
   :after-delete {:doc "`after-delete` hooks are called after an object is deleted"
                  :list []}})

(def hooks (atom default-hooks))

(defn reset-hooks! []
  (reset! hooks default-hooks))

(defprotocol Hook
  "A hook is mainly a function"
  (init [this])
  (handle [this
           type-name
           stored-object
           prev-object])
  (destroy [this]))

(defn find-hooks
  "Should return a list of `Hook` in the correct order"
  [hook-type]
  :todo)

(defn add-hook!
  "Add a `Hook` for the hook `hook-type`"
  [hook-type hook]
  (swap! hooks update-in [hook-type :list] conj hook))

(defn add-hooks!
  "Add a list of `Hook` for the hook `hook-type`"
  [hook-type hook-list]
  (swap! hooks update-in [hook-type :list] concat hook-list))

(defn init!
  "Initialize hooks"
  []
  (doseq [hook-type (keys @hooks)]
    (let [hook-list (find-hooks (:list hook-type))]
      (add-hooks! hook-type hook-list))))

(defn init-hooks!
  "Should search Jar, Namespaces and init Objects"
  []
  (doseq [hook-type (keys @hooks)]
    (doseq [hook (get-in @hooks [hook-type :list])]
      (init hook)
      (add-hook! hook-type hook)))
  @hooks)

(defn destroy-hooks
  "Should call all destructor for each hook in reverse order."
  []
  (doseq [hook-type (keys @hooks)]
    (doseq [hook (reverse (get-in @hooks [hook-type :list]))]
      (destroy hook))))

(defn bind-realized
  "bind high level type would be:

    RealizedObject
    -> (Type -> RealizedObject -> RealizedObject -> Maybe RealizedObject)
    -> RealizedObject
    -> RealizedObject

  If the hook returns nil, it doens't modify the realized object returned."
  [realized-object hook prev-object type-name]
  (let [result (handle hook type-name realized-object prev-object)]
    (if (nil? result)
      realized-object
      result)))

(defn apply-hook-list
  "Apply all hooks to some realized object of some type"
  [type-name realized-object prev-object hook-list]
  (reduce (fn [acc hook]
            (bind-realized acc hook prev-object type-name))
          realized-object
          hook-list))

(defn apply-hooks
  "Apply all hooks for some hook-type"
  [type-name realized-object prev-object hook-type]
  (apply-hook-list type-name
                   realized-object
                   prev-object
                   (get-in @hooks [hook-type :list])))

