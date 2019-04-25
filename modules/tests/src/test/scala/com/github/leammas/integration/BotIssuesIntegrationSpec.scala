package com.github.leammas.integration

import java.util.UUID

import cats.syntax.option._
import com.github.leammas.integration.state._
import com.github.leammas.issue.issuetracker.IssueId
import org.scalatest.{FlatSpec, Matchers}
import ru.pavkin.telegram.api.dto.{BotMessage, BotUpdate, Chat}

class BotIssuesIntegrationSpec extends FlatSpec with Matchers {

  it should "create issue on suspicious message" in {
    val item = "kill steal... you know the remainder"
    val chatId = 100

    val resultState = runTestApp(
      IntegrationState(
        ru.pavkin.telegram.test.state.ProcessState.init(incomingMessages =
          List(BotUpdate(1, BotMessage(1, Chat(chatId), item.some).some), BotUpdate(2, BotMessage(2, Chat(chatId), item.some).some))),
        com.github.leammas.state.wiring.ProcessState.init(Map.empty)
      ))

    resultState.issue.issues.store.get.unsafeRunSync().get(IssueId(
      UUID.fromString("561db430-4351-11e9-b475-0800200c9a66"))).size shouldEqual 1
  }

}
