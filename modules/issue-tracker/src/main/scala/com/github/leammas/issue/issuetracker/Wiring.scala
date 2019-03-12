package com.github.leammas.issue.issuetracker

import aecor.data.EventsourcedBehavior
import aecor.runtime.EventJournal
import com.github.leammas.issue.common.ChatId

final class Wiring[F[_]](notifications: fs2.Stream[F, ChatId], issueRuntime: Runtime[Issue, F, IssueState, IssueEvent, IssueId]) {

  val issueCreationProcessStep  = new IssueCreationProcessStep()

}

object Wiring {
  type Runtime[M[F], F[_], S, E, K] = EventsourcedBehavior[M, F, S, E] => K => F[M[F[]]]
}