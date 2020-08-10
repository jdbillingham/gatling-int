package openam.authntree

import io.gatling.core.Predef._
import scala.concurrent.duration._
class getEvaluatePolicy extends AuthNTreeSimulation {
  val admin_id = System.getProperty("admin_id", "amadmin")
  val admin_pwd = System.getProperty("admin_pwd", "password")

  val scn = scenario("evaluatePolicy")
    .exec(restLogin(admin_id, admin_pwd))
    .during(duration) {
          exec(evaluatePolicy("https://login.test.kubernetes.org.uk/web/sensitive/","${tokenId}"))
          .pause(pause seconds)
        }
  setUp(scn.inject(rampUsers(concurrency) during (warmup seconds))).protocols(httpProtocol).exponentialPauses
}
