package com.github.leammas.state

import java.util.UUID

import cats.data.Chain
import com.github.leammas.issue.issuetracker.IssueId
import org.scalatest.{FlatSpec, Matchers}
import wiring._
import cats.syntax.either._
import com.github.leammas.issue.issuetracker.IssueEvent.{
  IssueCommentAdded,
  IssueCreated,
  IssueResolved
}
import com.github.leammas.testkit.syntax._

class EventsourcedIssueSpec extends FlatSpec with Matchers {

  val issueId = IssueId(UUID.randomUUID())

  it should "create & comment & resolve issue" in {
    val issue = issues(issueId)
    val result = for {
      _ <- issue.create(1, "foo")
      _ <- issue.comment("bar")
      r <- issue.markResolved
    } yield r

    val s = ProcessState.init()
    result.run(s).unsafeRunSync() shouldEqual ().asRight
    s.issues.store.get.unsafeRunSync()(issueId) shouldEqual Chain(
      IssueCreated(1, "foo"),
      IssueCommentAdded("bar"),
      IssueResolved)
    // check if events have been published
    s.issues.queue.dequeueCurrent.unsafeRunSync().length shouldEqual 3
  }
}
