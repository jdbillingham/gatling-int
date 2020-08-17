package openam.oauth2

import io.gatling.core.Predef._

class Authorize extends OAuth2Simulation {

  val scn = scenario("Authorize")
    // request a new access_token
    .during(duration) {
      .feed(userFeeder)
      .exec(restLogin("${username}", "${password}"))
      .exec(authorize(oauth2_id, redirect_uri, scope, realm, "authorization_code"))
      .exec(accessToken2("${authorization_code}", oauth2_id, oauth2_pwd, redirect_uri, realm))
      .repeat(loop) {
        tokenInfo("${access_token}")
      }
      .pause(pause seconds)
    }

  setUp(scn.inject(rampUsers(concurrency) during (warmup seconds))).protocols(httpProtocol).exponentialPauses
}
