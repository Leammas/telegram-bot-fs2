package ru.pavkin.telegram.todolist

import cats.effect.{ContextShift, ExitCode, IO, IOApp}

object App extends IOApp {

  implicit val shift: ContextShift[IO] =
    cats.effect.internals.IOContextShift.global

  def run(args: List[String]): IO[ExitCode] =
    new TodoListBotProcess[IO].run.compile.drain.map(_ => ExitCode.Success)

}
