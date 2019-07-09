package openam.authntree

import io.gatling.core.Predef._
import scala.concurrent.duration._
class getEvaluatePolicy extends AuthNTreeSimulation {

  val scn = scenario("evaluatePolicy")
    .during(duration) {
      feed(userFeeder)
        .exec(usernamePasswordRestLoginPageNode("amadmin", "Intu1tP0C$&@", realm))
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
