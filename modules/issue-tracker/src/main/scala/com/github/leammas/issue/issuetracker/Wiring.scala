package com.github.leammas.issue.issuetracker

import cats.MonadError
import cats.effect.Sync
import com.github.leammas.issue.issuetracker.Issue.Issues

final class Wiring[F[_]: Sync](notifications: Notifications[F],
                               issues: Issues[F])(
                                      implicit F: MonadError[F, Throwable]) {

  val issueCreationProcessStep = new IssueCreationProcessStep[F](issues)

  val issueCreationProcess =
    new IssueCreationProcess(notifications, issueCreationProcessStep)

}
