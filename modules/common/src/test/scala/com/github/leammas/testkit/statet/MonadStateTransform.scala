package com.github.leammas.testkit.statet

import cats.Monad
import cats.effect.concurrent.Ref
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

  implicit def transformMRefS[F[_], S, A](implicit FMS: MonadState[F, Ref[F, S]],
                                       L: HasLens[S, A]): MonadState[F, A] =
    new MonadState[F, A] {
      implicit val monad: Monad[F] = FMS.monad

      def get: F[A] =
        FMS.get.flatMap(rs => rs.get.map(L.lens.get))

      def set(a: A): F[Unit] =
        FMS.get.flatMap(s => s.update(L.lens.set(a)(_)))

      def modify(f: A => A): F[Unit] =
        FMS.get.flatMap(s => s.update(L.lens.modify(f)(_)))

      def inspect[C](f: A => C): F[C] =
        FMS.get.flatMap(s => s.get.map(x => f(L.lens.get(x))))
    }
}

object MonadStateTransform extends MonadStateTransform

