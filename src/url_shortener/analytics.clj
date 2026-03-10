(ns url-shortener.analytics
  (:require [url-shortener.shared :refer [ip-validator]]
            [url-shortener.schema :refer [ips-key referrers-key daily-key TTL-ANALYTICS]]
            [clojure.tools.logging :as log]
            [taoensso.carmine :as redis])
  (:import java.time.LocalDate
           java.time.Instant))


(defn write-analytics! [geoip path remote-addr referer]
  (log/debug path remote-addr referer)
  (try
    (when (.isValid ip-validator remote-addr)
      (redis/wcar nil
                  (redis/zadd  (ips-key path) (.getEpochSecond (Instant/now)) remote-addr)
                  (redis/expire (ips-key path) TTL-ANALYTICS)
                  (redis/hincrby (daily-key path) (str (LocalDate/now)) 1)
                  (redis/expire  (daily-key path) TTL-ANALYTICS)))
    (when-let [m (.get @(:client geoip) (java.net.InetAddress/getByName remote-addr) java.util.Map)]
      (redis/wcar nil
                  (redis/hincrby (str path ":countries") (:country (.get m "country_code")) 1)
                  (redis/expire  (str path ":countries") TTL-ANALYTICS)))
    (when referer
      (redis/wcar nil
        (redis/lpush  (referrers-key path) referer)
        (redis/expire (referrers-key path) TTL-ANALYTICS)))
    (catch Exception e
      (log/error e "analytics write failed" path))))
