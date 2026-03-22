(ns url-shortener.analytics
  (:require [url-shortener.shared.utils :refer [ip-validator epoch-now today this-week this-month]]
            [url-shortener.schema :refer [ips-key referrers-key daily-key group-key TTL-ANALYTICS group-ips-key group-daily-key countries-key group-countries-key weekly-key monthly-key group-monthly-key group-weekly-key]]
            [clojure.tools.logging :as log]
            [taoensso.carmine :as redis]
            [com.stuartsierra.component :as component]
            [clojure.core.async :as a :refer [chan dropping-buffer <!! sub unsub close! thread]]))

(defn write-country [geoip path group-id remote-addr]
  (when-let [m (.get @(:client geoip) (java.net.InetAddress/getByName remote-addr) java.util.Map)]
        (log/debug (.get m "country_code"))
        (let [country (.get m "country_code")]
          (when country
            (redis/wcar nil
              (redis/hincrby (countries-key path) country 1)
              (redis/expire  (countries-key path) TTL-ANALYTICS)
              (redis/hincrby (group-countries-key group-id) country 1)
              (redis/expire  (group-countries-key group-id) TTL-ANALYTICS))))))

(defn write-analytics! [geoip path group-id remote-addr referer]
  (log/debug path remote-addr referer)
  (try
    (when (.isValid ip-validator remote-addr)
      (redis/wcar nil
        (redis/zadd    (ips-key path) (epoch-now) remote-addr)
        (redis/expire  (ips-key path) TTL-ANALYTICS)
        (redis/hincrby (daily-key path) (today) 1)
        (redis/expire  (daily-key path) TTL-ANALYTICS)
        (redis/hincrby (weekly-key path) (this-week) 1)
        (redis/expire  (weekly-key path) TTL-ANALYTICS)
        (redis/hincrby (monthly-key path) (this-month) 1)
        (redis/expire  (monthly-key path) TTL-ANALYTICS)
        (redis/hincrby (group-key group-id) "clicks" 1)
        (redis/zadd    (group-ips-key group-id) (epoch-now) remote-addr)
        (redis/expire  (group-ips-key group-id) TTL-ANALYTICS)
        (redis/hincrby (group-daily-key group-id) (today) 1)
        (redis/expire  (group-daily-key group-id) TTL-ANALYTICS)
        (redis/hincrby (group-weekly-key group-id) (this-week) 1)
        (redis/expire  (group-weekly-key group-id) TTL-ANALYTICS)
        (redis/hincrby (group-monthly-key group-id) (this-month) 1)
        (redis/expire  (group-monthly-key group-id) TTL-ANALYTICS))      
      (write-country geoip path group-id remote-addr))
    (when referer
      (redis/wcar nil
        (redis/lpush  (referrers-key path) referer)
        (redis/expire (referrers-key path) TTL-ANALYTICS)))
    (catch Exception e
      (log/error e "analytics write failed" path))))


(defrecord AnalyticsConsumer []
  component/Lifecycle
  (start [{:keys [pubsub geoip] :as component}]
    (let [ch (chan (dropping-buffer 64))]
      (sub (:publication pubsub) :click ch)
      (thread
        (loop []
          (when-let [{:keys [path group-id remote-addr referer]} (<!! ch)]
            (write-analytics! geoip path group-id remote-addr referer)
            (recur))))
      (assoc component :ch ch)))
  (stop [{:keys [pubsub ch] :as component}]
    (unsub (:publication pubsub) :click ch)
    (close! ch)
    (dissoc component :ch)))
