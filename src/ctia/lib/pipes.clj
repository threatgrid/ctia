(ns ctia.lib.pipes)

(defprotocol AsyncPipe
  (start-workers [this num])
  (shutdown! [this])
  (add-job [this job-msg]))

(defprotocol WorkHandler
  (make-worker [this])
  (do-some-work [this work]))

(defn do-start-workers [pipe num-workers]
  (dotimes [_ num-workers]
    (make-worker pipe)))
