import it.pagopa.interop.backendforfrontend.common.system.FileManagerUtils
import it.pagopa.interop.backendforfrontend.error.BFFErrors.InvalidInterfaceContentTypeDetected
import it.pagopa.interop.catalogprocess.client.model._
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.nio.file.{Files, Paths}
import java.util.UUID

class FileManagerUtilsSpec() extends AnyWordSpec with Matchers with ScalaFutures {

  val eServiceRest: EService = EService(
    id = UUID.randomUUID(),
    producerId = UUID.randomUUID(),
    name = "name",
    description = "description",
    technology = EServiceTechnology.REST,
    descriptors = Seq.empty,
    riskAnalysis = Seq.empty,
    mode = EServiceMode.DELIVER
  )
  val eServiceSoap: EService = eServiceRest.copy(technology = EServiceTechnology.SOAP)

  "a FileManagerUtils.verify " should {
    "succeed with a JSON file" in {

      val file = Files.readAllBytes(Paths.get("src/test/resources/apis.json"))

      FileManagerUtils
        .verify(file = file, filename = "apis.json", eService = eServiceRest, isInterface = true)(
          Seq.empty
        ) shouldBe Right(())
    }

    "succeed with a YAML file" in {

      val file = Files.readAllBytes(Paths.get("src/test/resources/apis.json"))

      FileManagerUtils
        .verify(file = file, filename = "apis.json", eService = eServiceRest, isInterface = true)(
          Seq.empty
        ) shouldBe Right(())
    }

    "succeed with a WSDL file" in {

      val file = Files.readAllBytes(Paths.get("src/test/resources/apis.wsdl"))

      FileManagerUtils
        .verify(file = file, filename = "apis.wsdl", eService = eServiceSoap, isInterface = true)(
          Seq.empty
        ) shouldBe Right(())
    }

    "succeed with a XML file" in {

      val file = Files.readAllBytes(Paths.get("src/test/resources/apis.xml"))

      FileManagerUtils
        .verify(file = file, filename = "apis.xml", eService = eServiceSoap, isInterface = true)(
          Seq.empty
        ) shouldBe Right(())
    }

    "fail for unexpected file format" in {

      val file = Files.readAllBytes(Paths.get("src/test/resources/apis.conf"))

      FileManagerUtils
        .verify(file = file, filename = "apis.conf", eService = eServiceSoap, isInterface = true)(
          Seq.empty
        ) shouldBe Left(
        InvalidInterfaceContentTypeDetected(eServiceSoap.id.toString, "text/x-config", eServiceSoap.technology.toString)
      )
    }

  }
}
