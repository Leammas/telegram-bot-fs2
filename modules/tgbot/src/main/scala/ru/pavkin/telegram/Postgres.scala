package ru.pavkin.telegram

import cats.effect.{ContextShift, Effect}
import doobie.util.transactor.Transactor

import scala.language.higherKinds

object Postgres {

  def transactor[F[_]: ContextShift](config: PostgresConfig)(
      implicit F: Effect[F]): F[Transactor[F]] =
    F.delay(
      Transactor.fromDriverManager[F](
        "org.postgresql.Driver", // fully-qualified driver class name
        s"jdbc:postgresql://${config.contactPoints}:${config.port}/${config.database}", // connect URL
        config.username,
        config.password // password
      ))

}

final case class PostgresConfig(contactPoints: String,
                                port: Int,
                                database: String,
                                username: String,
                                password: String)
