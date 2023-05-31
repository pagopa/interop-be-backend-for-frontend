package it.pagopa.interop.backendforfrontend.api.impl

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.marshalling.ToEntityMarshaller
import akka.http.scaladsl.unmarshalling.{FromEntityUnmarshaller, Unmarshaller}
import it.pagopa.interop.backendforfrontend.api.HealthApiMarshaller
import it.pagopa.interop.backendforfrontend.model.{Problem, SAMLResponse}
import spray.json.DefaultJsonProtocol
import akka.http.scaladsl.marshallers.xml.ScalaXmlSupport._
import akka.http.scaladsl.model.{ContentTypes, MediaTypes}
import akka.http.scaladsl.unmarshalling.Unmarshaller.UnsupportedContentTypeException
import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.marshallers.xml.ScalaXmlSupport._
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, MediaTypes}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.{ExceptionHandler, Route}
import akka.http.scaladsl.unmarshalling.{FromEntityUnmarshaller, Unmarshaller}
import akka.stream.ActorMaterializer

import scala.concurrent.{ExecutionContextExecutor, Future}
import scala.xml.NodeSeq
import scala.concurrent.Future

object Hea extends HealthApiMarshaller {}

object HealthApiMarshallerImpl extends HealthApiMarshaller with SprayJsonSupport with DefaultJsonProtocol {

  override implicit def fromEntityUnmarshallerSAMLResponse: FromEntityUnmarshaller[SAMLResponse] =
    Unmarshaller.stringUnmarshaller
      .forContentTypes(MediaTypes.`text/xml`, MediaTypes.`application/xml`)
      .flatMap { _ => _ => nodeSeq =>
        nodeSeq.headOption match {
          case Some(xml) => Future.successful(SAMLResponse(Some(xml)))
          case None      =>
            Future.failed(
              UnsupportedContentTypeException(Some(ContentTypes.`text/xml(UTF-8)`), Some(MediaTypes.`text/xml`))
            )
        }
      }

  override implicit def toEntityMarshallerProblem: ToEntityMarshaller[Problem] = entityMarshallerProblem
}
