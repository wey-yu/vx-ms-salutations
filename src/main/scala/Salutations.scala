
import io.vertx.core.json.JsonObject
import io.vertx.scala.core.Vertx
import io.vertx.scala.ext.web.Router
import io.vertx.scala.servicediscovery.types.HttpEndpoint
import io.vertx.scala.servicediscovery.{ServiceDiscovery, ServiceDiscoveryOptions}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.{Failure, Success}

object Salutations {

  val vertx = Vertx.vertx()

  def discovery = {
    // Settings for the Redis backend
    val redisHost = sys.env.get("REDIS_HOST").getOrElse("127.0.0.1")
    val redisPort = sys.env.get("REDIS_PORT").getOrElse("6379").toInt
    val redisAuth = sys.env.get("REDIS_PASSWORD").getOrElse(null)
    val redisRecordsKey = sys.env.get("REDIS_RECORDS_KEY").getOrElse("scala-records")

    // Mount the service discovery backend (Redis)
    val discovery = ServiceDiscovery.create(vertx, ServiceDiscoveryOptions()
      .setBackendConfiguration(
        new JsonObject()
          .put("host", redisHost)
          .put("port", redisPort)
          .put("auth", redisAuth)
          .put("key", redisRecordsKey)
      )
    )

    // Settings for record the service
    val serviceName = sys.env.get("SERVICE_NAME").getOrElse("hello")
    //val serviceHost = sys.env.get("SERVICE_HOST").getOrElse("localhost") // domain name
    //val servicePort = sys.env.get("SERVICE_PORT").getOrElse("8080").toInt // set to 80 on Clever Cloud
    val serviceRoot = sys.env.get("SERVICE_ROOT").getOrElse("/api")

    /* tips to set automatically host and port */
    val serviceHost = sys.env.get("SERVICE_HOST") match {
      case None => {
        sys.env.get("APP_ID")  match {
          case None => "localhost"
          case Some(value) => value.replace("_", "-") + ".cleverapps.io" // get the generated domain name
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
    
    // create the microservice record
    val record = HttpEndpoint.createRecord(
      serviceName,
      serviceHost,
      servicePort,
      serviceRoot
    )
    // add meta data for details ... if you want, eg if you have several routes
    record.setMetadata(
      new JsonObject()
        .put(
          "services",
          new JsonArray().add("/yo").add("/hi")
        )
      )

    // search the service before?

    discovery.publishFuture(record).onComplete{
      case Success(result) => println(s"😃 publication OK")
      case Failure(cause) => println(s"😡 publication KO: $cause")
    }
    // TODO: retry when failure
    // discovery.close() // or not
  }

  def main(args: Array[String]): Unit = {

    val server = vertx.createHttpServer()
    val router = Router.router(vertx)

    // use redis backend to publish service informations
    discovery

    val httpPort = sys.env.get("PORT").getOrElse("8080").toInt

    // my services
    router.get("/api/yo").handler(context => {
      context
        .response()
        .putHeader("content-type", "application/json;charset=UTF-8")
        .end(new JsonObject().put("message", "Yo 🐻").encodePrettily())
    })

    router.get("/api/hi").handler(context => {
      context
        .response()
        .putHeader("content-type", "application/json;charset=UTF-8")
        .end(new JsonObject().put("message", "Hi 🐼").encodePrettily())
    })

    // home page
    router.get("/").handler(context => {
      context
        .response()
        .putHeader("content-type", "text/html;charset=UTF-8")
        .end("<h1>Hello 🌍</h1>")
    })

    println(s"🌍 Listening on  - Enjoy 😄")
    server.requestHandler(router.accept _).listen(httpPort)
  }
}
