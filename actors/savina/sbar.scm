(define Capacity (int-top))
(define Haircuts (int-top))

(define waiting-room
  (a/actor "waiting-room" (waiting-customers barber-asleep)
           (enter (customer)
                  (if (= (length waiting-customers) Capacity)
                      (begin
                        (a/send customer full)
                        (a/become waiting-room waiting-customers barber-asleep))
                      (begin
                        (if barber-asleep
                            (begin
                              (a/send a/self next)
                              (a/become waiting-room (cons customer waiting-customers) #t))
                            (begin
                              (a/send customer wait)
                              (a/become waiting-room (cons customer waiting-customers) #f))))))
           (next ()
                 (if (pair? waiting-customers)
                     (let ((c (car waiting-customers)))
                       (a/send barber-actor enter c a/self)
                       (a/become waiting-room (cdr waiting-customers) barber-asleep))
                     (begin
                       (a/send barber-actor wait)
                       (a/become waiting-room waiting-customers #t))))
           (exit ()
                 (a/send barber-actor exit)
                 (a/terminate))))
(define barber
  (a/actor "barber" ()
           (enter (customer room)
                  (a/send customer start)
                  (a/send customer done)
                  (a/send room next)
                  (a/become barber))
           (wait ()
                 (a/become barber))
           (exit ()
                 (a/terminate))))
(define customer-factory
  (a/actor "customer-factory" (hairs-cut-so-far id-gen)
           (start ()
                  (letrec ((loop (lambda (i)
                                   (if (= i Haircuts)
                                       #f
                                       (let ((c (a/create customer (+ i id-gen))))
                                         (a/send waiting-room-actor enter c)
                                         (loop (+ i 1)))))))
                    (loop 0)
                    (a/become customer-factory hairs-cut-so-far (+ id-gen Haircuts))))
           (returned (customer)
                     (a/send waiting-room-actor enter customer)
                     (a/become customer-factory hairs-cut-so-far (+ id-gen 1)))
           (done ()
                 (if (= (+ hairs-cut-so-far 1) Haircuts)
                     (begin
                       (a/send waiting-room-actor exit)
                       (a/terminate))
                     (a/become customer-factory (+ 1 hairs-cut-so-far) id-gen)))))
(define customer
  (a/actor "customer" (id)
           (full ()
                 (a/send factory-actor returned a/self)
                 (a/become customer id))
           (wait ()
                 (a/become customer id))
           (start ()
                  (a/become customer id))
           (done ()
                 (a/send factory-actor done)
                 (a/terminate))))

(define barber-actor (a/create barber))
(define waiting-room-actor (a/create waiting-room '() #t))
(define factory-actor (a/create customer-factory 0 0))
(a/send factory-actor start)