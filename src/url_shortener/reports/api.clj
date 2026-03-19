(ns url-shortener.reports.api
  (:require [taoensso.carmine :as redis]
            [url-shortener.shared.utils :refer [resolve-report]]
            [url-shortener.schema :refer [ips-key referrers-key daily-key]]))


(defn handle-api-report [{{token :token} :path-params}]
  (let [report (resolve-report token)]
    (if (seq report)
      (let [path (get report "path")
            [clicks url unique-ips daily referrers countries]
            (redis/wcar nil
              (redis/hget    path "clicks")
              (redis/hget    path "url")
              (redis/zcard   (ips-key path))
              (redis/hgetall (daily-key path))
              (redis/lrange  (referrers-key path) 0 -1)
              (redis/hgetall (str path ":countries")))]
        {:status  200
         :body    {:path       path
                   :url        url
                   :clicks     clicks
                   :unique-ips unique-ips
                   :daily      (apply hash-map daily)
                   :referrers  referrers
                   :countries  (apply hash-map countries)}})
      {:status 404 :body "Report not found or expired"})))
