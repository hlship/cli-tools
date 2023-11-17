(ns net.lewisship.cli-tools.job-status
  (:require [net.lewisship.cli-tools.progress :as progress]
            [clj-commons.ansi :refer [compose]]
            [net.lewisship.cli-tools.terminal :refer [tput]]))

(def ^:private *jobs (atom nil))

(def ^:private *job-id (atom 0))

(def ^:private *config (atom nil))

(def ^:private *thread (atom nil))

(defn ^long now-ms
  []
  (System/currentTimeMillis))

(defmacro ^:private with-err
  [& body]
  #_`(do ~@body)
  `(binding [*out* *err*]
     (do ~@body)))

(def ^:private status->font
  {:normal  nil
   :success :green
   :failure :red
   :warning :yellow})

(defn default-layout
  "Default layout for a job's data. Formats the summary, then (if :progress-target
  is non-nil), uses the progress-formatter to format a progress bar."
  [active? complete? job-data]
  (let [{:keys [prefix progress-value progress-target progress-formatter summary status]} job-data
        inner-font (cond
                     active?
                     :bold

                     complete?
                     :faint)]
    (list
      [{:font (status->font status)}
       [{:font inner-font}
        prefix
        summary]]
      (when progress-target
        (list " "
              (progress-formatter progress-value progress-target))))))


(def default-config
  {:update-ms          100                                  ; update interval for the display
   :dim-ms             500                                  ; dims job after inactivity
   ;; Can be overridden by a job:
   :layout             default-layout                       ; formats the job, including progress
   :progress-formatter progress/block-progress-formatter})

(defn- output-job-line!
  [_config job-data]
  (if (::dirty? job-data)
    (let [{:keys  [layout]
           ::keys [line active? complete?]} job-data
          ;; There is definitely interference between rlwrap (used by clj) and some of these codes.
          ;; Works fine with clojure (not clj) and Babashka.
          line-content (compose
                         (tput "sc")                        ; save cursor position
                         (tput "cuu" line)                  ; cursor up
                         (tput "hpa" 0)                     ; leftmost column
                         (layout active? complete? job-data)
                         (tput "el")                        ; erase to end of line
                         (tput "rc"))]                      ; restore cursor position
      (print line-content)
      (dissoc job-data ::dirty?))
    ;; Not dirty, leave it unchanged.
    job-data))

(defn- mark-active-jobs
  "Marks recently updated jobs that are not identified as active, as active.
  Marks active jobs that have been inactive too long as inactive."
  [config jobs]
  (let [active-cutoff-ms (- (now-ms) (:dim-ms config))]
    (map (fn [{::keys [active? last-active-ms] :as job-data}]
           (cond
             (and (not active?)
                  (<= active-cutoff-ms last-active-ms))
             (assoc job-data ::active? true
                    ::dirty? true)

             (and active?
                  (< last-active-ms active-cutoff-ms))
             (assoc job-data ::active? false
                    ::dirty? true)

             :else
             job-data))
         jobs)))

(defn- move-and-purge-inactive-completed-jobs
  [jobs]
  (let [pred      (fn [{::keys [complete? active?]}]
                    (boolean
                      (and complete? (not active?))))
        {purgable  true
         remaining false} (->> jobs
                               (sort-by ::line)
                               (group-by pred))
        reordered (-> []
                      (into remaining)
                      (into (map #(assoc % ::purge? true ::dirty? true) purgable)))]
    (map (fn [{::keys [line] :as job-data} new-line]
           (cond-> job-data
                   (not= line new-line) (assoc
                                          ::line new-line
                                          ::dirty? true)))
         reordered
         (iterate inc 1))))

(defn- index-by
  [f coll]
  (reduce (fn [m v]
            (assoc m (f v) v))
          {}
          coll))

(defn- refresh-job-lines
  []
  (let [jobs     @*jobs
        config   @*config
        job-list (->> jobs
                      vals
                      (mark-active-jobs config))]
    (when (some ::dirty? job-list)
      (print (tput "civis"))                                ; make cursor invisible
      (let [jobs' (->> job-list
                       (map #(output-job-line! config %))
                       (remove ::purge?)
                       move-and-purge-inactive-completed-jobs
                       (index-by ::id))]
        (print (tput "cnorm"))                              ; normal cursor
        (flush)
        (when-not (compare-and-set! *jobs jobs jobs')
          (recur))))))

(defn- job-loop
  []
  ;; When the *config goes nil, shut down and clear out any other state.
  (while @*config
    (try
      (with-err
        (refresh-job-lines))
      (Thread/sleep (:update-ms @*config))
      ;; When interrupted, loop back to top.
      (catch InterruptedException _)))
  (println)
  (reset! *thread nil)
  (reset! *jobs nil))

(defn start-board
  "Starts the job board, which will dynamically update to show new and updated jobs."
  ([] (start-board nil))
  ([overrides]
   (when @*config
     (throw (IllegalStateException. "Job board is already started")))
   (reset! *config (merge default-config overrides))
   (let [thread (Thread. job-loop)]
     (reset! *thread thread)
     (doto thread
       (.setName "job-status-update")
       (.setDaemon true)
       (.start)))))

(defn- kick-thread

  []
  (when-let [thread @*thread]
    (.interrupt thread)))

(defn stop
  "Stops the job status update thread."
  []
  (reset! *config nil)
  (kick-thread))

(defn new-job
  ([]
   (new-job nil))
  ([job-data]
   (let [job-id   (swap! *job-id inc)
         job-data (-> (select-keys @*config [:layout :progress-formatter])
                      (merge job-data)
                      (assoc ::id job-id
                             ::line 1
                             ::dirty? true
                             ::last-active-ms (now-ms)))]
     ;; Make room for the new line at offset 0
     (with-err (println))
     ;; Inform existing lines they've been moved up one.
     ;; There's a tiny race condition here if there's a visual update
     ;; after the println and before the line numbers are updated.
     (swap! *jobs (fn [jobs]
                    (-> jobs
                        (update-vals #(-> %
                                          (update ::line inc)
                                          ;; dirty ... but not active.
                                          (assoc ::dirty? true)))
                        (assoc job-id job-data))))
     job-id)))

(defn- update-job
  [job-id f & args]
  (swap! *jobs update job-id
         (fn [job-data]
           (-> (apply f job-data args)
               (assoc ::dirty? true
                      ::last-active-ms (now-ms)))))
  job-id)

(defn- assoc-job
  [job-id k v]
  (update-job job-id assoc k v))

(defn prefix
  "Sets the prefix for the job; the prefix immeditately precedes the summary
  in the default layout, and usually ends with a space, or other punctuation, as
  a seperator.  May be a composed string."
  [job-id prefix]
  (assoc-job job-id :prefix prefix))

(defn summary
  "Sets the job summary, a short bit of text (or composed text) that identifies the
  current activity of the job.  The job's status and activity may affect the font
  used when displaying the job summary."
  [job-id summary]
  (assoc-job job-id :summary summary))

(defn start-progress
  "Adds progress output for the job; target is a number that is the value to be reached
  (for example, a number of files to read, or pages to output). This enables
  a progress bar (by default, following the summary), that is initally at 0%."
  [job-id target]
  (assoc-job job-id :progress-target target))

(defn complete-progress
  "Sets the progress value to the progress target, such that progress is displayed at 100%."
  [job-id]
  (update-job job-id
              (fn [{:keys [progress-target] :as job-data}]
                (assoc job-data :progress-value progress-target))))
(defn tick
  "Called after [[start-progress]] to advance the progress by 1 (or by a specified value)."
  ([job-id]
   (tick job-id 1))
  ([job-id increment]
   (update-job job-id
               (fn [{:keys [progress-value] :as job-data}]
                 (assoc job-data
                        :progress-value (if progress-value
                                          (+ progress-value increment)
                                          increment))))))

(defn set-progress
  "Sets the progress value to a specific value."
  [job-id progress-value]
  (assoc-job job-id :progress-value progress-value))

(defn status
  "Changes the job's status to one of :normal (default), :success, :failure, or :warning.
  This affects the font used when displaying the job's prefix and summary (in the default layout)."
  [job-id status]
  (assoc-job job-id :status status))

(defn complete
  "Marks the job as complete; completed jobs are moved to the top of the list of jobs."
  [job-id]
  (assoc-job job-id ::complete? true))




