package ru.pavkin.telegram.test
package todolist

import cats.effect.SyncIO
import cats.effect.concurrent.Ref
import org.scalatest.{FlatSpec, Matchers}
import ru.pavkin.telegram.api.dto.{BotMessage, BotUpdate, Chat}
import ru.pavkin.telegram.test.state.{ProcessState, StateBotApi}
import wiring._
import cats.syntax.option._
import ru.pavkin.telegram.todolist.PostgresTodoListStorage.Record

class TodoListBotSpec extends FlatSpec with Matchers {

  /*private def run(state: ProcessState) = bot
    .flatMap(_.launch.compile.drain)
    .runS(state)
    .unsafeRunSync()*/

  private def run(state: ProcessState) = Ref.of[SyncIO, ProcessState](state).flatMap { refState =>
    bot
      .flatMap(_.launch.compile.drain)
      .runS(refState).handleErrorWith(_ => SyncIO.pure(refState)).flatMap(_.get)
  }.unsafeRunSync()

  it should "do nothing when idle" in {
    val resultState = run(ProcessState.empty)

    resultState shouldEqual ProcessState.empty
  }

  it should "call notify if phrase is suspicious" in {
    val item = "kill steal... you know the remainder"
    val chatId = 100

    val resultState = run(ProcessState.empty.copy(chatMessages =
      StateBotApi.InnerState(List(BotUpdate(1, BotMessage(1, Chat(chatId), item.some).some)), List.empty)))

    resultState.notifications.value.head shouldEqual chatId
    resultState.records.value.head shouldEqual Record(chatId, item)
  }

  it should "keep state on error" in {
    val item = "goroutine is a lightweight thread of execution"
    val chatId = 404

    val resultState = run(ProcessState.empty.copy(chatMessages =
      StateBotApi.InnerState(List(BotUpdate(1, BotMessage(1, Chat(chatId), item.some).some)), List.empty)))

    resultState.records.value.head shouldEqual Record(chatId, item)
  }

}
