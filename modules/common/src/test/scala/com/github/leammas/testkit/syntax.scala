package com.github.leammas.testkit

import cats.Monad
import fs2.concurrent.InspectableQueue
import cats.implicits._

object syntax {
  implicit final class DequeueCurrent[F[_]: Monad, A](val q: InspectableQueue[F, A]) /*extends AnyVal*/ {
    def dequeueCurrent: F[List[A]] = for {
      c <- q.getSize
      // not quite right if c > chunkSize
      elems <- q.tryDequeueChunk1(c)
    } yield elems.map(_.toList).orEmpty
  }
}
