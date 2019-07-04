package openam.authntree

import io.gatling.core.Predef._
import scala.concurrent.duration._

class RestLoginMultiStage extends AuthNTreeSimulation {

  val scn = scenario("AuthNTree-SIWRS1")
    .during(duration) {
      feed(userFeeder)
        .exec(multiStageRestLogin("${username}", "${password}", realm))
        .doIf(session => logoutPercent.>(0)) {
          randomSwitch {
            logoutPercent -> restLogout()
          }
        }
    }
    setUp(scn.inject(rampUsers(concurrency) during (warmup seconds))).protocols(httpProtocol).exponentialPauses
}
