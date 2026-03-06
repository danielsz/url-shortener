(ns url-shortener.report
  (:require [taoensso.carmine :as redis]
            [clojure.tools.logging :as log]
            [ring.util.response :refer [response bad-request header status not-found redirect]])
  (:import
   java.time.Instant
   java.security.SecureRandom
   java.util.Base64))

(def ^:private TTL-REPORT   (*  7 86400))
(defn- reports-key   [path] (str path ":reports"))
(defn- report-key    [tok]  (str "report:" tok))
(defn- ips-key       [path] (str path ":ips"))
(defn- referrers-key [path] (str path ":referrers"))
(defn- daily-key     [path] (str path ":daily"))


(defn- generate-token []
  (let [bytes (byte-array 16)]
    (.nextBytes (SecureRandom.) bytes)
    (.encodeToString (Base64/getUrlEncoder) bytes)))

(defn handle-create-report [{{:keys [user path]} :params}]
  (let [owner (redis/wcar nil (redis/hget path "user"))]
    (if (= owner user)
      (let [token (generate-token)]
        (redis/wcar nil
                    (redis/hset  (report-key token) "owner" user "path" path "created" (.getEpochSecond (Instant/now)))
                    (redis/expire (report-key token) TTL-REPORT)
                    (redis/sadd   (reports-key path) token))
        (response (str (System/getProperty "shorten.endpoint") "report/" token)))
      (status 403))))

(defn handle-report [{{token :token} :path-params}]
  (let [rk     (report-key token)
        report (apply hash-map (redis/wcar nil (redis/hgetall rk)))]
    (if (seq report)
      (let [path (get report "path")
            [clicks unique-ips daily referrers]
            (redis/wcar nil
              (redis/hget    path "clicks")
              (redis/zcard   (ips-key path))
              (redis/hgetall (daily-key path))
              (redis/lrange  (referrers-key path) 0 -1))]
        {:status  200
         :headers {"Content-Type" "application/json"}
         :body    {:path       path
                   :clicks     clicks
                   :unique-ips unique-ips
                   :daily      (apply hash-map daily)
                   :referrers  referrers}})
      {:status 404 :body "Report not found or expired"})))
