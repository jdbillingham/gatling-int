package openam.authntree

import io.gatling.core.Predef._
import scala.concurrent.duration._
class getAuthN extends AuthNTreeSimulation {

  val scn = scenario("getSessionInfo")
    .during(duration) {
      feed(userFeeder)
        .exec(usernamePasswordRestLoginPageNode("${username}", "${password}", realm))
        .doIf(session => logoutPercent.>(0)) {
          randomSwitch {
            logoutPercent -> restLogout()
          }
        }
    }
  setUp(scn.inject(rampUsers(concurrency) during (warmup seconds))).protocols(httpProtocol).exponentialPauses

}
