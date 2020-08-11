package openam.authntree

import io.gatling.core.Predef._
import scala.concurrent.duration._
class modUserPassword extends AuthNTreeSimulation {

  val scn = scenario("modUserPassword")
      .during(duration) {
        feed(userFeeder)
          .exec(restLogin("${username}", "${password}"))
          .repeat(loop) {
            exec(updateUser("${username}", realm))
            .pause(pause seconds)
          }
      }

  setUp(scn.inject(rampUsers(concurrency) during (warmup seconds))).protocols(httpProtocol).exponentialPauses
}
