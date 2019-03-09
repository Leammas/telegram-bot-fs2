package ru.pavkin.telegram.test.todolist

import com.typesafe.config.ConfigFactory
import org.scalatest.{FlatSpec, Matchers}
import ru.pavkin.telegram.todolist.AppConfig

class ConfigParserSpec extends FlatSpec with Matchers {

  it should "parse config" in {
    val config = pureconfig.loadConfigOrThrow[AppConfig](ConfigFactory.load)

    config.postgres.contactPoints shouldEqual "127.0.0.1"
  }

}
