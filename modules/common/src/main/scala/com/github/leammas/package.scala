package com.github

import cats.MonadError

package object leammas {
  type MonadThrowable[F[_]] = MonadError[F, Throwable]
}
