package openam.authntree

import io.gatling.core.Predef._
import scala.concurrent.duration._
class mix extends AuthNTreeSimulation {

  val scn = scenario("mix")
    .feed(userFeeder)
      .exec(usernamePasswordRestLoginPageNode("${username}", "${password}", realm))
    .during(duration) {
          exec(evaluatePolicy("https://login.test.kubernetes.org.uk/web/sensitive/","${tokenId}"))
          .exec(validateSession("${tokenId}"))
          .exec(getSessionProperty("AMCtxId","saveas","${tokenId}"))
        .doIf(session => logoutPercent.>(0)) {
          randomSwitch {
            logoutPercent -> restLogout()
          }
        }
    }
  setUp(scn.inject(rampUsers(concurrency) during (warmup seconds))).protocols(httpProtocol).exponentialPauses

}
