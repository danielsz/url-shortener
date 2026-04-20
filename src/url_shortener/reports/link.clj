(ns url-shortener.reports.link
  (:require   
   [taoensso.carmine :as redis]
   [url-shortener.shared.utils :refer [find-or-create-report!]]
   [url-shortener.schema :refer [reports-key]]
   [ring.util.response :refer [response status]]
   [url-shortener.shared.pages.v2 :as pages]))


(defn create-link-report! [owner-id path]
  (let [actual-owner (redis/wcar nil (redis/hget path "owner-id"))]
    (if (= actual-owner owner-id)
      (response (str (System/getProperty "shortener.service") "report/"
                     (find-or-create-report! path reports-key)))
      (status 403))))


(defn handle-link-report [token report]
  (let [path      (get report "subject")
        [url desc] (redis/wcar nil (redis/hget path "url") (redis/hget path "description"))]
    {:status  200
     :headers {"Content-Type" "text/html"}
     :body    (pages/link-dashboard-page path url desc
                (str "@get('/report/" token "/stream')")
                nil)}))


