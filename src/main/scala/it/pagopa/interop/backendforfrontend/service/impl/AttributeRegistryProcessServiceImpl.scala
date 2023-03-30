package it.pagopa.interop.backendforfrontend.service.impl

import akka.actor.typed.ActorSystem
//import com.typesafe.scalalogging.{Logger, LoggerTakingImplicit}
import it.pagopa.interop.backendforfrontend.service.AttributeRegistryProcessService
//import it.pagopa.interop.commons.logging.{CanLogContextFields, ContextFieldsToLog}

import scala.concurrent.{ExecutionContextExecutor, Future}

class AttributeRegistryProcessServiceImpl(attributeRegistryURL: String, blockingEc: ExecutionContextExecutor)(implicit
  system: ActorSystem[_]
) extends AttributeRegistryProcessService {

  val x = attributeRegistryURL
  val y = blockingEc
  val z = system
  // val invoker: ApiInvoker = ApiInvoker(EnumsSerializers.all, blockingEc)(system.classicSystem)
  // val api: AttributeApi   = AttributeApi(attributeRegistryURL)

  /*  private implicit val logger: LoggerTakingImplicit[ContextFieldsToLog] =
    Logger.takingImplicit[ContextFieldsToLog](this.getClass)*/

  override def getAttributes(name: Option[String], limit: Int, offset: Int, kinds: String)(implicit
    contexts: Seq[(String, String)]
  ): Future[Unit] = ???
}
