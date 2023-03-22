package it.pagopa.interop.backendforfrontend.api.impl

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.marshalling.ToEntityMarshaller
import akka.http.scaladsl.unmarshalling.FromEntityUnmarshaller
import it.pagopa.interop.backendforfrontend.api.ClientsApiMarshaller
import it.pagopa.interop.backendforfrontend.model._
import spray.json.DefaultJsonProtocol

object ClientsApiMarshallerImpl extends ClientsApiMarshaller with SprayJsonSupport with DefaultJsonProtocol {

  override implicit def toEntityMarshallerProblem: ToEntityMarshaller[Problem] = entityMarshallerProblem

  override implicit def toEntityMarshallerCreatedResource: ToEntityMarshaller[CreatedResource] =
    sprayJsonMarshaller[CreatedResource]

  override implicit def fromEntityUnmarshallerPurposeAdditionDetailsSeed
    : FromEntityUnmarshaller[PurposeAdditionDetailsSeed] = sprayJsonUnmarshaller[PurposeAdditionDetailsSeed]

  override implicit def toEntityMarshallerOperatorarray: ToEntityMarshaller[Seq[Operator]] =
    sprayJsonMarshaller[Seq[Operator]]

  override implicit def fromEntityUnmarshallerKeySeedList: FromEntityUnmarshaller[Seq[KeySeed]] =
    sprayJsonUnmarshaller[Seq[KeySeed]]

  override implicit def toEntityMarshallerEncodedClientKey: ToEntityMarshaller[EncodedClientKey] =
    sprayJsonMarshaller[EncodedClientKey]

  override implicit def fromEntityUnmarshallerClientSeed: FromEntityUnmarshaller[ClientSeed] =
    sprayJsonUnmarshaller[ClientSeed]

  override implicit def toEntityMarshallerCompactClients: ToEntityMarshaller[CompactClients] =
    sprayJsonMarshaller[CompactClients]

  override implicit def toEntityMarshallerPublicKeys: ToEntityMarshaller[PublicKeys] =
    sprayJsonMarshaller[PublicKeys]

  override implicit def toEntityMarshallerPublicKey: ToEntityMarshaller[PublicKey] = sprayJsonMarshaller[PublicKey]

  override implicit def toEntityMarshallerClient: ToEntityMarshaller[Client] = sprayJsonMarshaller[Client]

}
