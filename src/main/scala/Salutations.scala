
import io.vertx.core.json.JsonObject
import io.vertx.scala.core.Vertx
import io.vertx.scala.ext.web.Router
import io.vertx.scala.servicediscovery.types.HttpEndpoint
import io.vertx.scala.servicediscovery.{ServiceDiscovery, ServiceDiscoveryOptions}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.{Failure, Success}

object Salutations {
  def main(args: Array[String]) {

    val vertx = Vertx.vertx()
    val server = vertx.createHttpServer()
    val router = Router.router(vertx)

    val redisHost = sys.env.get("REDIS_HOST") match {
      case None => "127.0.0.1"
      case Some(host) => host
    }
    val redisPort = sys.env.get("REDIS_PORT") match {
      case None => 6379
      case Some(port) => port.toInt
    }
    val redisAuth = sys.env.get("REDIS_PASSWORD") match {
      case None => null
      case Some(auth) => auth
    }
    val redisRecordsKey = sys.env.get("REDIS_RECORDS_KEY") match {
      case None => "scala-records"
      case Some(key) => key
    }

    val discovery = ServiceDiscovery.create(vertx, ServiceDiscoveryOptions()
      .setBackendConfiguration(
        new JsonObject()
          .put("host", redisHost)
          .put("port", redisPort)
          .put("auth", redisAuth)
          .put("key", redisRecordsKey)
      )
    )

    val serviceName = sys.env.get("SERVICE_NAME") match {
      case None => "salutations"
      case Some(name) => name
    }


    val serviceHost = sys.env.get("SERVICE_HOST") match {
      case None => {
        sys.env.get("APP_ID")  match {
          case None => "localhost"
          case Some(value) => value.replace("_", "-") + ".cleverapps.io"
        }
      }
      case Some(host) => host
    }

    val servicePort = sys.env.get("SERVICE_PORT") match { // external port has to be set to 80 on CC
      case None => {
        sys.env.get("APP_ID")  match {
          case None => 8080
          case Some(value) => 80
        }
      }
      case Some(port) => port.toInt
    }
    val serviceRoot = sys.env.get("SERVICE_ROOT") match {
      case None => "/api"
      case Some(root) => root
    }

    val record = HttpEndpoint.createRecord(
      serviceName,
      serviceHost,
      servicePort,
      serviceRoot
    )

    discovery.publishFuture(record).onComplete{
      case Success(result) => println(s"publication OK")
      case Failure(cause) => println(s"publication KO $cause")
    }

    discovery.close()

    val httpPort = sys.env.get("PORT") match { // internal port has to be set to 8080 on CC
      case None => 8080
      case Some(port) => port.toInt
    }

    router.route("/api/yo").handler(context => {
      context
        .response()
        .putHeader("content-type", "application/json;charset=UTF-8")
        .end(new JsonObject().put("message", "Yo 😃").encodePrettily())
    })

    router.route("/api/hi").handler(context => {
      context
        .response()
        .putHeader("content-type", "application/json;charset=UTF-8")
        .end(new JsonObject().put("message", "Hi 🐼").encodePrettily())
    })

    router.route("/").handler(context => {
      context
        .response()
        .putHeader("content-type", "text/html;charset=UTF-8")
        .end("<h1>Hello 🌍</h1>")
    })

    println(s"🌍 Listening on $httpPort - Enjoy 😄")
    server.requestHandler(router.accept _).listen(httpPort)

  }
}