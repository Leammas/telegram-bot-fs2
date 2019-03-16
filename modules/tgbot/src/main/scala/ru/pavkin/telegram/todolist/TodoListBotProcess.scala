package ru.pavkin.telegram.todolist

import cats.effect.{ContextShift, Effect}
import cats.implicits._
import com.github.leammas.postgres.{Postgres, PostgresConfig}
import com.typesafe.config.ConfigFactory
import fs2.Stream
import io.chrisdavenport.log4cats.slf4j._
import io.circe.generic.auto._
import org.http4s._
import org.http4s.circe._
import org.http4s.client.blaze.Http1Client
import ru.pavkin.telegram.api.Http4SBotAPI
import ru.pavkin.telegram.api.dto.{BotResponse, BotUpdate}

import scala.language.higherKinds

/**
  * Creates and wires up everything that is needed to launch a [[TodoListBot]] and launches it.
  *
  * @param token telegram bot token
  */
class TodoListBotProcess[F[_]: ContextShift](implicit F: Effect[F]) {

  implicit val decoder: EntityDecoder[F, BotResponse[List[BotUpdate]]] =
    jsonOf[F, BotResponse[List[BotUpdate]]]

  def run: Stream[F, Unit] = Http1Client.stream[F]().flatMap { client =>
    val streamF = for {
      typesafeConfig <- F.delay(ConfigFactory.load)
      config <- F.delay(pureconfig.loadConfigOrThrow[AppConfig](typesafeConfig))
      logger <- Slf4jLogger.create[F]
      transactor <- Postgres.transactor(config.postgres)
      storage <- PostgresTodoListStorage.init(transactor, "items")
      botAPI <- F.delay(new Http4SBotAPI(config.telegram.token, client, logger))
      adminNotifier <- F.delay(new LazyAdminNotifier[F])
      phraseChecker <- F.delay(new NaivePhraseChecker[F])
      todoListBot <- F.delay(
        new TodoListBot(botAPI, storage, phraseChecker, adminNotifier, logger))
    } yield todoListBot.launch

    Stream.force(streamF)
  }
}

final case class TelegramConfig(token: String)

final case class AppConfig(telegram: TelegramConfig, postgres: PostgresConfig)
