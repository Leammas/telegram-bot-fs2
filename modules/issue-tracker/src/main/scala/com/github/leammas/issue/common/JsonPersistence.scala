package com.github.leammas.issue.common

import java.nio.ByteBuffer

import aecor.journal.postgres.PostgresEventJournal.Serializer
import aecor.journal.postgres.PostgresEventJournal.Serializer.TypeHint
import aecor.runtime.akkapersistence.serialization.DecodingFailure
import cats.syntax.either._
import cats.syntax.option._
import io.circe.{Decoder, Encoder, Printer}

object JsonPersistence {

  def jsonSerializer[T: Encoder: Decoder]: Serializer[T] = new Serializer[T] {
    def serialize(a: T): (TypeHint, Array[Byte]) =
      ("", Encoder[T].apply(a).pretty(Printer.noSpaces).getBytes())

    def deserialize(typeHint: TypeHint,
                    bytes: Array[Byte]): Either[Throwable, T] =
      io.circe.jawn
        .decodeByteBuffer(ByteBuffer.wrap(bytes))
        .leftMap(error => DecodingFailure(error.getMessage, error.some))
  }

}
