(ns url-shortener.reports.api
  (:require [taoensso.carmine :as redis]
            [url-shortener.shared.utils :refer [resolve-report infer-report-type]]
            [url-shortener.schema :refer [ips-key referrers-key daily-key weekly-key monthly-key countries-key group-key group-ips-key group-daily-key group-weekly-key group-monthly-key group-countries-key]]))



(defn handle-api-report [{{token :token} :path-params}]
  (let [report (resolve-report token)]
    (if (seq report)
      (let [subject (get report "subject")
            type      (infer-report-type subject)]
        (case type
          "link"
          (let [[clicks url unique-ips daily weekly monthly referrers countries]
                (redis/wcar nil
                  (redis/hget    subject "clicks")
                  (redis/hget    subject "url")
                  (redis/zcard   (ips-key subject))
                  (redis/hgetall (daily-key subject))
                  (redis/hgetall (weekly-key subject))
                  (redis/hgetall (monthly-key subject))
                  (redis/lrange  (referrers-key subject) 0 -1)
                  (redis/hgetall (countries-key subject)))]
            {:status 200
             :body   {:path       subject
                      :url        url
                      :clicks     clicks
                      :unique-ips unique-ips
                      :daily      (apply hash-map daily)
                      :weekly     (apply hash-map weekly)
                      :monthly    (apply hash-map monthly)
                      :referrers  referrers
                      :countries  (apply hash-map countries)}})
          "group"
          (let [[clicks unique-ips daily weekly monthly countries]
                (redis/wcar nil
                  (redis/hget    (group-key subject) "clicks")
                  (redis/zcard   (group-ips-key subject))
                  (redis/hgetall (group-daily-key subject))
                  (redis/hgetall (group-weekly-key subject))
                  (redis/hgetall (group-monthly-key subject))
                  (redis/hgetall (group-countries-key subject)))]
            {:status 200
             :body   {:group-id   subject
                      :clicks     clicks
                      :unique-ips unique-ips
                      :daily      (apply hash-map daily)
                      :weekly     (apply hash-map weekly)
                      :monthly    (apply hash-map monthly)
                      :countries  (apply hash-map countries)}})
          {:status 400 :body "Unknown report type"}))
      {:status 404 :body "Report not found or expired"})))




