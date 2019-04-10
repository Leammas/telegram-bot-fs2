package com.github.leammas.integration

import aecor.runtime.Eventsourced.Entities
import cats.data.ReaderT
import cats.effect.{ContextShift, IO, LiftIO}
import cats.mtl.ApplicativeAsk
import cats.{Monad, ~>}
import com.github.leammas.issue.issuetracker.{Issue, IssueId, IssueRejection, Notifications}
import com.github.leammas.testkit.statet.HasLens
import com.github.leammas.testkit.statet.HasLens._
import monocle.macros.GenLens
import ru.pavkin.telegram.api.ChatId
import cats.implicits._
import com.github.leammas.testkit.statet.ReaderTransform._
import cats.tagless.syntax.functorK._

import scala.concurrent.ExecutionContext.global
import cats.mtl.implicits._

object state {

  type TgBotState = ru.pavkin.telegram.test.state.ProcessState

  type IssueState = com.github.leammas.state.wiring.ProcessState

  final case class IntegrationState(bot: TgBotState, issue: IssueState)

  type IntegrationSyncState[T] = ReaderT[IO, IntegrationState, T]

  val tgbotTransform =
    new ~>[ReaderT[IO, TgBotState, ?], ReaderT[IO, IntegrationState, ?]] {
      def apply[A](
          fa: ReaderT[IO, TgBotState, A]): ReaderT[IO, IntegrationState, A] =
        fa.local(_.bot)
    }

  val issueTransform =
    new ~>[ReaderT[IO, IssueState, ?], ReaderT[IO, IntegrationState, ?]] {
      def apply[A](
          fa: ReaderT[IO, IssueState, A]): ReaderT[IO, IntegrationState, A] =
        fa.local(_.issue)
    }

  def liftIO[F[_]](implicit LiftF: LiftIO[F]): ~>[IO, F] = new ~>[IO, F] {
    def apply[A](fa: IO[A]): F[A] = LiftF.liftIO(fa)
  }

  object StateNotifications {
    type BorrowedState =
      ru.pavkin.telegram.test.state.StateAdminNotifier.InnerState

    implicit def lens: HasLens[IntegrationState, BorrowedState] =
      GenLens[IntegrationState](_.bot.notifications).toHasLens

    //size bound& :S
    def apply[F[_]: Monad: LiftIO](
        implicit AA: ApplicativeAsk[F, BorrowedState]): Notifications[F] =
      new Notifications[F] {
        def events: fs2.Stream[F, ChatId] =
          fs2.Stream.eval(AA.ask).flatMap(_.q.dequeue.head.translate(liftIO))
      }
  }

  import StateNotifications._

  val botApp = ru.pavkin.telegram.test.wiring.bot

  type IssueType[T] =
    com.github.leammas.state.wiring.ProcessSyncState[Either[IssueRejection, T]]

  type IssueIntegrationType[T] = IntegrationSyncState[Either[IssueRejection, T]]

  val issueAggregateTransform = new ~>[IssueType, IssueIntegrationType] {
    def apply[A](fa: IssueType[A]): IssueIntegrationType[A] = fa.local(_.issue)
  }

  val issues: Issue.Issues[IntegrationSyncState] = {
    val innerIssues: IssueId => Issue[IssueType] = (k: IssueId) =>
      com.github.leammas.state.wiring.issues(k)
    Entities(innerIssues.andThen(_.mapK(issueAggregateTransform)))
  }

  val issueWiring =
    new com.github.leammas.issue.issuetracker.Wiring[IntegrationSyncState](
      StateNotifications[IntegrationSyncState],
      issues)

  val runningBot: IntegrationSyncState[Unit] =
    tgbotTransform(botApp).flatMap(x =>
      x.launch.translate(tgbotTransform).compile.drain)

  val runningIssues = issueWiring.issueCreationProcess.run

  def runTestApp(state: IntegrationState): IntegrationState = {
    implicit val contextShift: ContextShift[IO] = IO.contextShift(global)
    (runningIssues, runningBot)
      .parMapN((_, _) => ())
      .run(state)
      .redeem(_ => state, _ => state)
      .unsafeRunSync()
  }

}
