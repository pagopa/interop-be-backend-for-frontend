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
import it.pagopa.interop.commons.ratelimiter.model.RateLimitStatus

import cats.syntax.all._
import it.pagopa.interop.tenantprocess
import scala.concurrent.duration.DurationInt
import it.pagopa.interop.tenantmanagement.client.model.Tenant
import it.pagopa.interop.commons.utils.service.OffsetDateTimeSupplier
import it.pagopa.interop.tenantmanagement.client.model.ExternalId
import it.pagopa.interop.tenantmanagement.client.invoker.ApiError
import it.pagopa.interop.selfcare.partyprocess.client.model.Institution

class AuthorizationApiServiceSpec extends AnyWordSpecLike with SpecHelper with ScalatestRouteTest {

  "Generating a session token" should {

    "succeed when the tenant is present" in {

      val uid: String      = UUID.randomUUID().toString
      val selfcareId: UUID = UUID.randomUUID()
      val tenantId: UUID   = UUID.randomUUID()

      val jwtClaimsSet = Map(
        "organization" -> Map(
          "id"         -> selfcareId.toString(),
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

      (mockTenantManagement
        .getBySelfcareId(_: UUID)(_: Seq[(String, String)]))
        .expects(selfcareId, *)
        .once()
        .returns(
          Future.successful(
            Tenant(
              id = tenantId,
              selfcareId = selfcareId.toString.some,
              externalId = ExternalId("origin", "externalId"),
              features = Nil,
              attributes = Nil,
              createdAt = OffsetDateTimeSupplier.get(),
              updatedAt = None
            )
          )
        )

      val desiredClaimSet: Map[String, AnyRef] = Map(
        "uid"            -> uid,
        "user-roles"     -> "admin,anotherRole",
        "organizationId" -> tenantId.toString(),
        "selfcareId"     -> selfcareId.toString(),
        "organization"   -> Map(
          "id"         -> selfcareId.toString(),
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
        .returns(Future.successful(RateLimitStatus(10, 10, 1.second)))

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

    "succeed even if the tenant needs to be upserted" in {

      val uid: String      = UUID.randomUUID().toString
      val selfcareId: UUID = UUID.randomUUID()
      val tenantId: UUID   = UUID.randomUUID()

      val jwtClaimsSet = Map(
        "organization" -> Map(
          "id"         -> selfcareId.toString(),
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

      (mockTenantManagement
        .getBySelfcareId(_: UUID)(_: Seq[(String, String)]))
        .expects(selfcareId, *)
        .once()
        .returns(Future.failed(ApiError[Problem](404, "oh no!", None, new Exception, Map.empty)))

      (mockPartyProcess
        .getInstitution(_: UUID)(_: Seq[(String, String)], _: ExecutionContext))
        .expects(selfcareId, *, *)
        .once()
        .returns(
          Future.successful(
            Institution(
              id = selfcareId,
              externalId = "whatever",
              originId = "IPACode",
              description = "foo",
              digitalAddress = "of a digital home?",
              address = "of an actual home?",
              zipCode = "winzip",
              taxCode = "not mine please",
              origin = "IPA",
              institutionType = None,
              attributes = Nil
            )
          )
        )

      (mockTenantProcess
        .selfcareUpsertTenant(_: String, _: String)(_: UUID)(_: Seq[(String, String)]))
        .expects("IPA", "IPACode", selfcareId, *)
        .once()
        .returns(
          Future.successful(
            tenantprocess.client.model.Tenant(
              id = tenantId,
              selfcareId = selfcareId.toString.some,
              externalId = tenantprocess.client.model.ExternalId("IPA", "IPACode"),
              features = Nil,
              attributes = Nil,
              createdAt = OffsetDateTimeSupplier.get(),
              updatedAt = None
            )
          )
        )

      val desiredClaimSet: Map[String, AnyRef] = Map(
        "uid"            -> uid,
        "user-roles"     -> "admin,anotherRole",
        "organizationId" -> tenantId.toString(),
        "selfcareId"     -> selfcareId.toString(),
        "organization"   -> Map(
          "id"         -> selfcareId.toString(),
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
        .returns(Future.successful(RateLimitStatus(10, 10, 1.second)))

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
      val selfcareId: UUID = UUID.randomUUID()
      val tenantId: UUID   = UUID.randomUUID()

      val jwtClaimsSet = Map(
        "organization" -> Map(
          "id"         -> selfcareId.toString(),
          "fiscalCode" -> "fiscalCode",
          "roles"      -> List(Map("role" -> "admin"))
        ),
        "uid"          -> uid
      ).asClaimSet

      (mockJwtReader
        .getClaims(_: String))
        .expects(*)
        .once()
        .returns(Success(jwtClaimsSet))

      (mockTenantManagement
        .getBySelfcareId(_: UUID)(_: Seq[(String, String)]))
        .expects(selfcareId, *)
        .once()
        .returns(
          Future.successful(
            Tenant(
              id = tenantId,
              selfcareId = selfcareId.toString.some,
              externalId = ExternalId("origin", "externalId"),
              features = Nil,
              attributes = Nil,
              createdAt = OffsetDateTimeSupplier.get(),
              updatedAt = None
            )
          )
        )

      val desiredClaimSet: Map[String, AnyRef] = Map(
        "uid"            -> uid,
        "user-roles"     -> "admin",
        "organizationId" -> tenantId.toString(),
        "selfcareId"     -> selfcareId.toString(),
        "organization"   -> Map(
          "id"         -> selfcareId.toString(),
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
        .returns(Future.successful(RateLimitStatus(10, 10, 1.second)))

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
