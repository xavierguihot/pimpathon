package pimpathon


object boolean {
  implicit class BooleanOps(val value: Boolean) extends AnyVal {
    def either[R](right: R): EitherCapturer[R] = new EitherCapturer[R](value, right)
  }

  class EitherCapturer[R](value: Boolean, right: R) {
    def or[L](left: => L): Either[L, R] = if (value) Right(right) else Left(left)
  }
}