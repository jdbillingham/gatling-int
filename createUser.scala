package openam.authntree

import io.gatling.core.Predef._
import scala.concurrent.duration._
import io.gatling.commons.validation._

class createUserAsAdmin extends AuthNTreeSimulation {

  val scn = scenario("createUserAsAdmin")
        .exec(restLogin("amadmin", "4b1bofm3awguqq11gb0ero3hzhkhatcj"))
        .repeat(loop) {
        feed(userFeeder)
            .exec(writeUser("Z${username}", realm))
        }

  setUp(scn.inject(rampUsers(concurrency) during (warmup seconds))).protocols(httpProtocol).exponentialPauses
}
