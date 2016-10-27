;; Adapted from Savina benchmark ("Ping Pong" benchmarks, coming from Scala)
(letrec ((ping-actor
          (actor "ping" (count pong)
                 (start ()
                        (send pong send-ping self)
                        (become ping-actor (- count 1) pong))
                 (ping ()
                       (send pong send-ping self)
                       (become ping-actor (- count 1) pong))
                 (send-pong ()
                            (if (> count 0)
                                (begin
                                  (send self ping)
                                  (become ping-actor count pong))
                                (begin
                                  (send pong stop)
                                  (terminate))))))
         (pong-actor
          (actor "pong" (count)
                 (stop () (terminate))
                 (send-ping (to)
                            (send to send-pong)
                            (become  pong-actor (+ count 1)))))
         (pong (create pong-actor 0))
         (N 5)
         (ping (create ping-actor N pong)))
  (send ping start))
