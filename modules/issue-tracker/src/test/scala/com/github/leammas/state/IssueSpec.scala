package com.github.leammas.state

import com.github.leammas.issue.issuetracker.{IssueRejection, IssueState}
import org.scalatest.{FlatSpec, Matchers}
import org.scalatest.prop.{Checkers, PropertyChecks}
import com.github.leammas.testkit.MagnoliaArbitrary._
import org.scalacheck.{Arbitrary, Gen, Prop}
import boopickle.Default.{ Pickle, Unpickle }
import com.github.leammas.issue.issuetracker.Issue._
import boopickle.Default._

class IssueSpec extends FlatSpec with Matchers with Checkers with PropertyChecks {

  val issueRejection: Gen[IssueRejection] =
    implicitly[Arbitrary[IssueRejection]].arbitrary

  val issueState: Gen[IssueState] =
    implicitly[Arbitrary[IssueState]].arbitrary

  it should "encode issue state with boopickle back and forth" in {
    check(Prop.forAllNoShrink(issueState) { v: IssueState =>
      val bb = Pickle.intoBytes(v)
      Unpickle[IssueState].fromBytes(bb) == v
    })
  }

  it should "encode issue command rejection with boopickle back and forth" in {
    check(Prop.forAllNoShrink(issueRejection) { v: IssueRejection =>
      val bb = Pickle.intoBytes(v)
      Unpickle[IssueRejection].fromBytes(bb) == v
    })
  }

}
