package ru.pavkin.telegram.test
package todolist

import cats.syntax.option._
import org.scalatest.{FlatSpec, Matchers}
import ru.pavkin.telegram.api.dto.{BotMessage, BotUpdate, Chat}
import ru.pavkin.telegram.test.state.ProcessState
import ru.pavkin.telegram.test.wiring._
import ru.pavkin.telegram.todolist.PostgresTodoListStorage.Record
import com.github.leammas.testkit.syntax._

class TodoListBotSpec extends FlatSpec with Matchers {

  it should "do nothing when idle" in {
    val resultState = runTestApp(ProcessState.init())

    resultState.isEmpty should be(true)
  }

  it should "call notify if phrase is suspicious" in {
    val item = "kill steal... you know the remainder"
    val chatId = 100

    val resultState = runTestApp(
      ProcessState.init(incomingMessages =
        List(BotUpdate(1, BotMessage(1, Chat(chatId), item.some).some))))

    resultState.notifications.q.dequeueCurrent
      .unsafeRunSync()
      .head shouldEqual chatId
    resultState.records.value.get.unsafeRunSync().head shouldEqual Record(
      chatId,
      item)
  }

  it should "keep state on error" in {
    val item = "goroutine is a lightweight thread of execution"
    val chatId = 404

    val resultState = runTestApp(
      ProcessState.init(incomingMessages =
        List(BotUpdate(1, BotMessage(1, Chat(chatId), item.some).some))))

    resultState.records.value.get.unsafeRunSync().head shouldEqual Record(
      chatId,
      item)
  }

}
