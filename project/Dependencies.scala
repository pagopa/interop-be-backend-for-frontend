import PagopaVersions._
import Versions._
import sbt._

object Dependencies {

  private[this] object akka {
    lazy val namespace           = "com.typesafe.akka"
    lazy val actorTyped          = namespace                       %% "akka-actor-typed"       % akkaVersion
    lazy val actor               = namespace                       %% "akka-actor"             % akkaVersion
    lazy val persistence         = namespace                       %% "akka-persistence-typed" % akkaVersion
    lazy val stream              = namespace                       %% "akka-stream"            % akkaVersion
    lazy val http                = namespace                       %% "akka-http"              % akkaHttpVersion
    lazy val httpJson            = namespace                       %% "akka-http-spray-json"   % akkaHttpVersion
    lazy val httpJson4s          = "de.heikoseeberger"             %% "akka-http-json4s"       % "1.38.2"
    lazy val management          = "com.lightbend.akka.management" %% "akka-management"        % akkaManagementVersion
    lazy val managementLogLevels =
      "com.lightbend.akka.management" %% "akka-management-loglevels-logback" % akkaManagementVersion
    lazy val slf4j          = namespace %% "akka-slf4j"               % akkaVersion
    lazy val testkit        = namespace %% "akka-actor-testkit-typed" % akkaVersion
    lazy val httpTestkit    = namespace %% "akka-http-testkit"        % akkaHttpVersion
    lazy val untypedTestkit = namespace %% "akka-testkit"             % akkaVersion

  }

  private[this] object pagopa {
    lazy val namespace = "it.pagopa"

    lazy val commons = namespace %% "interop-commons-utils"  % commonsVersion
    lazy val jwt     = namespace %% "interop-commons-jwt"    % commonsVersion
    lazy val signer  = namespace %% "interop-commons-signer" % commonsVersion

    lazy val partyManagementClient =
      namespace %% "interop-selfcare-party-management-client" % partyManagementVersion

    lazy val partyProcessClient =
      namespace %% "interop-selfcare-party-process-client" % partyProcessVersion

    lazy val userRegistryClient =
      namespace %% "interop-selfcare-user-registry-client" % userRegistryVersion

    lazy val attributeRegistryClient =
      namespace %% "interop-be-attribute-registry-management-client" % attributeRegistryVersion
  }

  private[this] object cats {
    lazy val namespace = "org.typelevel"
    lazy val core      = namespace %% "cats-core" % catsVersion
  }

  private[this] object json4s {
    lazy val namespace = "org.json4s"
    lazy val jackson   = namespace %% "json4s-jackson" % json4sVersion
    lazy val ext       = namespace %% "json4s-ext"     % json4sVersion
  }

  private[this] object jackson {
    lazy val namespace   = "com.fasterxml.jackson.core"
    lazy val core        = namespace % "jackson-core"        % jacksonVersion
    lazy val annotations = namespace % "jackson-annotations" % jacksonVersion
    lazy val databind    = namespace % "jackson-databind"    % jacksonVersion
  }

  private[this] object logback {
    lazy val namespace = "ch.qos.logback"
    lazy val classic   = namespace % "logback-classic" % logbackVersion
  }

  private[this] object kamon {
    lazy val namespace  = "io.kamon"
    lazy val bundle     = namespace %% "kamon-bundle"     % kamonVersion
    lazy val prometheus = namespace %% "kamon-prometheus" % kamonVersion
  }

  private[this] object mustache {
    lazy val mustache = "com.github.spullara.mustache.java" % "compiler" % mustacheVersion
  }

  private[this] object scalatest {
    lazy val namespace = "org.scalatest"
    lazy val core      = namespace %% "scalatest" % scalatestVersion
  }

  private[this] object scalamock {
    lazy val namespace = "org.scalamock"
    lazy val core      = namespace %% "scalamock" % scalaMockVersion
  }

  object Jars {
    lazy val overrides: Seq[ModuleID] =
      Seq(jackson.annotations % Compile, jackson.core % Compile, jackson.databind % Compile)
    lazy val `server`: Seq[ModuleID]  = Seq(
      // For making Java 12 happy
      "javax.annotation"             % "javax.annotation-api" % "1.3.2" % "compile",
      //
      akka.actor                     % Compile,
      akka.actorTyped                % Compile,
      akka.http                      % Compile,
      akka.httpJson                  % Compile,
      akka.management                % Compile,
      akka.managementLogLevels       % Compile,
      akka.persistence               % Compile,
      akka.slf4j                     % Compile,
      akka.stream                    % Compile,
      cats.core                      % Compile,
      kamon.bundle                   % Compile,
      kamon.prometheus               % Compile,
      logback.classic                % Compile,
      mustache.mustache              % Compile,
      pagopa.commons                 % Compile,
      pagopa.jwt                     % Compile,
      pagopa.partyProcessClient      % Compile,
      pagopa.partyManagementClient   % Compile,
      pagopa.userRegistryClient      % Compile,
      pagopa.attributeRegistryClient % Compile,
      pagopa.signer                  % Compile,
      akka.httpTestkit               % Test,
      akka.testkit                   % Test,
      akka.untypedTestkit            % Test,
      scalamock.core                 % Test,
      scalatest.core                 % Test
    )
    lazy val client: Seq[ModuleID]    = Seq(
      akka.stream     % Compile,
      akka.http       % Compile,
      akka.httpJson4s % Compile,
      akka.slf4j      % Compile,
      json4s.jackson  % Compile,
      json4s.ext      % Compile
    )
  }
}
