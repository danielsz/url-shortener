(ns url-shortener.admin.v2.group-detail
  (:require
    [clojure.core.async :as async]
    [clojure.tools.logging :as log]
    [starfederation.datastar.clojure.api :as d*]
    [starfederation.datastar.clojure.adapter.http-kit :refer [->sse-response on-open on-close]]
    [url-shortener.shared.analytics :as analytics]
    [taoensso.carmine :as redis]
    [cheshire.core :as json]
    [url-shortener.shared.pages.v2 :as pages]
    [ring.util.response :refer [redirect]]
    [url-shortener.schema :refer [group-links-key]]))


(defn- group-detail-page [group-id]
  (pages/group-dashboard-page group-id
    (str "@get('/admin/v2/group/" group-id "/stream')")
    ["← Admin Dashboard" "/admin"]))

(defn handle-group-detail [{{group-id :group-id} :path-params}]
  (let [links (redis/wcar nil (redis/smembers (group-links-key group-id)))]
    (if (= 1 (count links))
      (redirect (str "/admin/v2/link/" (first links)))
      {:status  200
       :headers {"Content-Type" "text/html"
                 "Cache-Control" "no-store"}
       :body    (group-detail-page group-id)})))


(defn handle-group-stream [pubsub {{group-id :group-id} :path-params :as request}]
  (let [ch-atom  (atom nil)
        feed-atom (atom [])
        cleanup! (fn []
                   (when-let [ch @ch-atom]
                     (async/unsub (:publication pubsub) :analytics-update ch)
                     (async/close! ch)
                     (reset! ch-atom nil)))]
    (->sse-response request
      {on-open
       (fn [sse]
         (let [ch (async/chan (async/sliding-buffer 10))]
           (reset! ch-atom ch)
           (async/sub (:publication pubsub) :analytics-update ch)
           (try
             (d*/patch-signals! sse (json/generate-string (analytics/group-signals group-id)))
             (loop []
               (when-let [event (async/<!! ch)]
                 (when (= (:group-id event) group-id)
                   (try
                     (let [path (:path event)
                           url  (redis/wcar nil (redis/hget path "url"))
                           item {:short (str (System/getProperty "shortener.service") path)
                                 :url   url
                                 :time  (-> (java.time.LocalTime/now) (.truncatedTo java.time.temporal.ChronoUnit/SECONDS) str)}]
                       (swap! feed-atom #(vec (take 5 (cons item %)))))
                     (d*/patch-signals! sse (json/generate-string (assoc (analytics/group-signals group-id) :feed @feed-atom)))
                     (catch Exception e (log/error "push failed" (.getMessage e)))))
                 (recur)))
             (catch Exception e (log/error "stream failed for" group-id (.getMessage e))))))
       on-close
       (fn [sse status]
         (log/debug "group stream v2 closed" group-id status)
         (cleanup!))})))
