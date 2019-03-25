package ru.pavkin.telegram.test.db

import cats.effect.{ContextShift, IO, Timer}
import com.dimafeng.testcontainers.{ForAllTestContainer, GenericContainer}
import doobie.util.transactor.Transactor
import org.scalatest.Suite
import org.testcontainers.containers.wait.LogMessageWaitStrategy
import cats.implicits._
import com.github.leammas.postgres.{Postgres, PostgresConfig}

import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.concurrent.duration._

trait PostgresTest extends ForAllTestContainer {
  self: Suite =>

  implicit val shift: ContextShift[IO] =
    cats.effect.internals.IOContextShift.global

  implicit val timer: Timer[IO] = IO.timer(ExecutionContext.global)

  val transactorP: Promise[Transactor[IO]] =
    Promise[Transactor[IO]]

  override val container: GenericContainer = new GenericContainer(
    "postgres:10",
    Seq(5432),
    Map("POSTGRES_DB" -> "test",
        "POSTGRES_USER" -> "test",
        "POSTGRES_PASSWORD" -> "test"),
    waitStrategy = Some(
      (new LogMessageWaitStrategy)
        .withRegEx(".*database system is ready to accept connections.*\\s"))
  )

  override def afterStart(): Unit =
    createTransactor
      .runAsync {
        case Left(e) =>
          transactorP.failure(e)
          IO.unit
        case Right(t) =>
          transactorP.success(t)
          IO.unit
      }
      .unsafeRunSync()

  val transactorF: Future[Transactor[IO]] =
    transactorP.future

  private def waitForConnection(tr: Transactor[IO]): IO[Unit] =
    tr.connect(tr.kernel).void.handleErrorWith { t: Throwable =>
      IO.sleep(1.second).flatMap(_ => tr.connect(tr.kernel).void)
    }

  private def createTransactor =
    for {
      transactor <- Postgres.transactor[IO](
        PostgresConfig("localhost",
                       container.container
                         .getMappedPort(5432),
                       "test",
                       "test",
                       "test"))
      _ <- waitForConnection(transactor)
    } yield transactor

}
