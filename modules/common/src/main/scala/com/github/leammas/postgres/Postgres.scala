package com.github.leammas.postgres

import cats.effect._
import doobie.hikari.HikariTransactor
import doobie.util.ExecutionContexts
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

  def hikariTransactor[F[_]: ContextShift: Async](numFixedThreads: Int, config: PostgresConfig): Resource[F, Transactor[F]] = for {
    ce <- ExecutionContexts.fixedThreadPool[F](32) // our connect EC
    te <- ExecutionContexts.cachedThreadPool[F]    // our transaction EC
    xa <- HikariTransactor.newHikariTransactor[F](
      "org.postgresql.Driver", // fully-qualified driver class name
      s"jdbc:postgresql://${config.contactPoints}:${config.port}/${config.database}", // connect URL
      config.username,
      config.password, // password
      ce,                                     // await connection here
      te                                      // execute JDBC operations here
    )
  } yield xa

}

final case class PostgresConfig(contactPoints: String,
                                port: Int,
                                database: String,
                                username: String,
                                password: String)
