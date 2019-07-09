package openam.authntree

import io.gatling.core.Predef._
import scala.concurrent.duration._
class getEvaluatePolicy extends AuthNTreeSimulation {
  val admin_id = System.getProperty("admin_id", "amadmin")
  val admin_pwd = System.getProperty("admin_pwd", "password")

  val scn = scenario("evaluatePolicy")
    .during(duration) {
      feed(userFeeder)
        .exec(usernamePasswordRestLoginPageNode(admin_id, admin_pwd, realm))
        .repeat(100) {
          exec(evaluatePolicy("https://login.test.kubernetes.org.uk/web/sensitive/","${tokenId}"))
          .pause(pause seconds)
        }
        .doIf(session => logoutPercent.>(0)) {
          randomSwitch {
            logoutPercent -> restLogout()
          }
        }
    }
  setUp(scn.inject(rampUsers(concurrency) during (warmup seconds))).protocols(httpProtocol).exponentialPauses

}
