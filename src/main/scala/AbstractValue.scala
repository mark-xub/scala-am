import scalaz.Semigroup

/**
 * Each primitive has to implement this trait.
 */
trait Primitive[Addr, Abs] {
  /** The name of the primitive */
  val name: String
  /** Calls the primitive.
   * @param fexp: the expression with which the primitive has been called
   * @param args: the arguments with which the primitive has been called, both their expression and their value
   * @param store: the store
   * @return either an error, or the value returned by the primitive along with the updated store
   */
  def call[Exp : Expression](fexp : Exp, args: List[(Exp, Abs)], store: Store[Addr, Abs]): Either[String, (Abs, Store[Addr, Abs])]
}

/** Abstract values are abstract representations of the possible values of a variable */
trait AbstractValue[A] extends Semigroup[A] {
  /** Can this abstract value be considered true for conditionals? */
  def isTrue(x: A): Boolean
  /** Can this abstract value be considered false for conditionals? */
  def isFalse(x: A): Boolean
  /** Is this an erroneous value? (and only an erroneous value) */
  def isError(x: A): Boolean
  /** Is this the null value? (Scheme's null?, returns an abstract boolean) */
  def isNull(x: A): A
  /** Is this a cons cell? (Scheme's pair?) */
  def isCons(x: A): A
  /** Is this a character? (Scheme's char?) */
  def isChar(x: A): A
  /** Is this a symbol? (Scheme's symbol?) */
  def isSymbol(x: A): A
  /** Is this a string? (Scheme's string?) */
  def isString(x: A): A
  /** Is this an integer? (Scheme's integer?) */
  def isInteger(x: A): A
  /** Fold a function over the values contained in this abstract values. This
      should be redefined only for container-like abstract values (e.g., for a
      set abstraction) */
  def foldValues[B](x: A, f: A => Set[B]): Set[B]
  /** Join operation on lattice elements  */
  def join(x: A, y: A): A
  def append(x: A, y: => A): A = join(x, y)
  /** Meet operation on lattice elements */
  def meet(x: A, y: A): A
  /** Checks whether x subsumes y */
  def subsumes(x: A, y: A): Boolean
  /** Adds two values */
  def plus(x: A, y: A): A
  /** Subtracts two values */
  def minus(x: A, y: A): A
  /** Multiplies two values */
  def times(x: A, y: A): A
  /** Divides two values */
  def div(x: A, y: A): A
  /** x % y */
  def modulo(x: A, y: A): A
  /** Computes the ceiling of x */
  def ceiling(x: A): A
  /** Computes the log of x */
  def log(x: A): A
  /** Is x strictly smaller than y? */
  def lt(x: A, y: A): A
  /** Is x numerically equal to y? */
  def numEq(x: A, y: A): A
  /** Negation */
  def not(x: A): A
  /** Conjunction */
  def and(x: A, y: => A): A
  /** Disjunction */
  def or(x: A, y: => A): A
  /** Equality test (pointer comparison for cons cells, i.e., Scheme's eq?) */
  def eq(x: A, y: A): A
  /** Takes the car of a cons cell */
  def car[Addr : Address](x: A): Set[Addr]
  /** Takes the cdr of a cons cell */
  def cdr[Addr : Address](x: A): Set[Addr]
  /** Returns a random integer bounded by x */
  def random(x: A): A
  /** Returns the string representation of this value */
  def toString[Addr : Address](x: A, store: Store[Addr, A]): String

  /** Extract closures contained in this value */
  def getClosures[Exp : Expression, Addr : Address](x: A): Set[(Exp, Environment[Addr])]
  /** Extract primitives contained in this value */
  def getPrimitive[Addr : Address](x: A): Option[Primitive[Addr, A]]
}

/** Concrete values have to be injected to become abstract */
trait AbstractInjection[A] {
  /** Name of this lattice */
  def name: String
  /** Bottom element of the lattice */
  def bottom: A
  /** Injection of an error value */
  def error(x: A): A
  /** Injection of an integer */
  def inject(x: Int): A
  /** Injection of a string */
  def inject(x: String): A
  /** Injection of a boolean */
  def inject(x: Boolean): A
  /** Injection of a character */
  def inject(x: Char): A
  /** Injection of a primitive function */
  def inject[Addr : Address](x: Primitive[Addr, A]): A
  /** Injection of a closure */
  def inject[Exp : Expression, Addr : Address](x: (Exp, Environment[Addr])): A
  /** Injection of a symbol */
  def injectSymbol(x: String): A
  /** Creates a cons cell */
  def cons[Addr : Address](car: Addr, cdr: Addr): A
  /** Nil value */
  def nil: A

}

