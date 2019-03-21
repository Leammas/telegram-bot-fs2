package com.github.leammas.issue.issuetracker

import cats.effect.Concurrent
import com.github.leammas.issue.common.ChatId
import com.github.leammas.issue.issuetracker.Issue.Issues

final class Wiring[F[_]: Concurrent](notifications: fs2.Stream[F, ChatId],
                                     issues: Issues[F]) {

  val issueCreationProcessStep = new IssueCreationProcessStep[F](issues)

  val issueCreationProcess =
    new IssueCreationProcess(notifications, issueCreationProcessStep)

}
