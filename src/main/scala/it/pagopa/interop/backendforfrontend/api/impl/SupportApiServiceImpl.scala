package it.pagopa.interop.backendforfrontend.api.impl

import akka.http.scaladsl.marshalling.ToEntityMarshaller
import akka.http.scaladsl.server.Directives.onComplete
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.model.HttpHeader
import com.typesafe.scalalogging.{Logger, LoggerTakingImplicit}
import it.pagopa.interop.backendforfrontend.api.SupportApiService
import it.pagopa.interop.backendforfrontend.api.impl.Utils.{buildClaimsByTenant, parseResponse, validate}
import it.pagopa.interop.backendforfrontend.common.system.ApplicationConfiguration
import it.pagopa.interop.backendforfrontend.error.BFFErrors._
import it.pagopa.interop.backendforfrontend.error.Handlers.handleError
import it.pagopa.interop.backendforfrontend.model._
import it.pagopa.interop.backendforfrontend.service.TenantProcessService
import it.pagopa.interop.commons.jwt.service.SessionTokenGenerator
import it.pagopa.interop.commons.logging.{CanLogContextFields, ContextFieldsToLog}
import it.pagopa.interop.commons.signer.model.SignatureAlgorithm
import it.pagopa.interop.commons.utils.TypeConversions._
import it.pagopa.interop.commons.utils._
import it.pagopa.interop.commons.utils.service.OffsetDateTimeSupplier
import it.pagopa.interop.backendforfrontend.common.HeaderUtils._

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Success

final case class SupportApiServiceImpl(
  sessionTokenGenerator: SessionTokenGenerator,
  tenantProcessService: TenantProcessService,
  offsetDateTimeSupplier: OffsetDateTimeSupplier
)(implicit ec: ExecutionContext)
    extends SupportApiService {

  private implicit val logger: LoggerTakingImplicit[ContextFieldsToLog] =
    Logger.takingImplicit[ContextFieldsToLog](this.getClass)

  override def getSaml2Token(sAMLTokenRequest: SAMLTokenRequest)(implicit
    contexts: Seq[(BearerToken, BearerToken)],
    toEntityMarshallerSessionToken: ToEntityMarshaller[SessionToken],
    toEntityMarshallerProblem: ToEntityMarshaller[Problem]
  ): Route = {
    logger.info(s"Calling get SAML2 token")

    val result: Future[SessionToken] = for {
      decodeResponse <- sAMLTokenRequest.saml2.decodeBase64.toFuture
      responseXml    <- parseResponse(decodeResponse).toFuture
      _              <- validate(responseXml)(offsetDateTimeSupplier).toFuture
      tenant         <- tenantProcessService.getTenant(sAMLTokenRequest.tenantId)
      selfcareId     <- tenant.selfcareId.toFuture(MissingSelfcareId(tenant.id))
      sessionToken   <- sessionTokenGenerator.generate(
        signatureAlgorithm = SignatureAlgorithm.RSAPkcs1Sha256,
        claimsSet = buildClaimsByTenant(selfcareId, tenant),
        audience = ApplicationConfiguration.generatedJwtAudience,
        tokenIssuer = ApplicationConfiguration.generatedJwtIssuer,
        validityDurationInSeconds = ApplicationConfiguration.supportJwtDuration
      )
    } yield SessionToken(sessionToken)

    onComplete(result) {
      val headers: List[HttpHeader] = headersFromContext()
      handleError(s"Error creating a session token", headers) orElse { case Success(token) =>
        getSaml2Token200(headers)(token)
      }
    }
  }

}
