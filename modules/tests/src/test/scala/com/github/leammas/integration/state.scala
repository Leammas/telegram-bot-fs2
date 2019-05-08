package com.github.leammas.integration

import aecor.runtime.Eventsourced.Entities
import cats.data.ReaderT
import cats.effect.{ContextShift, IO, LiftIO}
import cats.implicits._
import cats.mtl.ApplicativeAsk
import cats.mtl.implicits._
import cats.tagless.syntax.functorK._
import cats.{Monad, ~>}
import com.github.leammas.issue.issuetracker.{Issue, IssueId, IssueRejection, Notifications}
import com.olegpy.meow.optics.MkLensToType
import ru.pavkin.telegram.api.ChatId
import shapeless.=:!=

import scala.concurrent.ExecutionContext.global

object state {

  type TgBotState = ru.pavkin.telegram.test.state.ProcessState

  type IssueState = com.github.leammas.state.wiring.ProcessState

  final case class IntegrationState(bot: TgBotState, issue: IssueState)

  type IntegrationAsyncReader[T] = ReaderT[IO, IntegrationState, T]

  type AbstractTestReader[F[_]] = ApplicativeAsk[F, IntegrationState]

  implicit def asyncReaderDeriver[F[_]: AbstractTestReader, A](
      implicit neq: IntegrationState =:!= A,
      mkLensToType: MkLensToType[IntegrationState, A]): ApplicativeAsk[F, A] =
    com.olegpy.meow.hierarchy.deriveApplicativeAsk[F, IntegrationState, A]

  val tgbotTransform
    : ~>[ReaderT[IO, TgBotState, ?], ReaderT[IO, IntegrationState, ?]] =
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

    def apply[F[_]: Monad: LiftIO](
        implicit AA: ApplicativeAsk[F, BorrowedState]): Notifications[F] =
      new Notifications[F] {
        def events: fs2.Stream[F, ChatId] =
          fs2.Stream
            .eval(AA.ask)
            .flatMap(_.queue.dequeue.head.translate(liftIO))
      }

  }

  val botApp = ru.pavkin.telegram.test.wiring.bot

  type IssueType[T] =
    com.github.leammas.state.wiring.AsyncTestReader[Either[IssueRejection, T]]

  type IssueIntegrationType[T] =
    IntegrationAsyncReader[Either[IssueRejection, T]]

  val issueAggregateTransform = new ~>[IssueType, IssueIntegrationType] {
    def apply[A](fa: IssueType[A]): IssueIntegrationType[A] = fa.local(_.issue)
  }

  val issues: Issue.Issues[IntegrationAsyncReader] = {
    val innerIssues: IssueId => Issue[IssueType] = (k: IssueId) =>
      com.github.leammas.state.wiring.issues(k)
    Entities(innerIssues.andThen(_.mapK(issueAggregateTransform)))
  }

  val issueWiring =
    new com.github.leammas.issue.issuetracker.Wiring[IntegrationAsyncReader](
      StateNotifications[IntegrationAsyncReader],
      issues)

  val runningBot: IntegrationAsyncReader[Unit] =
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
