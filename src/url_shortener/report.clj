(ns url-shortener.report
  (:require
   [url-shortener.shared.utils :refer [resolve-report]]
   [clojure.tools.logging :as log]
   [ring.util.response :refer [bad-request not-found]]
   [starfederation.datastar.clojure.api :as d*]
   [starfederation.datastar.clojure.adapter.http-kit :refer [->sse-response on-open on-close]]
   [clojure.core.async :as async]
   [url-shortener.reports.link :as link]
   [url-shortener.reports.group :as group]))

(defn handle-create-report [{{:keys [path owner-id group-id]} :params}]
  (cond
    path     (link/create-link-report! owner-id path)
    group-id (group/create-group-report! owner-id (str owner-id ":" group-id))
    :else    (bad-request "path or group-id required")))

(defn handle-report-page [{{token :token} :path-params :as request}]
  (if-let [report (resolve-report token)]
    (case (get report "type")
      "link"  (link/handle-link-report token report)
      "group" (group/handle-group-report token report)
      (not-found "Unknown report type"))
    (not-found "Report not found or expired")))


(defn handle-report-stream [pubsub {{token :token} :path-params :as request}]
  (if-let [report (resolve-report token)]
    (let [type      (get report "type")
          target-id (get report "target-id")
          ch-atom   (atom nil)
          cleanup!  (fn []
                      (when-let [ch @ch-atom]
                        (log/debug "report stream closed" token)
                        (async/unsub (:publication pubsub) :click ch)
                        (async/close! ch)
                        (reset! ch-atom nil)))]
      (->sse-response request
        {on-open
         (fn [sse]
           (let [ch       (async/chan (async/sliding-buffer 10))
                  _        (reset! ch-atom ch)
                 push-all! (fn []
                             (case type
                               "link"
                               (do
                                 (d*/patch-elements! sse (link/render-stats target-id))
                                 (d*/patch-elements! sse (link/render-chart target-id))
                                 (d*/patch-elements! sse (link/render-countries target-id))
                                 (d*/patch-elements! sse (link/render-referrers target-id)))
                               "group"
                               (do
                                 (d*/patch-elements! sse (group/render-stats target-id))
                                 (d*/patch-elements! sse (group/render-chart target-id))
                                 (d*/patch-elements! sse (group/render-countries target-id))
                                 (d*/patch-elements! sse (group/render-links target-id)))))
                 relevant? (fn [event]
                             (case type
                               "link"  (= (:path event) target-id)
                               "group" (= (:group-id event) target-id)))]
             (async/sub (:publication pubsub) :click ch)
             (try
               (d*/patch-signals! sse "{\"connected\": true}")
               (push-all!)
               (loop []
                 (when-let [event (async/<!! ch)]
                   (when (relevant? event)
                     (try
                       (push-all!)
                       (catch Exception e (log/error "push failed" (.getMessage e)))))
                   (recur)))
               (catch Exception e (log/error "stream failed" (.getMessage e))))))
         on-close
         (fn [sse status]
           (log/debug "report stream on-close" token status)
           (cleanup!))}))
    (not-found "Report not found or expired")))





