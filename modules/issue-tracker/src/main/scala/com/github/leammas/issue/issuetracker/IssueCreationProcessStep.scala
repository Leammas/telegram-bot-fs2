package com.github.leammas.issue.issuetracker

import java.util.UUID

import cats.MonadError
import cats.effect.Concurrent
import cats.implicits._
import com.github.leammas.issue.common.ChatId
import com.github.leammas.issue.issuetracker.Issue.Issues

import scala.language.higherKinds

final class IssueCreationProcess[F[_]: Concurrent](
    notifications: fs2.Stream[F, ChatId],
    step: IssueCreationProcessStep[F])(implicit F: MonadError[F, Throwable]) {
  def run: F[Unit] =
    notifications.mapAsync(8)(step.processNotification).compile.drain
}

final class IssueCreationProcessStep[F[_]](issues: Issues[F])(
    implicit F: MonadError[F, Throwable]) {

  final private val IssueDescription = "Chat alert"

  private val id = IssueId(
    UUID.fromString("561db430-4351-11e9-b475-0800200c9a66"))

  //@todo test idea: create one, create two, fail, check state, show diff, parallelism
  def processNotification(alertedChatId: ChatId): F[Unit] =
    F.rethrow(
      issues(id)
        .create(alertedChatId, IssueDescription)
        .map(r =>
          r.leftMap(e => new RuntimeException(s"Error creating issue $e"))))

}
