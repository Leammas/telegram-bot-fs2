package com.github.leammas.testkit.statet

import cats.Applicative
import cats.implicits._
import cats.mtl.ApplicativeAsk

trait ReaderTransform {
  implicit def transform[F[_]: Applicative, S, A](
      implicit AA: ApplicativeAsk[F, S],
      L: HasLens[S, A]): ApplicativeAsk[F, A] = new ApplicativeAsk[F, A] {
    val applicative: Applicative[F] = AA.applicative

    def ask: F[A] = AA.ask.map(L.lens.get(_))

    def reader[B](f: A => B): F[B] = AA.reader(s => f(L.lens.get(s)))
  }
}

object ReaderTransform extends ReaderTransform
