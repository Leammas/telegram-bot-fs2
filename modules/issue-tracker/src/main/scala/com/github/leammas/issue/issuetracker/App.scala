package com.github.leammas.issue.issuetracker

import aecor.data.EitherK
import aecor.journal.postgres.PostgresEventJournal
import aecor.runtime.akkageneric.GenericAkkaRuntime
import akka.actor.ActorSystem
import cats.effect._
import cats.implicits._
import com.github.leammas.issue.issuetracker.EventSourcedIssue._
import com.github.leammas.postgres.{Postgres, PostgresConfig}
import com.typesafe.config.ConfigFactory

final case class AppConfig(postgres: PostgresConfig)

object App extends IOApp {

  implicit val shift: ContextShift[IO] =
    cats.effect.internals.IOContextShift.global

  def prepare: Resource[IO, Wiring[IO]] =
    for {
      typesafeConfig <- Resource.liftF(IO.delay(ConfigFactory.load))
      config <- Resource.liftF(
        IO.delay(pureconfig.loadConfigOrThrow[AppConfig](typesafeConfig)))
      actorSystem <- Resource.make(IO(ActorSystem()))(x =>
        IO.fromFuture(IO(x.terminate())).void)
      transactor <- Postgres.hikariTransactor[IO](16, config.postgres)
      issueJournal = PostgresEventJournal(transactor,
                                          "issue_events",
                                          EventSourcedIssue.tagging,
                                          IssueEvent.persistentSerializer)
      notifications = DummyNotifications.stream[IO]
      genericAkkaRuntime = GenericAkkaRuntime(actorSystem)
      wiring = new Wiring[IO](
        notifications,
        issueJournal,
        Wiring.toEventSourcedRuntime[EitherK[Issue, IssueRejection, ?[_]],
                                     IO,
                                     Option[IssueState],
                                     IssueEvent,
                                     IssueId](genericAkkaRuntime)
      )
    } yield wiring

  def run(args: List[String]): IO[ExitCode] =
    prepare.use(_.issueCreationProcess.run).as(ExitCode.Success)

}
