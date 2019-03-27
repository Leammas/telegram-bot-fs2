package com.github.leammas

import cats.ApplicativeError

object ApplicativeThrowable {
  type ApplicativeThrowable[F[_]] = ApplicativeError[F, Throwable]
}
