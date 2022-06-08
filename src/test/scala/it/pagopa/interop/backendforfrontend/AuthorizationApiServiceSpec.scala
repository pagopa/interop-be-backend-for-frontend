package it.pagopa.interop.backendforfrontend

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.testkit.ScalatestRouteTest
import com.nimbusds.jwt.JWTClaimsSet
import it.pagopa.interop.backendforfrontend.api.impl.AuthorizationApiMarshallerImpl._
import it.pagopa.interop.backendforfrontend.common.system.ApplicationConfiguration
import it.pagopa.interop.backendforfrontend.model.{IdentityToken, Problem, SessionToken}
import it.pagopa.interop.commons.signer.model.SignatureAlgorithm
import org.scalatest.matchers.should.Matchers._
import org.scalatest.wordspec.AnyWordSpecLike

import java.util.UUID
import scala.concurrent.Future
import scala.jdk.CollectionConverters.MapHasAsJava
import scala.util.{Failure, Success}

class AuthorizationApiServiceSpec extends AnyWordSpecLike with SpecHelper with ScalatestRouteTest {

  "Generating a session token" should {

    "succeed" in {

      val uid: String                       = UUID.randomUUID().toString
      val organization: Map[String, String] = Map("id" -> "id", "role" -> "role", "fiscalCode" -> "fiscalCode")
      val claimSet: Map[String, AnyRef]     = Map("uid" -> uid, "organization" -> organization.asJava)

      val builder: JWTClaimsSet.Builder = new JWTClaimsSet.Builder()
      val jwtClaimsSet: JWTClaimsSet    = builder.claim("uid", uid).claim("organization", organization.asJava).build()

      (mockJwtReader
        .getClaims(_: String))
        .expects(*)
        .once()
        .returns(Success(jwtClaimsSet))

      (mockSessionTokenGenerator
        .generate(_: SignatureAlgorithm, _: Map[String, AnyRef], _: Set[String], _: String, _: Long))
        .expects(
          SignatureAlgorithm.RSAPkcs1Sha256,
          claimSet,
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
        responseAs[Problem].errors.map(_.code) should contain theSameElementsAs Seq("016-0001")
      }
    }

    "fail on SessionTokenGenerator failure" in {

      val uid: String                       = UUID.randomUUID().toString
      val organization: Map[String, String] = Map("id" -> "id", "role" -> "role", "fiscalCode" -> "fiscalCode")
      val claimSet: Map[String, AnyRef]     = Map("uid" -> uid, "organization" -> organization.asJava)

      val builder: JWTClaimsSet.Builder = new JWTClaimsSet.Builder()
      val jwtClaimsSet: JWTClaimsSet    = builder.claim("uid", uid).claim("organization", organization.asJava).build()

      (mockJwtReader
        .getClaims(_: String))
        .expects(*)
        .once()
        .returns(Success(jwtClaimsSet))

      (mockSessionTokenGenerator
        .generate(_: SignatureAlgorithm, _: Map[String, AnyRef], _: Set[String], _: String, _: Long))
        .expects(
          SignatureAlgorithm.RSAPkcs1Sha256,
          claimSet,
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
        responseAs[Problem].errors.map(_.code) should contain theSameElementsAs Seq("016-0001")
      }
    }

  }

}
