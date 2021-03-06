(define result '())
(define (display x)(set! result (cons x result)))
(define (newline) (set! result (cons 'newline result)))

(define (create-hash-table size hash-fct . equal-fct)
  (let ((content (make-vector size))
        (same? (if (null? equal-fct) = (car equal-fct))))
    (define (next-index index)
      (remainder (+ index 1) size))
    (define (make-item status key info)
      (list status key info))
    (define (get-status item) (car item))
    (define (set-status! item status) (set-car! item status))
    (define (get-key item) (cadr item))
    (define (set-key! item key) (set-car! (cdr item) key))
    (define (get-info item) (caddr item))
    (define (set-info! item info) (set-car! (cddr item) info))
    (define (insert key info)
      (define (rehash-iter current)
        (let*  ((item (vector-ref content current))
                (status (get-status item)))
          (cond
            ((not (eq? status 'data))
             (set-status! item 'data)
             (set-key! item key)
             (set-info! item info))
            ((same? key (get-key item))
             (set-info! item info))
            (else (rehash-iter (next-index current))))))
      (rehash-iter (hash-fct key)))
    (define (find-item key)
      (define (rehash-iter current)
        (let* ((item (vector-ref content current))
               (status (get-status item)))
          (cond
            ((eq? status 'data)
             (if (same? key (get-key item))
                 item
                 (rehash-iter (next-index current))))
            ((eq? status 'empty) #f)
            (else (rehash-iter (next-index current))))))
      (rehash-iter (hash-fct key)))
    (define (retrieve key)
      (let ((temp (find-item key)))
        (if temp
            (get-info temp)
            #f)))
    (define (delete key)
      (let ((temp (find-item key)))
        (cond
          (temp
           (set-status! temp 'deleted)
           #t)
          (else #f))))
    (define (display-table)
      (let ((stop (vector-length content)))
        (define (iter current)
          (cond
            ((< current stop)
             (display current)
             (display "  ")
             (display (vector-ref content current))
             (newline)
             (iter (+ current 1)))))
        (iter 0)))
    (define (dispatch msg . args)
      (cond
        ((eq? msg 'insert) (insert (car args) (cadr args)))
        ((eq? msg 'delete) (delete (car args)))
        ((eq? msg 'retrieve) (retrieve (car args)))
        ((eq? msg 'display) (display-table))
        (else (error "unknown request -- create-hash-table" msg))))
    (do
        ((index (- (vector-length content) 1) (- index 1)))
      ((negative? index) 'done)
      (vector-set! content index (make-item 'empty '() '())))
    dispatch))

(define table (create-hash-table 13 (lambda (key) (modulo key 13))))
(table 'insert 1 79)
(table 'insert 4 69)
(table 'insert 14 98)
(table 'insert 7 72)
(table 'insert 27 14)
(table 'insert 11 50)
(table 'display)

(equal? result
        '(newline
         (empty () ())
         "  "
         12
         newline
         (data 11 50)
         "  "
         11
         newline
         (empty () ())
         "  "
         10
         newline
         (empty () ())
         "  "
         9
         newline
         (empty () ())
         "  "
         8
         newline
         (data 7 72)
         "  "
         7
         newline
         (empty () ())
         "  "
         6
         newline
         (empty () ())
         "  "
         5
         newline
         (data 4 69)
         "  "
         4
         newline
         (data 27 14)
         "  "
         3
         newline
         (data 14 98)
         "  "
         2
         newline
         (data 1 79)
         "  "
         1
         newline
         (empty () ())
         "  "
         0))