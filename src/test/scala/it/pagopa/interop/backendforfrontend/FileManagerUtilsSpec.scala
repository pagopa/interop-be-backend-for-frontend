import akka.http.scaladsl.model.{ContentTypes, HttpCharsets, MediaType}
import akka.http.scaladsl.server.directives.FileInfo
import it.pagopa.interop.backendforfrontend.error.BFFErrors.InvalidInterfaceContentTypeDetected
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import it.pagopa.interop.backendforfrontend.common.system.FileManagerUtils
import it.pagopa.interop.catalogprocess.client.model._

import java.io.File
import java.nio.file.Paths
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

      val file = Paths.get("src/test/resources/apis.json").toFile

      val fileParts: (FileInfo, File) =
        (FileInfo(fieldName = "apis", fileName = "apis.json", contentType = ContentTypes.`application/json`), file)

      FileManagerUtils
        .verify(fileParts = fileParts, eService = eServiceRest, isInterface = true)(Seq.empty) shouldBe Right(())
    }

    "succeed with a YAML file" in {

      val file = Paths.get("src/test/resources/apis.json").toFile

      val fileParts: (FileInfo, File) =
        (
          FileInfo(
            fieldName = "apis",
            fileName = "apis.yaml",
            contentType =
              MediaType.customWithFixedCharset("application", "x-yaml", HttpCharsets.`UTF-8`, List("yaml", "yml"))
          ),
          file
        )

      FileManagerUtils
        .verify(fileParts = fileParts, eService = eServiceRest, isInterface = true)(Seq.empty) shouldBe Right(())
    }

    "succeed with a WSDL file" in {

      val file = Paths.get("src/test/resources/apis.wsdl").toFile

      val fileParts: (FileInfo, File) =
        (
          FileInfo(
            fieldName = "apis",
            fileName = "apis.wsdl",
            contentType =
              MediaType.customWithFixedCharset("application", "wsdl+xml", HttpCharsets.`UTF-8`, List("wsdl"))
          ),
          file
        )

      FileManagerUtils
        .verify(fileParts = fileParts, eService = eServiceSoap, isInterface = true)(Seq.empty) shouldBe Right(())
    }

    "succeed with a XML file" in {

      val file = Paths.get("src/test/resources/apis.xml").toFile

      val fileParts: (FileInfo, File) =
        (
          FileInfo(
            fieldName = "apis",
            fileName = "apis.xml",
            contentType = MediaType.customWithFixedCharset("application", "soap+xml", HttpCharsets.`UTF-8`, List("xml"))
          ),
          file
        )

      FileManagerUtils
        .verify(fileParts = fileParts, eService = eServiceSoap, isInterface = true)(Seq.empty) shouldBe Right(())
    }

    "fail for unexpected file format" in {

      val file = Paths.get("src/test/resources/apis.conf").toFile

      val fileParts: (FileInfo, File) =
        (
          FileInfo(
            fieldName = "apis",
            fileName = "apis.conf",
            contentType = MediaType.customWithFixedCharset("text", "plain", HttpCharsets.`UTF-8`, List("conf"))
          ),
          file
        )

      FileManagerUtils
        .verify(fileParts = fileParts, eService = eServiceSoap, isInterface = true)(Seq.empty) shouldBe Left(
        InvalidInterfaceContentTypeDetected(eServiceSoap.id.toString, "text/x-config", eServiceSoap.technology.toString)
      )
    }

  }
}
