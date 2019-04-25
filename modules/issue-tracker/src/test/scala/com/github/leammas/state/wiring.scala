package com.github.leammas.state

import cats.Applicative
import cats.data.{Chain, ReaderT}
import cats.effect.concurrent.Ref
import cats.effect.{ContextShift, IO}
import cats.mtl.ApplicativeAsk
import com.github.leammas.issue.issuetracker.{EventSourcedIssue, IssueEvent, IssueId}
import com.github.leammas.testkit.statet.HasLens
import monocle.macros.GenLens
import com.github.leammas.testkit.statet.HasLens._
import com.github.leammas.testkit.statet.ReaderTransform._
import com.github.leammas.issue.issuetracker.Issue._
import fs2.concurrent.InspectableQueue

object wiring {

  final case class ProcessState(
      issues: RefRuntime.InnerState[IssueId, IssueEvent])

  object ProcessState {
    private implicit val shift: ContextShift[IO] =
      cats.effect.internals.IOContextShift.global

    def init(issues: Map[IssueId, Chain[IssueEvent]] = Map.empty): ProcessState = {

      (for {
        i <- Ref.of[IO, Map[IssueId, Chain[IssueEvent]]](issues)
        q <- InspectableQueue.unbounded[IO, (IssueId, IssueEvent)]
      } yield ProcessState(RefRuntime.InnerState(i, q))).unsafeRunSync()
    }
  }

  type AsyncTestReader[T] = ReaderT[IO, ProcessState, T]

  implicit val aa: ApplicativeAsk[AsyncTestReader, ProcessState] =
    new ApplicativeAsk[AsyncTestReader, ProcessState] {
      val applicative: Applicative[AsyncTestReader] =
        implicitly[Applicative[AsyncTestReader]]

      def ask: AsyncTestReader[ProcessState] =
        ReaderT[IO, ProcessState, ProcessState] { x =>
          IO.pure(x)
        }

      def reader[A](f: ProcessState => A): AsyncTestReader[A] =
        ReaderT[IO, ProcessState, A] { x =>
          IO.pure(f(x))
        }
    }

  implicit def lens
    : HasLens[ProcessState, RefRuntime.InnerState[IssueId, IssueEvent]] =
    GenLens[ProcessState](_.issues).toHasLens

  val issues: Issues[AsyncTestReader] =
    RefRuntime[AsyncTestReader, IssueId](EventSourcedIssue.behavior)

}
