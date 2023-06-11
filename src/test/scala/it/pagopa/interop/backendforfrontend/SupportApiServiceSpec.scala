package it.pagopa.interop.backendforfrontend

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.testkit.ScalatestRouteTest
import cats.syntax.all._
import it.pagopa.interop.backendforfrontend.model.SAMLResponse
import it.pagopa.interop.backendforfrontend.common.system.ApplicationConfiguration
import it.pagopa.interop.commons.signer.model.SignatureAlgorithm
import it.pagopa.interop.commons.utils.service.OffsetDateTimeSupplier
import it.pagopa.interop.tenantprocess.client.model.{ExternalId, Tenant}
import org.scalatest.matchers.should.Matchers._
import org.scalatest.wordspec.AnyWordSpecLike

import java.util.UUID
import java.nio.file.Paths
import scala.io.Source
import scala.util.Using
import java.time.{OffsetDateTime, ZoneOffset}
import scala.concurrent.Future

class SupportApiServiceSpec extends AnyWordSpecLike with SpecHelper with ScalatestRouteTest {

  "Generating a session token from a SAML2 Response" should {

    "succeed when the tenant is present and SAML2 Response is validated" in {

      Using(Source.fromFile(Paths.get("src/test/resources/saml2.xml").toFile())) { source =>
        val response: SAMLResponse = SAMLResponse(source.getLines().mkString)

        val timestamp = OffsetDateTime.of(2023, 5, 31, 9, 5, 40, 44, ZoneOffset.UTC)
        (() => mockDateTimeSupplier.get()).expects().returning(timestamp).once()

        val tenantId: UUID     = UUID.fromString(ApplicationConfiguration.pagoPaTenantId)
        val selfcareId: String = UUID.randomUUID().toString

        (mockTenantProcess
          .getTenant(_: UUID)(_: Seq[(String, String)]))
          .expects(tenantId, *)
          .once()
          .returns(
            Future.successful(
              Tenant(
                id = tenantId,
                selfcareId = selfcareId.some,
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

        val desiredClaimSet: Map[String, AnyRef] = Map(
          "uid"            -> "support",
          "user-roles"     -> "support",
          "organizationId" -> tenantId.toString,
          "selfcareId"     -> selfcareId,
          "organization" -> s"{\"id\":\"$selfcareId\",\"name\":\"PagoPa\",\"roles\":[{\"partyRole\":\"OPERATOR\",\"role\":\"support\"}]}"
        ).widen[AnyRef]

        (mockSessionTokenGenerator
          .generate(_: SignatureAlgorithm, _: Map[String, AnyRef], _: Set[String], _: String, _: Long))
          .expects(
            SignatureAlgorithm.RSAPkcs1Sha256,
            desiredClaimSet,
            ApplicationConfiguration.generatedJwtAudience,
            ApplicationConfiguration.generatedJwtIssuer,
            300
          )
          .once()
          .returns(Future.successful("sessionToken"))

        Post() ~> supportService.redirectToSupportPage(response) ~> check {
          status shouldEqual StatusCodes.MovedPermanently
        }
      }
    }
  }

  "succeed when the tenant is passed as a parameter and SAML2 Response is validated" in {

    Using(Source.fromFile(Paths.get("src/test/resources/saml2.xml").toFile())) { source =>
      val response: SAMLResponse = SAMLResponse(source.getLines().mkString)

      val timestamp = OffsetDateTime.of(2023, 5, 31, 9, 5, 40, 44, ZoneOffset.UTC)
      (() => mockDateTimeSupplier.get()).expects().returning(timestamp).once()

      val tenantId: UUID     = UUID.randomUUID()
      val selfcareId: String = UUID.randomUUID().toString

      (mockTenantProcess
        .getTenant(_: UUID)(_: Seq[(String, String)]))
        .expects(tenantId, *)
        .once()
        .returns(
          Future.successful(
            Tenant(
              id = tenantId,
              selfcareId = selfcareId.some,
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

      val desiredClaimSet: Map[String, AnyRef] = Map(
        "uid"            -> "support",
        "user-roles"     -> "support",
        "organizationId" -> tenantId.toString,
        "selfcareId"     -> selfcareId,
        "organization" -> s"{\"id\":\"$selfcareId\",\"name\":\"PagoPa\",\"roles\":[{\"partyRole\":\"OPERATOR\",\"role\":\"support\"}]}"
      ).widen[AnyRef]

      (mockSessionTokenGenerator
        .generate(_: SignatureAlgorithm, _: Map[String, AnyRef], _: Set[String], _: String, _: Long))
        .expects(
          SignatureAlgorithm.RSAPkcs1Sha256,
          desiredClaimSet,
          ApplicationConfiguration.generatedJwtAudience,
          ApplicationConfiguration.generatedJwtIssuer,
          3600
        )
        .once()
        .returns(Future.successful("sessionToken"))

      Post() ~> supportService.getSaml2Token(tenantId.toString, response) ~> check {
        status shouldEqual StatusCodes.OK
      }
    }
  }
}
