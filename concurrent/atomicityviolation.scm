;; Inspired from Fig. 1 of "Learning from Mistakes -- A Comprehensive Study on Real World Concurrency Bug Characteristics"
(letrec ((v '(foo))
         (thread1 (lambda ()
                    (if (not (null? v))
                        (display (car v)))))
         (thread2 (lambda ()
                    (set! v '())))
         (t1 (spawn (thread1)))
         (t2 (spawn (thread2))))
  (join t1)
  (join t2))
