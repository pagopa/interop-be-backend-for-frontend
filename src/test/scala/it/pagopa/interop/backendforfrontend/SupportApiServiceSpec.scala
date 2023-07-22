package it.pagopa.interop.backendforfrontend

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.testkit.ScalatestRouteTest
import it.pagopa.interop.backendforfrontend.api.impl.Utils._
import it.pagopa.interop.backendforfrontend.common.system.ApplicationConfiguration
import it.pagopa.interop.backendforfrontend.model.{Problem, SAMLTokenRequest}
import it.pagopa.interop.commons.signer.model.SignatureAlgorithm
import it.pagopa.interop.commons.utils.TypeConversions.StringOps
import it.pagopa.interop.commons.utils.service.OffsetDateTimeSupplier
import it.pagopa.interop.tenantprocess.client.model.{ExternalId, Tenant}
import org.scalatest.matchers.should.Matchers._
import org.scalatest.wordspec.AnyWordSpecLike

import java.nio.file.Paths
import java.time.{OffsetDateTime, ZoneOffset}
import java.util.UUID
import scala.concurrent.Future
import scala.io.Source
import scala.util.{Failure, Success, Using}

class SupportApiServiceSpec extends AnyWordSpecLike with SpecHelper with ScalatestRouteTest {

  "Generating a session token from a SAML2 Response" should {

    "succeed when the tenant is present and SAML2 Response is validated (redirect)" in {

      val response: String = Using(Source.fromFile(Paths.get("src/test/resources/saml2.xml").toFile()))(source =>
        source.getLines().mkString.encodeBase64
      ).flatten match {
        case Success(s) => s
        case Failure(e) => throw e
      }

      val timestamp = OffsetDateTime.of(2023, 5, 31, 9, 5, 40, 44, ZoneOffset.UTC)
      (() => mockDateTimeSupplier.get()).expects().returning(timestamp).once()

      val tenantId: UUID = ApplicationConfiguration.pagoPaTenantId

      (mockSessionTokenGenerator
        .generate(_: SignatureAlgorithm, _: Map[String, AnyRef], _: Set[String], _: String, _: Long))
        .expects(
          SignatureAlgorithm.RSAPkcs1Sha256,
          buildClaims(tenantId),
          ApplicationConfiguration.generatedJwtAudience,
          ApplicationConfiguration.generatedJwtIssuer,
          ApplicationConfiguration.supportLandingJwtDuration
        )
        .once()
        .returns(Future.successful("sessionToken"))

      Post() ~> authorizationService.samlLoginCallback(response, emptyRelayState) ~> check {
        status shouldEqual StatusCodes.Found
      }
    }

    "fail when selfcareId is missing (redirect)" in {

      val response: String = Using(Source.fromFile(Paths.get("src/test/resources/saml2.xml").toFile()))(source =>
        source.getLines().mkString.encodeBase64
      ).flatten match {
        case Success(s) => s
        case Failure(e) => throw e
      }

      val timestamp = OffsetDateTime.of(2023, 5, 31, 9, 5, 40, 44, ZoneOffset.UTC)
      (() => mockDateTimeSupplier.get()).expects().returning(timestamp).once()

      Post() ~> authorizationService.samlLoginCallback(response, emptyRelayState) ~> check {
        status shouldEqual StatusCodes.Found
      }
    }

    "succeed when a valid tenant is passed as a parameter and SAML2 Response is validated" in {

      val response = Using(Source.fromFile(Paths.get("src/test/resources/saml2.xml").toFile()))(source =>
        source.getLines().mkString.encodeBase64
      ).flatten match {
        case Success(s) => s
        case Failure(e) => throw e
      }

      val timestamp = OffsetDateTime.of(2023, 5, 31, 9, 5, 40, 44, ZoneOffset.UTC)
      (() => mockDateTimeSupplier.get()).expects().returning(timestamp).once()

      val tenantId: UUID   = UUID.randomUUID()
      val selfcareId: UUID = UUID.randomUUID()
      val tenant: Tenant   = Tenant(
        id = tenantId,
        selfcareId = Some(selfcareId.toString),
        externalId = ExternalId("IPA", "externalId"),
        features = Nil,
        attributes = Nil,
        createdAt = OffsetDateTimeSupplier.get(),
        updatedAt = None,
        mails = Nil,
        name = "PagoPa"
      )

      (mockTenantProcess
        .getTenant(_: UUID)(_: Seq[(String, String)]))
        .expects(tenantId, *)
        .once()
        .returns(Future.successful(tenant))

      (mockSessionTokenGenerator
        .generate(_: SignatureAlgorithm, _: Map[String, AnyRef], _: Set[String], _: String, _: Long))
        .expects(
          SignatureAlgorithm.RSAPkcs1Sha256,
          buildClaimsByTenant(selfcareId.toString, tenant),
          ApplicationConfiguration.generatedJwtAudience,
          ApplicationConfiguration.generatedJwtIssuer,
          ApplicationConfiguration.supportJwtDuration
        )
        .once()
        .returns(Future.successful("sessionToken"))

      Post() ~> supportService.getSaml2Token(SAMLTokenRequest(response, tenantId)) ~> check {
        status shouldEqual StatusCodes.OK
      }
    }

    "fail when selfcareId is missing" in {

      val response: String = Using(Source.fromFile(Paths.get("src/test/resources/saml2.xml").toFile()))(source =>
        source.getLines().mkString.encodeBase64
      ).flatten match {
        case Success(s) => s
        case Failure(e) => throw e
      }

      val timestamp = OffsetDateTime.of(2023, 5, 31, 9, 5, 40, 44, ZoneOffset.UTC)
      (() => mockDateTimeSupplier.get()).expects().returning(timestamp).once()

      val tenantId: UUID = UUID.randomUUID()

      (mockTenantProcess
        .getTenant(_: UUID)(_: Seq[(String, String)]))
        .expects(tenantId, *)
        .once()
        .returns(
          Future.successful(
            Tenant(
              id = tenantId,
              selfcareId = None,
              externalId = ExternalId("IPA", "externalId"),
              features = Nil,
              attributes = Nil,
              createdAt = OffsetDateTimeSupplier.get(),
              updatedAt = None,
              mails = Nil,
              name = "PagoPa"
            )
          )
        )

      Post() ~> supportService.getSaml2Token(SAMLTokenRequest(response, tenantId)) ~> check {
        status shouldEqual StatusCodes.InternalServerError
        val problem = responseAs[Problem]
        problem.status shouldBe StatusCodes.InternalServerError.intValue
      }
    }

    "fail when SAML2 Response is not valid, due to DATE condition" in {

      val response: String = Using(Source.fromFile(Paths.get("src/test/resources/saml2.xml").toFile()))(source =>
        source.getLines().mkString.encodeBase64
      ).flatten match {
        case Success(s) => s
        case Failure(e) => throw e
      }

      val timestamp = OffsetDateTime.now(ZoneOffset.UTC)
      (() => mockDateTimeSupplier.get()).expects().returning(timestamp).once()

      Post() ~> supportService.getSaml2Token(SAMLTokenRequest(response, UUID.randomUUID())) ~> check {
        status shouldEqual StatusCodes.InternalServerError
        val problem = responseAs[Problem]
        problem.status shouldBe StatusCodes.InternalServerError.intValue
        problem.errors.head.code shouldBe "016-9991"
      }
    }

    "fail when SAML2 Response is not valid, due to AUDIENCE condition" in {

      val response = Using(Source.fromFile(Paths.get("src/test/resources/saml2_err_aud.xml").toFile()))(source =>
        source.getLines().mkString.encodeBase64
      ).flatten match {
        case Success(s) => s
        case Failure(e) => throw e
      }

      val timestamp      = OffsetDateTime.of(2023, 5, 31, 9, 5, 40, 44, ZoneOffset.UTC)
      val tenantId: UUID = UUID.randomUUID()

      (() => mockDateTimeSupplier.get()).expects().returning(timestamp).once()

      Post() ~> supportService.getSaml2Token(SAMLTokenRequest(response, tenantId)) ~> check {
        status shouldEqual StatusCodes.InternalServerError
        val problem = responseAs[Problem]
        problem.status shouldBe StatusCodes.InternalServerError.intValue
        problem.errors.head.code shouldBe "016-9991"
      }
    }

    "fail when SAML2 Response is not valid, due to SUPPORT LEVEL condition" in {

      val response =
        Using(Source.fromFile(Paths.get("src/test/resources/saml2_err_sl.xml").toFile()))(source =>
          source.getLines().mkString.encodeBase64
        ).flatten match {
          case Success(s) => s
          case Failure(e) => throw e
        }

      val timestamp      = OffsetDateTime.of(2023, 5, 31, 9, 5, 40, 44, ZoneOffset.UTC)
      val tenantId: UUID = UUID.randomUUID()

      (() => mockDateTimeSupplier.get()).expects().returning(timestamp).once()

      Post() ~> supportService.getSaml2Token(SAMLTokenRequest(response, tenantId)) ~> check {
        status shouldEqual StatusCodes.InternalServerError
        val problem = responseAs[Problem]
        problem.status shouldBe StatusCodes.InternalServerError.intValue
        problem.errors.head.code shouldBe "016-9991"
      }
    }
  }
}
