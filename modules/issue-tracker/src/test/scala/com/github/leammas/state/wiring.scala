package com.github.leammas.state

import cats.mtl.implicits._ // look ma, still not ambiguous
import cats.data.{Chain, ReaderT}
import cats.effect.concurrent.Ref
import cats.effect.{ContextShift, IO}
import cats.mtl.ApplicativeAsk
import com.github.leammas.issue.issuetracker.{
  EventSourcedIssue,
  IssueEvent,
  IssueId
}
import com.olegpy.meow.optics.MkLensToType
import fs2.concurrent.InspectableQueue
import shapeless.=:!=

object wiring {
  import com.github.leammas.issue.issuetracker.Issue._

  final case class ProcessState(
      issues: RefRuntime.InnerState[IssueId, IssueEvent])

  object ProcessState {
    private implicit val shift: ContextShift[IO] =
      cats.effect.internals.IOContextShift.global

    def init(
        issues: Map[IssueId, Chain[IssueEvent]] = Map.empty): ProcessState = {

      (for {
        i <- Ref.of[IO, Map[IssueId, Chain[IssueEvent]]](issues)
        q <- InspectableQueue.unbounded[IO, (IssueId, IssueEvent)]
      } yield ProcessState(RefRuntime.InnerState(i, q))).unsafeRunSync()
    }
  }

  type AsyncTestReader[T] = ReaderT[IO, ProcessState, T]

  type AbstractTestReader[F[_]] = ApplicativeAsk[F, ProcessState]

  implicit def asyncReaderDeriver[F[_]: AbstractTestReader, A](
      implicit neq: ProcessState =:!= A,
      mkLensToType: MkLensToType[ProcessState, A]): ApplicativeAsk[F, A] =
    com.olegpy.meow.hierarchy.deriveApplicativeAsk[F, ProcessState, A]

  val issues: Issues[AsyncTestReader] =
    RefRuntime[AsyncTestReader, IssueId](EventSourcedIssue.behavior)

}
