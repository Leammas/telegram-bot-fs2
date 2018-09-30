package ru.pavkin.telegram.todolist

import cats.effect.concurrent.Ref
import cats.{Functor, Monad}
import cats.implicits._
import doobie._
import doobie.implicits._
import ru.pavkin.telegram.api.ChatId

import scala.language.higherKinds

/**
  * Algebra for managing storage of todo-list items
  */
trait TodoListStorage[F[_]] {
  def addItem(chatId: ChatId, item: Item): F[Unit]
  def getItems(chatId: ChatId): F[List[Item]]
  def clearList(chatId: ChatId): F[Unit]
}

/**
  * Simple in-memory implementation of [[TodoListStorage]] algebra, using [[cats.effect.concurrent.Ref]].
  * In real world this would go to some database of sort.
  */
class InMemoryTodoListStorage[F[_]: Functor](
    private val ref: Ref[F, Map[ChatId, List[Item]]])
    extends TodoListStorage[F] {

  def addItem(chatId: ChatId, item: Item): F[Unit] =
    ref.update(m => m.updated(chatId, item :: m.getOrElse(chatId, Nil))).void

  def getItems(chatId: ChatId): F[List[Item]] =
    ref.get.map(_.getOrElse(chatId, Nil))

  def clearList(chatId: ChatId): F[Unit] =
    ref.update(_ - chatId).void
}

final class PostgresTodoListStorage[F[_]: Monad](transactor: Transactor[F],
                                                 tableName: String)
    extends TodoListStorage[F] {

  def addItem(chatId: ChatId, item: Item): F[Unit] =
    (fr"INSERT INTO " ++ Fragment.const(tableName) ++ fr"(chat_id, item) VALUES($chatId, $item)").update.run
      .transact(transactor)
      .void

  def getItems(chatId: ChatId): F[List[Item]] =
    (fr"SELECT item FROM" ++ Fragment.const(tableName) ++ fr"WHERE chat_id = $chatId")
      .query[Item]
      .to[List]
      .transact(transactor)

  def clearList(chatId: ChatId): F[Unit] =
    (fr"DELETE FROM" ++ Fragment.const(tableName) ++ fr"WHERE chat_id = $chatId").update.run
      .transact(transactor)
      .void
}

object PostgresTodoListStorage {
  final case class Record(chatId: ChatId, item: Item)

  def init[F[_]: Monad](transactor: Transactor[F],
                        tableName: String): F[TodoListStorage[F]] = {
    // usual INT will not pass property-based check
    val query = (fr"""
      CREATE TABLE IF NOT EXISTS """ ++ Fragment.const(tableName) ++ fr""" (
        chat_id BIGINT  NOT NULL,
        item    TEXT NOT NULL
      )
     """).update.run
    query
      .transact(transactor)
      .map(_ => new PostgresTodoListStorage(transactor, tableName))
  }
}
