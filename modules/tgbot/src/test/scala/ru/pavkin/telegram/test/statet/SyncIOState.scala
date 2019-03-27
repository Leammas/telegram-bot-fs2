package ru.pavkin.telegram.test.statet

import cats.{Monad, Parallel}
import cats.data.{IndexedStateT, StateT}
import cats.effect.SyncIO
import cats.effect.concurrent.Ref
import cats.mtl.MonadState

trait SyncIOStateTOps {
  def pure[S, T](v: T): StateT[SyncIO, S, T] = StateT.pure(v)
}

object SyncIOState {
  type SyncIOState[S, T] = StateT[SyncIO, S, T]

  implicit def parallel[S]: Parallel[SyncIOState[S, ?], SyncIOState[S, ?]] =
    Parallel.identity[SyncIOState[S, ?]]

  object syntax extends SyncIOStateTOps
}

object RefState {
  type RefState[S, T] = StateT[SyncIO, Ref[SyncIO, S], T]

  private type StateTC[M[_], S] = {type l[A] = StateT[M, Ref[M, S], A]}

  implicit final def refStateState[M[_], S](implicit M: Monad[M]): MonadState[StateTC[M, S]#l, S] = {
    new MonadState[StateTC[M, S]#l, S] {
      import cats.implicits._
      val monad: Monad[StateTC[M, S]#l] = IndexedStateT.catsDataMonadForIndexedStateT

      def get: StateT[M, Ref[M, S], S] = StateT.get[M, Ref[M, S]].flatMapF(rs => rs.get)

      def set(s: S): StateT[M, Ref[M, S], Unit] = StateT.apply[M, Ref[M, S], Unit](rs => rs.set(s).map(_ => (rs, ())))

      def inspect[A](f: S => A): StateT[M, Ref[M, S], A] = StateT.inspectF[M, Ref[M, S], A](rs => rs.get.map(f))

      def modify(f: S => S): StateT[M, Ref[M, S], Unit] = StateT.modifyF[M, Ref[M, S]](rs => rs.update(f).map(_ => rs))
    }
  }

  //@todo randomize
  implicit def parallel[S]: Parallel[RefState[S, ?], RefState[S, ?]] =
    Parallel.identity[RefState[S, ?]]
}
