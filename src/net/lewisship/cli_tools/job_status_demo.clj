(ns net.lewisship.cli-tools.job-status-demo
  {:command-ns 'net.lewisship.cli-tools.builtins}
  (:require [net.lewisship.cli-tools :refer [defcommand]]
            [net.lewisship.cli-tools.job-status :as j]))

(defn sleep
  ([ms] (Thread/sleep ms))
  ([job-id ms]
   (Thread/sleep ms)
   job-id))

(defn thread*
  [f]
  (let [*complete (promise)
        thread    (Thread. (fn []
                             (deliver *complete (f))))]
    (doto thread
      (.setDaemon true)
      .start)

    *complete))

(defmacro thread
  [& body]
  `(thread* (fn [] (do ~@body))))

(defn done
  [job-id]
  (-> job-id
      j/complete))

(defn simple-job
  [summary delay]
  (thread (-> (j/new-job)
              (j/summary (str summary " ..."))
              (sleep delay)
              (j/status :success)
              (j/summary (str summary " \u2713 "))
              done)))

(defn progress-job
  [summary target delay]
  (let [job (-> (j/new-job)
                (j/summary [{:width 30} summary])
                (j/start-progress target))]
    (dotimes [_ target]
      (sleep delay)
      (j/tick job))

    (-> job
        (j/complete-progress)
        (j/status :success)
        done)))

(defcommand job-status-demo
  "Demo the use of the job status board."
  []
  (j/start-board)
  (let [futures [(thread (simple-job "Atomic turbines to speed" 2000))
                 (thread (sleep 500)
                         (progress-job "Loading Bat-fuel" 15 250))
                 (thread (sleep 1000)
                         (progress-job "Rotating Batmobile platform" 180 10))
                 (thread (simple-job "Initializing on-board Bat-computer" 1000))]]
    (run! deref futures))
  (-> (j/new-job)
      (j/status :warning)
      (j/summary "Please fasten your Bat-seatbelts")
      done)

  (Thread/sleep 3000))
