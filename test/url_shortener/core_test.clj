(ns url-shortener.core-test
  (:require [clojure.test :refer [is deftest testing]]
            [url-shortener.core :refer [handle-redirect handle-create create-short-url]]
            [taoensso.carmine :as redis])
  (:import  java.net.URL))

(deftest url-shortener-test
  (testing "Test all"

  (redis/wcar nil
      (redis/set "/asdf" "http://google.com"))

  (is (= (redis/wcar nil
              (redis/get "/asdf"))
         "http://google.com"))

  (let [resp (handle-redirect {:uri "/asdf"})]
    (is (= "http://google.com" (-> resp :headers (get "Location")))))

  ; 404 works
  (is (= 404
         (:status (handle-redirect {:path "/unknown"}))))

  ; Gen and retrieve
  (let [orig-url "https://github.com/ptaoussanis/carmine"
        new-url (create-short-url orig-url)
        path (.getPath (URL. new-url))]
    ; Saved to redis
    (is (= (redis/wcar nil (redis/get path))
           orig-url))
    ; Returns correct value
    (is (= (:status (handle-redirect {:uri path}))
           302))
    (is (= (-> (handle-redirect {:uri path})
               :headers
               (get "Location"))
           orig-url))

    ; Hashes sensibly
    (is (= (:body (handle-create {:params {"url" orig-url}}))
           new-url))
    ))
)
