package it.pagopa.interop.backendforfrontend

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.testkit.ScalatestRouteTest
import cats.syntax.all._
import com.typesafe.scalalogging.LoggerTakingImplicit
import it.pagopa.interop.backendforfrontend.SpecHelper._
import it.pagopa.interop.backendforfrontend.common.system.ApplicationConfiguration
import it.pagopa.interop.backendforfrontend.model.{IdentityToken, Problem, SessionToken}
import it.pagopa.interop.backendforfrontend.error.BFFErrors.SelfcareNotFound
import it.pagopa.interop.commons.logging.ContextFieldsToLog
import it.pagopa.interop.commons.ratelimiter.model.RateLimitStatus
import it.pagopa.interop.commons.signer.model.SignatureAlgorithm
import it.pagopa.interop.commons.utils.service.OffsetDateTimeSupplier
import it.pagopa.interop.selfcare.partyprocess.client.model.Institution
import it.pagopa.interop.tenantprocess.client.{model => TenantProcessModel}
import org.scalatest.matchers.should.Matchers._
import org.scalatest.wordspec.AnyWordSpecLike

import java.util.UUID
import scala.concurrent.duration.DurationInt
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}
import scala.jdk.CollectionConverters._
import com.nimbusds.jwt.JWTClaimsSet

class AuthorizationApiServiceSpec extends AnyWordSpecLike with SpecHelper with ScalatestRouteTest {

