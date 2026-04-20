(ns url-shortener.report
  (:require
   [url-shortener.shared.utils :refer [resolve-report infer-report-type]]
   [clojure.tools.logging :as log]
   [ring.util.response :refer [bad-request not-found]]
   [starfederation.datastar.clojure.api :as d*]
   [starfederation.datastar.clojure.adapter.http-kit :refer [->sse-response on-open on-close]]
   [clojure.core.async :as async]
   [url-shortener.reports.link :as link]
   [cheshire.core :as json]
   [url-shortener.shared.analytics :as analytics]
   [taoensso.carmine :as redis]
   [url-shortener.reports.group :as group]))

(defn handle-create-report [{{:keys [path owner-id group-id]} :params}]
  (cond
    path     (link/create-link-report! owner-id path)
    group-id (group/create-group-report! owner-id (str owner-id ":" group-id))
    :else    (bad-request "path or group-id required")))

(defn handle-report-page [{{token :token} :path-params :as request}]
  (if-let [report (resolve-report token)]
    (let [subject (get report "subject")]
      (case (infer-report-type subject)
        "link"  (link/handle-link-report  token report)
        "group" (group/handle-group-report token report)
        (not-found "Unknown report type")))
    (not-found "Report not found or expired")))


(defn handle-report-stream [pubsub {{token :token} :path-params :as request}]
  (if-let [report (resolve-report token)]
    (let [subject   (get report "subject")
          type      (infer-report-type subject)
          ch-atom   (atom nil)
          feed-atom (atom [])
          cleanup!  (fn []
                      (when-let [ch @ch-atom]
                        (log/debug "report stream closed" token)
                        (async/unsub (:publication pubsub) :analytics-update ch)
                        (async/close! ch)
                        (reset! ch-atom nil)))
          build!    (fn []
                      (case type
                        "link"  (assoc (analytics/link-signals subject)  :feed @feed-atom)
                        "group" (assoc (analytics/group-signals subject) :feed @feed-atom)))
          relevant? (fn [event]
                      (case type
                        "link"  (= (:path event) subject)
                        "group" (= (:group-id event) subject)))]
      (->sse-response request
        {on-open
         (fn [sse]
           (let [ch (async/chan (async/sliding-buffer 10))]
             (reset! ch-atom ch)
             (async/sub (:publication pubsub) :analytics-update ch)
             (try
               (d*/patch-signals! sse (json/generate-string (build!)))
               (loop []
                 (when-let [event (async/<!! ch)]
                   (when (relevant? event)
                     (try
                       (let [path (:path event)
                             url  (redis/wcar nil (redis/hget path "url"))
                             item {:short (str (System/getProperty "shortener.service") path)
                                   :url   url
                                   :time  (-> (java.time.LocalTime/now)
                                              (.truncatedTo java.time.temporal.ChronoUnit/SECONDS)
                                              str)}]
                         (swap! feed-atom #(vec (take 5 (cons item %)))))
                       (d*/patch-signals! sse (json/generate-string (build!)))
                       (catch Exception e (log/error "push failed" (.getMessage e)))))
                   (recur)))
               (catch Exception e (log/error "stream failed" (.getMessage e))))))
         on-close
         (fn [sse status]
           (log/debug "report stream on-close" token status)
           (cleanup!))}))
    (not-found "Report not found or expired")))

