package it.pagopa.interop.backendforfrontend

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.testkit.ScalatestRouteTest
import it.pagopa.interop.backendforfrontend.api.impl.AuthorizationApiMarshallerImpl._
import it.pagopa.interop.backendforfrontend.common.system.ApplicationConfiguration
import it.pagopa.interop.backendforfrontend.model.{IdentityToken, Problem, SessionToken}
import it.pagopa.interop.commons.signer.model.SignatureAlgorithm
import org.scalatest.matchers.should.Matchers._
import org.scalatest.wordspec.AnyWordSpecLike

import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}
import SpecHelper._
import com.typesafe.scalalogging.LoggerTakingImplicit
import it.pagopa.interop.commons.logging.ContextFieldsToLog

class AuthorizationApiServiceSpec extends AnyWordSpecLike with SpecHelper with ScalatestRouteTest {

  "Generating a session token" should {

    "succeed" in {

      val uid: String      = UUID.randomUUID().toString
      val orgId: UUID      = UUID.randomUUID()
      val orgIdStr: String = orgId.toString

      val jwtClaimsSet = Map(
        "organization" -> Map(
          "id"         -> orgIdStr,
          "fiscalCode" -> "fiscalCode",
          "roles"      -> List(Map("role" -> "admin"), Map("role" -> "anotherRole"))
        ),
        "uid"          -> uid
      ).asClaimSet

      (mockJwtReader
        .getClaims(_: String))
        .expects(*)
        .once()
        .returns(Success(jwtClaimsSet))

      val desiredClaimSet: Map[String, AnyRef] = Map(
        "uid"            -> uid,
        "user-roles"     -> "admin,anotherRole",
        "organizationId" -> orgId,
        "organization"   -> Map(
          "id"         -> orgIdStr,
          "fiscalCode" -> "fiscalCode",
          "roles"      -> List(Map("role" -> "admin"), Map("role" -> "anotherRole"))
        ).toJSONObject
      )

      (mockRateLimiter
        .rateLimiting(_: UUID)(
          _: ExecutionContext,
          _: LoggerTakingImplicit[ContextFieldsToLog],
          _: Seq[(String, String)]
        ))
        .expects(*, *, *, *)
        .once()
        .returns(Future.unit)

      (mockSessionTokenGenerator
        .generate(_: SignatureAlgorithm, _: Map[String, AnyRef], _: Set[String], _: String, _: Long))
        .expects(
          SignatureAlgorithm.RSAPkcs1Sha256,
          desiredClaimSet,
          ApplicationConfiguration.generatedJwtAudience,
          ApplicationConfiguration.generatedJwtIssuer,
          ApplicationConfiguration.generatedJwtDuration
        )
        .once()
        .returns(Future.successful("sessionToken"))

      Post() ~> service.getSessionToken(IdentityToken(bearerToken))(
        Seq.empty,
        toEntityMarshallerSessionToken
      ) ~> check {
        status shouldEqual StatusCodes.OK
        responseAs[SessionToken] shouldEqual SessionToken("sessionToken")
      }
    }

    "fail on JWTReader failure" in {

      (mockJwtReader
        .getClaims(_: String))
        .expects(*)
        .once()
        .returns(Failure(new RuntimeException("JWT reading fails")))

      Post() ~> service.getSessionToken(IdentityToken(bearerToken))(
        Seq.empty,
        toEntityMarshallerSessionToken
      ) ~> check {
        responseAs[Problem].errors.map(_.code) should contain theSameElementsAs Seq("016-9991")
      }
    }

    "fail on SessionTokenGenerator failure" in {

      val uid: String      = UUID.randomUUID().toString
      val orgId: UUID      = UUID.randomUUID()
      val orgIdStr: String = orgId.toString

      val jwtClaimsSet = Map(
        "organization" -> Map("id" -> orgIdStr, "fiscalCode" -> "fiscalCode", "roles" -> List(Map("role" -> "admin"))),
        "uid"          -> uid
      ).asClaimSet

      (mockJwtReader
        .getClaims(_: String))
        .expects(*)
        .once()
        .returns(Success(jwtClaimsSet))

      val desiredClaimSet: Map[String, AnyRef] = Map(
        "uid"            -> uid,
        "user-roles"     -> "admin",
        "organizationId" -> orgId,
        "organization"   -> Map(
          "id"         -> orgIdStr,
          "fiscalCode" -> "fiscalCode",
          "roles"      -> List(Map("role" -> "admin"))
        ).toJSONObject
      )

      (mockRateLimiter
        .rateLimiting(_: UUID)(
          _: ExecutionContext,
          _: LoggerTakingImplicit[ContextFieldsToLog],
          _: Seq[(String, String)]
        ))
        .expects(*, *, *, *)
        .once()
        .returns(Future.unit)

      (mockSessionTokenGenerator
        .generate(_: SignatureAlgorithm, _: Map[String, AnyRef], _: Set[String], _: String, _: Long))
        .expects(
          SignatureAlgorithm.RSAPkcs1Sha256,
          desiredClaimSet,
          ApplicationConfiguration.generatedJwtAudience,
          ApplicationConfiguration.generatedJwtIssuer,
          ApplicationConfiguration.generatedJwtDuration
        )
        .once()
        .returns(Future.failed(new RuntimeException("Session token generator fails")))

      Post() ~> service.getSessionToken(IdentityToken(bearerToken))(
        Seq.empty,
        toEntityMarshallerSessionToken
      ) ~> check {
        responseAs[Problem].errors.map(_.code) should contain theSameElementsAs Seq("016-9991")
      }
    }

  }

}
