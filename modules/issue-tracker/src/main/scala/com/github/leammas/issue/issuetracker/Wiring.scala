package com.github.leammas.issue.issuetracker

import aecor.data.{EitherK, EventsourcedBehavior}
import aecor.encoding.{KeyDecoder, KeyEncoder, WireProtocol}
import aecor.runtime.akkageneric.GenericAkkaRuntime
import aecor.runtime.{EventJournal, Eventsourced}
import cats.effect.{Concurrent, Effect}
import cats.tagless.FunctorK
import com.github.leammas.MonadThrowable
import com.github.leammas.issue.common.ChatId
import com.github.leammas.issue.issuetracker.Issue.{IssueKey, Issues}
import com.github.leammas.issue.issuetracker.Wiring.EventsourcedRuntime

final class Wiring[F[_]: Concurrent](
    notifications: fs2.Stream[F, ChatId],
    journal: EventJournal[F, IssueKey, IssueEvent],
    issueRuntime: EventsourcedRuntime[EitherK[Issue, IssueRejection, ?[_]],
                                      F,
                                      Option[IssueState],
                                      IssueEvent,
                                      IssueId]) {

  val issues: Issues[F] =
    issueRuntime(journal)(EventSourcedIssue.behavior[F])

  val issueCreationProcessStep = new IssueCreationProcessStep[F](issues)

  val issueCreationProcess =
    new IssueCreationProcess(notifications, issueCreationProcessStep)

}
// .runBehavior[IssueKey, EitherK[Issue, IssueRejection, ?[_]], IO])

object Wiring {
  type EventsourcedRuntime[M[_[_]], F[_], S, E, K] =
    EventJournal[F, K, E] => EventsourcedBehavior[M, F, S, E] => F[K => M[F]]

  def toEventSourcedRuntime[M[_[_]]: FunctorK: WireProtocol,
                            F[_]: Effect,
                            S,
                            E,
                            K: KeyEncoder: KeyDecoder](
      akkaRuntime: GenericAkkaRuntime)
    : EventsourcedRuntime[M[F], F[_], S, E, K] =
    (j: EventJournal[F, K, E]) =>
      (b: EventsourcedBehavior[M, F, S, E]) => {
        akkaRuntime.runBehavior[K, M, F]("", Eventsourced(b, j))
    }
}
