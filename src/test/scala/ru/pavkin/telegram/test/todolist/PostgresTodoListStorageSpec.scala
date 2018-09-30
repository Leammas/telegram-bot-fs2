package ru.pavkin.telegram.test.todolist

import org.scalacheck.{Arbitrary, Gen, Prop}
import org.scalatest.FlatSpec
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.prop.{Checkers, PropertyChecks}
import org.scalatest.time.{Millis, Seconds, Span}
import ru.pavkin.telegram.test.arbitrary.MagnoliaArbitrary._
import ru.pavkin.telegram.test.db.PostgresTest
import ru.pavkin.telegram.todolist.PostgresTodoListStorage
import ru.pavkin.telegram.todolist.PostgresTodoListStorage.Record

import scala.concurrent.ExecutionContext.Implicits.global

class PostgresTodoListStorageSpec
    extends FlatSpec
    with PostgresTest
    with Checkers
    with PropertyChecks
    with ScalaFutures {

  override implicit val patienceConfig: PatienceConfig =
    PatienceConfig(timeout = Span(10, Seconds), interval = Span(150, Millis))

  val storageF = transactorF.flatMap(t =>
    PostgresTodoListStorage.init(t, "foo").unsafeToFuture())

  val validRecord: Gen[Record] =
    implicitly[Arbitrary[Record]].arbitrary
      .filter(_.item.nonEmpty)

  "RecordStorage" should "save and retrieve items" in {
    check {
      Prop.forAllNoShrink(validRecord) { v: Record =>
        val result = for {
          storage <- storageF
          _ <- storage.addItem(v.chatId, v.item).unsafeToFuture()
          r <- storage.getItems(v.chatId).unsafeToFuture()
        } yield r.contains(v.item)

        whenReady(result)(identity): Boolean
      }
    }
  }
}
