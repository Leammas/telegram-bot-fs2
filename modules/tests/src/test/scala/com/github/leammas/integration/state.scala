package com.github.leammas.integration

import cats.data.ReaderT
import cats.effect.{IO, LiftIO}
import cats.mtl.ApplicativeAsk
import cats.{Monad, ~>}
import com.github.leammas.issue.issuetracker.{IssueId, Notifications}
import com.github.leammas.testkit.statet.HasLens
import com.github.leammas.testkit.statet.HasLens._
import fs2.concurrent.InspectableQueue
import monocle.macros.GenLens
import ru.pavkin.telegram.api.ChatId
import cats.implicits._
import com.github.leammas.testkit.statet.ReaderTransform._

object state {

  type TgBotState = ru.pavkin.telegram.test.state.ProcessState

  type IssueState = com.github.leammas.state.wiring.ProcessState

  final case class IntegrationState(bot: TgBotState, issue: IssueState)

  type IntegrationSyncState[T] = ReaderT[IO, IntegrationState, T]

  val tgbotTransform =
    new ~>[ReaderT[IO, TgBotState, ?], ReaderT[IO, TgBotState, ?]] {
      def apply[A](
          fa: ReaderT[IO, TgBotState, A]): ReaderT[IO, IntegrationState, A] =
        ReaderT[IO, IntegrationState, A](is => fa.run(is.bot))
    }

  val issueTransform =
    new ~>[ReaderT[IO, IssueState, ?], ReaderT[IO, IntegrationState, ?]] {
      def apply[A](
          fa: ReaderT[IO, IssueState, A]): ReaderT[IO, IntegrationState, A] =
        ReaderT[IO, IntegrationState, A](is => fa.run(is.bot))
    }

  def liftIO[F[_]](implicit LiftF: LiftIO[F]): ~>[IO, F] = new ~>[IO, F] {
    def apply[A](fa: IO[A]): F[A] = LiftF.liftIO(fa)
  }

  object StateNotifications {
    final case class InnerState(q: InspectableQueue[IO, ChatId]) extends AnyVal

    implicit def lens: HasLens[IntegrationState, InnerState] =
      GenLens[IntegrationState](_.bot.notifications).toHasLens

    def apply[F[_]: Monad: LiftIO](
                                    implicit AA: ApplicativeAsk[F, InnerState]): Notifications[F] =
      new Notifications[F] {
        def events: fs2.Stream[F, ChatId] =
          fs2.Stream.eval(AA.ask).flatMap(_.q.dequeue.translate(liftIO))
      }
  }

  val botApp = ru.pavkin.telegram.test.wiring.bot

  val issues = (k: IssueId) => com.github.leammas.state.wiring.issues(k).mapK(issueTransform)

  val issueWiring =
    new com.github.leammas.issue.issuetracker.Wiring[IntegrationSyncState](
      StateNotifications[IntegrationSyncState],
      issues )

  val botWiring = tgbotTransform(botApp).flatMap(x => x.mapK(tgbotTransform))

  def runTestApp(state: IntegrationState): IntegrationState = {
    val runningIssues = issueWiring.issueCreationProcess.run

    val runningBot = botWiring .flatMap(_.launch.compile.drain)

  }

}
