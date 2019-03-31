package com.github.leammas.testkit.statet

import cats.Parallel
import cats.data.StateT
import cats.effect.SyncIO

trait SyncIOStateTOps {
  def pure[S, T](v: T): StateT[SyncIO, S, T] = StateT.pure(v)
}

object SyncIOState {
  type SyncIOState[S, T] = StateT[SyncIO, S, T]

  implicit def parallel[S]: Parallel[SyncIOState[S, ?], SyncIOState[S, ?]] =
    Parallel.identity[SyncIOState[S, ?]]

  object syntax extends SyncIOStateTOps
}