/** This is where we define (Scheme) primitives */
class Primitives[Addr, Abs]
  (implicit abs: AbstractValue[Abs], absi: AbstractInjection[Abs],
    addr: Address[Addr], addri: AddressInjection[Addr]) {

  /** This is how a primitive is defined by extending Primitive */
  object Cons extends Primitive[Addr, Abs] {
    val name = "cons"
    def call[Exp : Expression](fexp : Exp, args: List[(Exp, Abs)], store: Store[Addr, Abs]) = args match {
      case (carexp, car) :: (cdrexp, cdr) :: Nil => {
        val cara = addri.cell(carexp)
        val cdra = addri.cell(cdrexp)
        Right((absi.cons(cara, cdra), store.extend(cara, car).extend(cdra, cdr)))
      }
      case l => Left(s"cons: 2 operands expected, got ${l.size} instead")
    }
  }

  /* Other primitives are much simpler than cons and sometimes don't need access
   * to the store, or have a fixed amount of arguments, so we define a few
   * helper classes to define them */

  /** A primitive taking no argument, e.g., (newline) */
  class NullaryOperation(val name: String, f: => Abs) extends Primitive[Addr, Abs] {
    def call[Exp : Expression](fexp: Exp, args: List[(Exp, Abs)], store: Store[Addr, Abs]) = args match {
      case Nil => Right((f, store))
      case l => Left(s"${name}: no operand expected, got ${l.size} instead")
    }
  }
  object NullaryOperation {
    def apply(name: String, f: => Abs) = new NullaryOperation(name, f)
  }
  /** A primitive taking a single argument, e.g., (random 1) */
  case class UnaryOperation(name: String, f: Abs => Abs) extends Primitive[Addr, Abs] {
    def call[Exp : Expression](fexp: Exp, args: List[(Exp, Abs)], store: Store[Addr, Abs]) = args match {
      case (_, x) :: Nil => Right((f(x), store))
      case l => Left(s"${name}: 1 operand expected, got ${l.size} instead")
    }
  }
  /** A primitive taking two arguments, e.g., (modulo 5 1) */
  case class BinaryOperation(name: String, f: (Abs, Abs) => Abs) extends Primitive[Addr, Abs] {
    def call[Exp : Expression](fexp: Exp, args: List[(Exp, Abs)], store: Store[Addr, Abs]) = args match {
      case (_, x) :: (_, y) :: Nil => Right((f(x, y), store))
      case l => Left(s"${name}: 2 operands expected, got ${l.size} instead")
    }
  }

  /** A primitive taking a single argument and modifying the store */
  case class UnaryStoreOperation(name: String, f: (Abs, Store[Addr, Abs]) => (Abs, Store[Addr, Abs])) extends Primitive[Addr, Abs] {
    def call[Exp : Expression](fexp: Exp, args: List[(Exp, Abs)], store: Store[Addr, Abs]) = args match {
      case (_, x) :: Nil => Right(f(x, store))
      case l => Left(s"${name}: 1 operand expected, got ${l.size} instead")
    }
  }

  /** A primitive taking two arguments and modifying the store */
  case class BinaryStoreOperation(name: String, f: (Abs, Abs, Store[Addr, Abs]) => (Abs, Store[Addr, Abs])) extends Primitive[Addr, Abs] {
    def call[Exp : Expression](fexp: Exp, args: List[(Exp, Abs)], store: Store[Addr, Abs]) = args match {
      case (_, x) :: (_, y) :: Nil => Right(f(x, y, store))
      case l => Left(s"${name}: 2 operand expected, got ${l.size} instead")
    }
  }

  /** A primitive that doesn't modify the store but takes a variable amount of arguments */
  abstract class VariadicOperation extends Primitive[Addr, Abs] {
    def call(args: List[Abs]): Either[String, Abs]
    def call[Exp : Expression](fexp: Exp, args: List[(Exp, Abs)], store: Store[Addr, Abs]) =
      call(args.map({ case (_, v) => v })) match {
        case Right(v) => Right((v, store))
        case Left(err) => Left(err)
      }
  }

  /* Some primitives are defined by extending one of the helper class */

  object Plus extends VariadicOperation {
    val name = "+"
    def call(args: List[Abs]) = args match {
      case Nil => Right(absi.inject(0))
      case x :: rest => call(rest) match {
        case Right(y) => Right(abs.plus(x, y))
        case Left(err) => Left(err)
      }
    }
  }
  object Minus extends VariadicOperation {
    val name = "-"
    def call(args: List[Abs]) = args match {
      case Nil => Left("-: at least 1 operand expected, got 0")
      case x :: Nil => Right(abs.minus(absi.inject(0), x))
      case x :: rest => Plus.call(rest) match {
        case Right(y) => Right(abs.minus(x, y))
        case Left(err) => Left(err)
      }
    }
  }
  object Times extends VariadicOperation {
    val name = "*"
    def call(args: List[Abs]) = args match {
      case Nil => Right(absi.inject(1))
      case x :: rest => call(rest) match {
        case Right(y) => Right(abs.times(x, y))
        case Left(err) => Left(err)
      }
    }
  }
  object Div extends VariadicOperation {
    val name = "/"
    def call(args: List[Abs]) = args match {
      case Nil => Left("/: at least 1 operand expected, got 0")
      case x :: rest => Times.call(rest) match {
        case Right(y) => Right(abs.div(x, y))
        case Left(err) => Left(err)
      }
    }
  }

  /* Some primitives can be defined as just a function that we pass to one of the helper class' constructor */

  private def newline: Abs = {
    println("")
    absi.bottom
  }
  private def display(v: Abs): Abs = { print(v); absi.bottom }

  private def car(v: Abs, store: Store[Addr, Abs]): Abs =
    abs.car(v).foldLeft(absi.bottom)((acc, a) => abs.join(acc, store.lookup(a)))

  private def cdr(v: Abs, store: Store[Addr, Abs]): Abs =
    abs.cdr(v).foldLeft(absi.bottom)((acc, a) => abs.join(acc, store.lookup(a)))


  /* Among them, recursive primitives need to be defined as a fixpoint operation,
   * otherwise abstract values (e.g., gcd(Int, Int)) can lead to infinite
   * loops */

  /** (define (gcd a b) (if (= b 0) a (gcd b (modulo a b)))) */
  private def gcd(a: Abs, b: Abs, visited: Set[(Abs, Abs)]): Abs = {
    if (visited.contains(a, b)) {
      absi.bottom
    } else {
      val cond = abs.numEq(b, absi.inject(0))
      val t = if (abs.isTrue(cond)) { a } else { absi.bottom }
      val f = if (abs.isFalse(cond)) { gcd(b, abs.modulo(a, b), visited + ((a, b))) } else { absi.bottom }
      abs.join(t, f)
    }
  }
  private def gcd(a: Abs, b: Abs): Abs = gcd(a, b, Set())

  /** (define (equal? a b) (or (eq? a b) (and (null? a) (null? b)) (and (pair? a) (pair? b) (equal? (car a) (car b)) (equal? (cdr a) (cdr b))))) */
  private def equal(a: Abs, b: Abs, store: Store[Addr, Abs], visited: Set[(Abs, Abs)]): Abs = {
    if (visited.contains(a, b)) {
      absi.bottom
    } else {
      val visited2 = visited + ((a, b))
      abs.or(abs.eq(a, b),
        abs.or(abs.and(abs.isNull(a), abs.isNull(b)),
          abs.and(abs.isCons(a),
            abs.and(abs.isCons(b),
              abs.and(equal(car(a, store), car(b, store), store, visited2),
                equal(cdr(a, store), cdr(b, store), store, visited2))))))
    }
  }
  private def equal(a: Abs, b: Abs, store: Store[Addr, Abs]): Abs = equal(a, b, store, Set())

  /** (define (length l) (if (pair? l) (+ 1 (length (cdr l))) (if (null? l) 0 (error "length called with a non-list")))) */
  private def length(l: Abs, store: Store[Addr, Abs], visited: Set[Abs]): Abs = {
    if (visited.contains(l)) {
      absi.bottom
    } else {
      val visited2 = visited + l
      val cond = abs.isCons(l)
      val t = if (abs.isTrue(cond)) { abs.plus(absi.inject(1), length(cdr(l, store), store, visited2)) } else { absi.bottom }
      val f = if (abs.isFalse(cond)) {
        val fcond = abs.isNull(l)
        val ft = if (abs.isTrue(fcond)) { absi.inject(0) } else { absi.bottom }
        val ff = if (abs.isFalse(fcond)) { absi.error(absi.inject("length called with a non-list")) } else { absi.bottom }
        abs.join(ft, ff)
      } else {
        absi.bottom
      }
      abs.join(t, f)
    }
  }
  private def length(l: Abs, store: Store[Addr, Abs]): Abs = length(l, store, Set())

  /** Bundles all the primitives together */
  val all: List[Primitive[Addr, Abs]] = List(
    Plus, Minus, Times, Div,
    BinaryOperation("quotient", abs.div),
    BinaryOperation("<", abs.lt), // TODO: <, <=, =, >, >= should accept any number of arguments
    BinaryOperation("<=", (x, y) => abs.or(abs.lt(x, y), abs.numEq(x, y))),
    BinaryOperation("=", abs.numEq),
    BinaryOperation(">", (x, y) => abs.and(abs.not(abs.lt(x, y)), abs.not(abs.numEq(x, y)))),
    BinaryOperation(">=", (x, y) => abs.not(abs.lt(x, y))),
    BinaryOperation("modulo", abs.modulo),
    BinaryOperation("gcd", gcd),
    UnaryOperation("not", abs.not),
    UnaryOperation("random", abs.random),
    UnaryOperation("ceiling", abs.ceiling),
    UnaryOperation("log", abs.log),
    UnaryOperation("zero?", (x) => abs.numEq(absi.inject(0), x)), /* (define (zero? x) (= x 0)) */
    UnaryOperation("positive?", (x) => abs.lt(absi.inject(0), x)), /* (define (positive? x) (< 0 x)) */
    UnaryOperation("negative?", (x) => abs.lt(x, absi.inject(0))), /* (define (negative? x) (< x 0)) */
    UnaryOperation("odd?", (x) => abs.numEq(absi.inject(1), abs.modulo(x, absi.inject(2)))), /* (define (odd? x) (= 1 (modulo x 2))) */
    UnaryOperation("even?", (x) => abs.numEq(absi.inject(0), abs.modulo(x, absi.inject(2)))), /* (define (even? x) (= 0 (modulo x 2))) */
    UnaryOperation("display", display),
    NullaryOperation("newline", newline),
    Cons,
    UnaryStoreOperation("car", (v, store) => (car(v, store), store)),
    UnaryStoreOperation("cdr", (v, store) => (cdr(v, store), store)),
    UnaryStoreOperation("caar", (v, store) => (car(car(v, store), store), store)),
    UnaryStoreOperation("cadr", (v, store) => (car(cdr(v, store), store), store)),
    UnaryStoreOperation("cddr", (v, store) => (cdr(cdr(v, store), store), store)),
    UnaryStoreOperation("cdar", (v, store) => (cdr(car(v, store), store), store)),
    UnaryStoreOperation("caaar", (v, store) => (car(car(car(v, store), store), store), store)),
    UnaryStoreOperation("cdaar", (v, store) => (cdr(car(car(v, store), store), store), store)),
    UnaryStoreOperation("caadr", (v, store) => (car(car(cdr(v, store), store), store), store)),
    UnaryStoreOperation("cdadr", (v, store) => (cdr(car(cdr(v, store), store), store), store)),
    UnaryStoreOperation("caddr", (v, store) => (car(cdr(cdr(v, store), store), store), store)),
    UnaryStoreOperation("cdddr", (v, store) => (cdr(cdr(cdr(v, store), store), store), store)),
    UnaryStoreOperation("cadar", (v, store) => (car(cdr(car(v, store), store), store), store)),
    UnaryStoreOperation("cddar", (v, store) => (cdr(cdr(car(v, store), store), store), store)),
    BinaryStoreOperation("set-car!", (cell, v, store) =>
      (absi.bottom,
        abs.car(cell).foldLeft(store)((acc, a) => acc.update(a, v)))),
    BinaryStoreOperation("set-cdr!", (cell, v, store) =>
      (absi.bottom,
        abs.cdr(cell).foldLeft(store)((acc, a) => acc.update(a, v)))),
    UnaryOperation("error", absi.error),
    UnaryOperation("null?", abs.isNull),
    UnaryOperation("pair?", abs.isCons),
    UnaryOperation("char?", abs.isChar),
    UnaryOperation("symbol?", abs.isSymbol),
    UnaryOperation("string?", abs.isString),
    UnaryOperation("integer?", abs.isInteger),
    UnaryOperation("number?", abs.isInteger), // TODO: support other numbers as well
    BinaryOperation("eq?", abs.eq),
    BinaryStoreOperation("equal?", (a, b, store) => (equal(a, b, store), store)),
    UnaryStoreOperation("length", (v, store) => (length(v, store), store))
  )

  private val allocated = all.map({ prim => (prim.name, addri.primitive(prim.name), absi.inject(prim)) })
  val forEnv: List[(String, Addr)] = allocated.map({ case (name, a, _) => (name, a) })
  val forStore: List[(Addr, Abs)] =  allocated.map({ case (_, a, v) => (a, v) })
}

object AbstractValue
