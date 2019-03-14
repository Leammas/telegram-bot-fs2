package com.github.leammas.issue.issuetracker

import aecor.journal.postgres.PostgresEventJournal
import akka.actor.ActorSystem
import cats.effect.{ContextShift, ExitCode, IO, IOApp}
import cats.implicits._

object App extends IOApp {

  implicit val shift: ContextShift[IO] =
    cats.effect.internals.IOContextShift.global

  def run(args: List[String]): IO[ExitCode] = IO {
    val system = ActorSystem
    val pgRuntime = PostgresEventJournal()
  } >>
    IO.unit.map(_ => ExitCode.Success)

}
