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
          List(BotUpdate(1, BotMessage(1, Chat(chatId), item.some).some))),
        com.github.leammas.state.wiring.ProcessState.init(Map.empty)
      ))

    println(resultState.issue.issues.value.get.unsafeRunSync())

    resultState.issue.issues.value.get.unsafeRunSync().get(IssueId(
      UUID.fromString("561db430-4351-11e9-b475-0800200c9a66"))).size shouldEqual 1
  }



  it should "create & comment & resolve issue" in {
    import cats.syntax.either._
    val issueId = IssueId(UUID.randomUUID())
    val issue = issues(issueId)
    val result = for {
      _ <- issue.create(1, "foo")
      _ <- issue.comment("bar")
      r <- issue.markResolved
    } yield r

    val s = IntegrationState(ru.pavkin.telegram.test.state.ProcessState.init(), com.github.leammas.state.wiring.ProcessState.init(Map.empty))
    result.run(s).unsafeRunSync() shouldEqual ().asRight
    s.issue.issues.value.get.unsafeRunSync()(issueId).length shouldEqual 3
  }

}
