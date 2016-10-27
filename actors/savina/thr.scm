;; Adapted from Savina benchmarks ("Thread Ring" benchmark, coming from Theron)
(letrec ((N 3)
         (R 10)
         (threadring-actor
          (actor "thread-ring" (id actors-in-ring next-actor)
                 (ping (pings-left)
                       (printf "pinging actor ~a~n" id)
                       (if (> pings-left 0)
                           (begin
                             (send next-actor ping (- pings-left 1))
                             (become threadring-actor id actors-in-ring next-actor))
                           (begin
                             (send next-actor exit actors-in-ring)
                             (become threadring-actor id actors-in-ring next-actor))))
                 (data (next)
                       (become threadring-actor id actors-in-ring next))
                 (exit (exits-left)
                       (printf "actor ~a is exiting~n" id)
                       (if (> exits-left 1) ; different from original benchmark (original will send an exit to a dead actor)
                           (send next-actor exit (- exits-left 1))
                           #f)
                       (terminate))))
         (ring-actors (build-vector N
                                    (lambda (i) (create threadring-actor i N #f))))
         (loop-next (lambda (i)
                      (if (= i N)
                          'done
                          (begin
                            (send (vector-ref ring-actors i)
                                  data (vector-ref ring-actors (modulo (+ i 1) N)))
                            (loop-next (+ i 1)))))))
  (loop-next 0)
  (send (vector-ref ring-actors 0) ping R))
