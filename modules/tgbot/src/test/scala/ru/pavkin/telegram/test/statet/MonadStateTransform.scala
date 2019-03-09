package ru.pavkin.telegram.test.statet

import cats.Monad
import cats.implicits._
import cats.mtl.MonadState

trait MonadStateTransform {
  implicit def transformMS[F[_], S, A](implicit FMS: MonadState[F, S],
                                       L: HasLens[S, A]): MonadState[F, A] =
    new MonadState[F, A] {
      implicit val monad: Monad[F] = FMS.monad

      def get: F[A] =
        FMS.get.map(L.lens.get)

      def set(a: A): F[Unit] =
        FMS.get.flatMap(s => FMS.set(L.lens.set(a)(s)))

      def modify(f: A => A): F[Unit] =
        FMS.get.flatMap(s => FMS.set(L.lens.modify(f)(s)))

      def inspect[C](f: A => C): F[C] =
        FMS.get.map(s => f(L.lens.get(s)))
    }
}

object MonadStateTransform extends MonadStateTransform

