package com.github.leammas.issue

import cats.effect.{ContextShift, ExitCode, IO, IOApp}

object App extends IOApp {

  implicit val shift: ContextShift[IO] =
    cats.effect.internals.IOContextShift.global

  def run(args: List[String]): IO[ExitCode] =
    IO.unit.map(_ => ExitCode.Success)

}