  "Generating a session token" should {

    "succeed when the tenant is present and its origin is IPA" in {

      val uid: String        = UUID.randomUUID().toString
      val selfcareId: String = UUID.randomUUID().toString
      val tenantId: UUID     = UUID.randomUUID()

      val jwtClaimsSet: JWTClaimsSet = Map[String, Object](
        "organization" -> Map(
          "id"         -> selfcareId,
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

      (mockInteropTokenGenerator
        .generateInternalToken(_: String, _: List[String], _: String, _: Long))
        .expects(*, *, *, *)
        .once()
        .returns(Future.successful(internalToken))

      (mockTenantProcess
        .getBySelfcareId(_: UUID)(_: Seq[(String, String)]))
        .expects(UUID.fromString(selfcareId), *)
        .once()
        .returns(
          Future.successful(
            TenantProcessModel.Tenant(
              id = tenantId,
              selfcareId = selfcareId.some,
              externalId = TenantProcessModel.ExternalId("IPA", "externalId"),
              features = Nil,
              attributes = Nil,
              createdAt = OffsetDateTimeSupplier.get(),
              updatedAt = None,
              mails = Nil,
              name = "name"
            )
          )
        )

      val desiredClaimSet: Map[String, AnyRef] = Map(
        "uid"            -> uid,
        "user-roles"     -> "admin,anotherRole",
        "organizationId" -> tenantId.toString,
        "selfcareId"     -> selfcareId,
        "externalId"     -> Map("origin" -> "IPA", "value" -> "externalId").asJava,
        "organization"   -> Map(
          "id"         -> selfcareId,
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

      Post() ~> authorizationService.getSessionToken(IdentityToken(bearerToken))(
        Seq.empty,
        toEntityMarshallerSessionToken
      ) ~> check {
        status shouldEqual StatusCodes.OK
        responseAs[SessionToken] shouldEqual SessionToken("sessionToken")
      }
    }

    "succeed when the tenant is present and its origin is ANAC" in {

      val uid: String        = UUID.randomUUID().toString
      val selfcareId: String = UUID.randomUUID().toString
      val tenantId: UUID     = UUID.randomUUID()

      val jwtClaimsSet: JWTClaimsSet = Map[String, Object](
        "organization" -> Map(
          "id"         -> selfcareId,
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

      (mockInteropTokenGenerator
        .generateInternalToken(_: String, _: List[String], _: String, _: Long))
        .expects(*, *, *, *)
        .once()
        .returns(Future.successful(internalToken))

      (mockTenantProcess
        .getBySelfcareId(_: UUID)(_: Seq[(String, String)]))
        .expects(UUID.fromString(selfcareId), *)
        .once()
        .returns(
          Future.successful(
            TenantProcessModel.Tenant(
              id = tenantId,
              selfcareId = selfcareId.some,
              externalId = TenantProcessModel.ExternalId("ANAC", "externalId"),
              features = Nil,
              attributes = Nil,
              createdAt = OffsetDateTimeSupplier.get(),
              updatedAt = None,
              mails = Nil,
              name = "name"
            )
          )
        )

      val desiredClaimSet: Map[String, AnyRef] = Map(
        "uid"            -> uid,
        "user-roles"     -> "admin,anotherRole",
        "organizationId" -> tenantId.toString,
        "selfcareId"     -> selfcareId,
        "externalId"     -> Map("origin" -> "ANAC", "value" -> "externalId").asJava,
        "organization"   -> Map(
          "id"         -> selfcareId,
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

      Post() ~> authorizationService.getSessionToken(IdentityToken(bearerToken))(
        Seq.empty,
        toEntityMarshallerSessionToken
      ) ~> check {
        status shouldEqual StatusCodes.OK
        responseAs[SessionToken] shouldEqual SessionToken("sessionToken")
      }
    }

    "succeed when the tenant is present and its origin is not IPA or ANAC but is in allowlist" in {

      val uid: String        = UUID.randomUUID().toString
      val selfcareId: String = allowList.head
      val tenantId: UUID     = UUID.randomUUID()

      val jwtClaimsSet: JWTClaimsSet = Map[String, Object](
        "organization" -> Map(
          "id"         -> selfcareId,
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

      (mockInteropTokenGenerator
        .generateInternalToken(_: String, _: List[String], _: String, _: Long))
        .expects(*, *, *, *)
        .once()
        .returns(Future.successful(internalToken))

      (mockTenantProcess
        .getBySelfcareId(_: UUID)(_: Seq[(String, String)]))
        .expects(UUID.fromString(selfcareId), *)
        .once()
        .returns(
          Future.successful(
            TenantProcessModel.Tenant(
              id = tenantId,
              selfcareId = selfcareId.some,
              externalId = TenantProcessModel.ExternalId("other-origin", "externalId"),
              features = Nil,
              attributes = Nil,
              createdAt = OffsetDateTimeSupplier.get(),
              updatedAt = None,
              mails = Nil,
              name = "name"
            )
          )
        )

      val desiredClaimSet: Map[String, AnyRef] = Map(
        "uid"            -> uid,
        "user-roles"     -> "admin,anotherRole",
        "organizationId" -> tenantId.toString,
        "selfcareId"     -> selfcareId,
        "externalId"     -> Map("origin" -> "other-origin", "value" -> "externalId").asJava,
        "organization"   -> Map(
          "id"         -> selfcareId,
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

      Post() ~> authorizationService.getSessionToken(IdentityToken(bearerToken))(
        Seq.empty,
        toEntityMarshallerSessionToken
      ) ~> check {
        status shouldEqual StatusCodes.OK
        responseAs[SessionToken] shouldEqual SessionToken("sessionToken")
      }
    }

    "succeed when the tenant is present and its origin is not IPA or ANAC and is not in allowlist" in {

      val uid: String        = UUID.randomUUID().toString
      val selfcareId: String = UUID.randomUUID().toString
      val tenantId: UUID     = UUID.randomUUID()

      val jwtClaimsSet: JWTClaimsSet = Map[String, Object](
        "organization" -> Map(
          "id"         -> selfcareId,
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

      (mockInteropTokenGenerator
        .generateInternalToken(_: String, _: List[String], _: String, _: Long))
        .expects(*, *, *, *)
        .once()
        .returns(Future.successful(internalToken))

      (mockTenantProcess
        .getBySelfcareId(_: UUID)(_: Seq[(String, String)]))
        .expects(UUID.fromString(selfcareId), *)
        .once()
        .returns(
          Future.successful(
            TenantProcessModel.Tenant(
              id = tenantId,
              selfcareId = selfcareId.some,
              externalId = TenantProcessModel.ExternalId("other-origin", "externalId"),
              features = Nil,
              attributes = Nil,
              createdAt = OffsetDateTimeSupplier.get(),
              updatedAt = None,
              mails = Nil,
              name = "name"
            )
          )
        )

      Post() ~> authorizationService.getSessionToken(IdentityToken(bearerToken))(
        Seq.empty,
        toEntityMarshallerSessionToken
      ) ~> check {
        status shouldEqual StatusCodes.BadRequest
        responseAs[Problem].errors.map(_.code) should contain theSameElementsAs Seq("016-0011")
      }
    }

    "succeed if the tenant needs to be upserted and its origin is IPA" in {

      val uid: String        = UUID.randomUUID().toString
      val selfcareId: String = UUID.randomUUID().toString
      val tenantId: UUID     = UUID.randomUUID()

      val jwtClaimsSet: JWTClaimsSet = Map[String, Object](
        "organization" -> Map(
          "id"         -> selfcareId,
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

      (mockInteropTokenGenerator
        .generateInternalToken(_: String, _: List[String], _: String, _: Long))
        .expects(*, *, *, *)
        .once()
        .returns(Future.successful(internalToken))

      (mockTenantProcess
        .getBySelfcareId(_: UUID)(_: Seq[(String, String)]))
        .expects(UUID.fromString(selfcareId), *)
        .once()
        .returns(Future.failed(SelfcareNotFound(UUID.fromString(selfcareId))))

      (mockPartyProcess
        .getInstitution(_: String)(_: Seq[(String, String)], _: ExecutionContext))
        .expects(selfcareId, *, *)
        .once()
        .returns(
          Future.successful(
            Institution(
              id = UUID.fromString(selfcareId),
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
        .selfcareUpsertTenant(_: String, _: String, _: String)(_: String)(_: Seq[(String, String)]))
        .expects("IPA", "IPACode", "foo", selfcareId, *)
        .once()
        .returns(
          Future.successful(
            TenantProcessModel.Tenant(
              id = tenantId,
              selfcareId = selfcareId.some,
              externalId = TenantProcessModel.ExternalId("IPA", "IPACode"),
              features = Nil,
              attributes = Nil,
              createdAt = OffsetDateTimeSupplier.get(),
              updatedAt = None,
              mails = Nil,
              name = "foo"
            )
          )
        )

      val desiredClaimSet: Map[String, AnyRef] = Map(
        "uid"            -> uid,
        "user-roles"     -> "admin,anotherRole",
        "organizationId" -> tenantId.toString,
        "selfcareId"     -> selfcareId,
        "externalId"     -> Map("origin" -> "IPA", "value" -> "IPACode").asJava,
        "organization"   -> Map(
          "id"         -> selfcareId,
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

      Post() ~> authorizationService.getSessionToken(IdentityToken(bearerToken))(
        Seq.empty,
        toEntityMarshallerSessionToken
      ) ~> check {
        status shouldEqual StatusCodes.OK
        responseAs[SessionToken] shouldEqual SessionToken("sessionToken")
      }
    }

    "succeed if the tenant needs to be upserted and its origin is ANAC" in {

      val uid: String        = UUID.randomUUID().toString
      val selfcareId: String = UUID.randomUUID().toString
      val tenantId: UUID     = UUID.randomUUID()

      val jwtClaimsSet: JWTClaimsSet = Map[String, Object](
        "organization" -> Map(
          "id"         -> selfcareId,
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

      (mockInteropTokenGenerator
        .generateInternalToken(_: String, _: List[String], _: String, _: Long))
        .expects(*, *, *, *)
        .once()
        .returns(Future.successful(internalToken))

      (mockTenantProcess
        .getBySelfcareId(_: UUID)(_: Seq[(String, String)]))
        .expects(UUID.fromString(selfcareId), *)
        .once()
        .returns(Future.failed(SelfcareNotFound(UUID.fromString(selfcareId))))

      (mockPartyProcess
        .getInstitution(_: String)(_: Seq[(String, String)], _: ExecutionContext))
        .expects(selfcareId, *, *)
        .once()
        .returns(
          Future.successful(
            Institution(
              id = UUID.fromString(selfcareId),
              externalId = "whatever",
              originId = "ANACCode",
              description = "foo",
              digitalAddress = "of a digital home?",
              address = "of an actual home?",
              zipCode = "winzip",
              taxCode = "not mine please",
              origin = "ANAC",
              institutionType = None,
              attributes = Nil
            )
          )
        )

      (mockTenantProcess
        .selfcareUpsertTenant(_: String, _: String, _: String)(_: String)(_: Seq[(String, String)]))
        .expects("ANAC", "ANACCode", "foo", selfcareId, *)
        .once()
        .returns(
          Future.successful(
            TenantProcessModel.Tenant(
              id = tenantId,
              selfcareId = selfcareId.some,
              externalId = TenantProcessModel.ExternalId("ANAC", "ANACCode"),
              features = Nil,
              attributes = Nil,
              createdAt = OffsetDateTimeSupplier.get(),
              updatedAt = None,
              mails = Nil,
              name = "foo"
            )
          )
        )

      val desiredClaimSet: Map[String, AnyRef] = Map(
        "uid"            -> uid,
        "user-roles"     -> "admin,anotherRole",
        "organizationId" -> tenantId.toString,
        "selfcareId"     -> selfcareId,
        "externalId"     -> Map("origin" -> "ANAC", "value" -> "ANACCode").asJava,
        "organization"   -> Map(
          "id"         -> selfcareId,
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

      Post() ~> authorizationService.getSessionToken(IdentityToken(bearerToken))(
        Seq.empty,
        toEntityMarshallerSessionToken
      ) ~> check {
        status shouldEqual StatusCodes.OK
        responseAs[SessionToken] shouldEqual SessionToken("sessionToken")
      }
    }

    "succeed if the tenant needs to be upserted and its origin is not IPA or ANAC but is in allowlist" in {

      val uid: String        = UUID.randomUUID().toString
      val selfcareId: String = allowList.head
      val tenantId: UUID     = UUID.randomUUID()

      val jwtClaimsSet: JWTClaimsSet = Map[String, Object](
        "organization" -> Map(
          "id"         -> selfcareId,
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

      (mockInteropTokenGenerator
        .generateInternalToken(_: String, _: List[String], _: String, _: Long))
        .expects(*, *, *, *)
        .once()
        .returns(Future.successful(internalToken))

      (mockTenantProcess
        .getBySelfcareId(_: UUID)(_: Seq[(String, String)]))
        .expects(UUID.fromString(selfcareId), *)
        .once()
        .returns(Future.failed(SelfcareNotFound(UUID.fromString(selfcareId))))

      (mockPartyProcess
        .getInstitution(_: String)(_: Seq[(String, String)], _: ExecutionContext))
        .expects(selfcareId, *, *)
        .once()
        .returns(
          Future.successful(
            Institution(
              id = UUID.fromString(selfcareId),
              externalId = "whatever",
              originId = "non-IPACode",
              description = "foo",
              digitalAddress = "of a digital home?",
              address = "of an actual home?",
              zipCode = "winzip",
              taxCode = "not mine please",
              origin = "non-IPA",
              institutionType = None,
              attributes = Nil
            )
          )
        )

      (mockTenantProcess
        .selfcareUpsertTenant(_: String, _: String, _: String)(_: String)(_: Seq[(String, String)]))
        .expects("non-IPA", "non-IPACode", "foo", selfcareId, *)
        .once()
        .returns(
          Future.successful(
            TenantProcessModel.Tenant(
              id = tenantId,
              selfcareId = selfcareId.some,
              externalId = TenantProcessModel.ExternalId("other-origin", "externalId"),
              features = Nil,
              attributes = Nil,
              createdAt = OffsetDateTimeSupplier.get(),
              updatedAt = None,
              mails = Nil,
              name = "foo"
            )
          )
        )

      val desiredClaimSet: Map[String, AnyRef] = Map(
        "uid"            -> uid,
        "user-roles"     -> "admin,anotherRole",
        "organizationId" -> tenantId.toString,
        "selfcareId"     -> selfcareId,
        "externalId"     -> Map("origin" -> "other-origin", "value" -> "externalId").asJava,
        "organization"   -> Map(
          "id"         -> selfcareId,
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

      Post() ~> authorizationService.getSessionToken(IdentityToken(bearerToken))(
        Seq.empty,
        toEntityMarshallerSessionToken
      ) ~> check {
        status shouldEqual StatusCodes.OK
        responseAs[SessionToken] shouldEqual SessionToken("sessionToken")
      }
    }

    "succeed if the tenant needs to be upserted and its origin is not IPA or ANAC and is not in allowlist" in {

      val uid: String        = UUID.randomUUID().toString
      val selfcareId: String = UUID.randomUUID().toString

      val jwtClaimsSet: JWTClaimsSet = Map[String, Object](
        "organization" -> Map(
          "id"         -> selfcareId,
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

      (mockInteropTokenGenerator
        .generateInternalToken(_: String, _: List[String], _: String, _: Long))
        .expects(*, *, *, *)
        .once()
        .returns(Future.successful(internalToken))

      (mockTenantProcess
        .getBySelfcareId(_: UUID)(_: Seq[(String, String)]))
        .expects(UUID.fromString(selfcareId), *)
        .once()
        .returns(Future.failed(SelfcareNotFound(UUID.fromString(selfcareId))))

      (mockPartyProcess
        .getInstitution(_: String)(_: Seq[(String, String)], _: ExecutionContext))
        .expects(selfcareId, *, *)
        .once()
        .returns(
          Future.successful(
            Institution(
              id = UUID.fromString(selfcareId),
              externalId = "whatever",
              originId = "non-IPACode",
              description = "foo",
              digitalAddress = "of a digital home?",
              address = "of an actual home?",
              zipCode = "winzip",
              taxCode = "not mine please",
              origin = "non-IPA",
              institutionType = None,
              attributes = Nil
            )
          )
        )

      Post() ~> authorizationService.getSessionToken(IdentityToken(bearerToken))(
        Seq.empty,
        toEntityMarshallerSessionToken
      ) ~> check {
        status shouldEqual StatusCodes.BadRequest
        responseAs[Problem].errors.map(_.code) should contain theSameElementsAs Seq("016-0011")
      }
    }

    "fail on JWTReader failure" in {

      (mockJwtReader
        .getClaims(_: String))
        .expects(*)
        .once()
        .returns(Failure(new RuntimeException("JWT reading fails")))

      Post() ~> authorizationService.getSessionToken(IdentityToken(bearerToken))(
        Seq.empty,
        toEntityMarshallerSessionToken
      ) ~> check {
        responseAs[Problem].errors.map(_.code) should contain theSameElementsAs Seq("016-9991")
      }
    }

    "fail on SessionTokenGenerator failure" in {

      val uid: String        = UUID.randomUUID().toString
      val selfcareId: String = UUID.randomUUID().toString
      val tenantId: UUID     = UUID.randomUUID()

      val jwtClaimsSet: JWTClaimsSet = Map[String, Object](
        "organization" -> Map(
          "id"         -> selfcareId,
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

      (mockInteropTokenGenerator
        .generateInternalToken(_: String, _: List[String], _: String, _: Long))
        .expects(*, *, *, *)
        .once()
        .returns(Future.successful(internalToken))

      (mockTenantProcess
        .getBySelfcareId(_: UUID)(_: Seq[(String, String)]))
        .expects(UUID.fromString(selfcareId), *)
        .once()
        .returns(
          Future.successful(
            TenantProcessModel.Tenant(
              id = tenantId,
              selfcareId = selfcareId.some,
              externalId = TenantProcessModel.ExternalId("IPA", "externalId"),
              features = Nil,
              attributes = Nil,
              createdAt = OffsetDateTimeSupplier.get(),
              updatedAt = None,
              mails = Nil,
              name = "name"
            )
          )
        )

      val desiredClaimSet: Map[String, AnyRef] = Map(
        "uid"            -> uid,
        "user-roles"     -> "admin",
        "organizationId" -> tenantId.toString,
        "selfcareId"     -> selfcareId,
        "externalId"     -> Map("origin" -> "IPA", "value" -> "externalId").asJava,
        "organization"   -> Map(
          "id"         -> selfcareId,
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

      Post() ~> authorizationService.getSessionToken(IdentityToken(bearerToken))(
        Seq.empty,
        toEntityMarshallerSessionToken
      ) ~> check {
        responseAs[Problem].errors.map(_.code) should contain theSameElementsAs Seq("016-9991")
      }
    }

  }

}
