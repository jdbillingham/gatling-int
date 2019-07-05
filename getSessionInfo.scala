package openam.authntree

import io.gatling.core.Predef._
import scala.concurrent.duration._
class getSessionInfo extends AuthNTreeSimulation {

  val scn = scenario("getSessionInfo")
    .during(duration) {
      feed(userFeeder)
        .exec(usernamePasswordRestLoginPageNode("${username}", "${password}", realm))
        .pause(pause seconds)
        .repeat(100) {
          exec(getSessionInfo("",""))
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
