package openam.authntree

import io.gatling.core.Predef._
import scala.concurrent.duration._
class getUserAsAdmin extends AuthNTreeSimulation {

val admin_id = System.getProperty("admin_id", "amadmin")
val admin_pwd = System.getProperty("admin_pwd", "password")

  val scn = scenario("getUserAsAdmin")
      .exec(usernamePasswordRestLoginPageNode(admin_id, admin_pwd, realm))
      //.exec(restLogin(admin_id, admin_pwd))
      .during(duration) {
        feed(userFeeder)
        .exec(readUser("${username}", realm))
        .pause(pause seconds)
      }

  setUp(scn.inject(rampUsers(concurrency) during (warmup seconds))).protocols(httpProtocol).exponentialPauses
}
