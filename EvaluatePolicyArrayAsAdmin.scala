package openam.authntree

import io.gatling.core.Predef._
import scala.concurrent.duration._
class getEvaluatePolicyMultipleResources extends AuthNTreeSimulation {
  val admin_id = System.getProperty("admin_id", "amadmin")
  val admin_pwd = System.getProperty("admin_pwd", "password")
  val resources = Array("shipment:4567=SFO",
        "shipment:4567=SFO",
        "shipment:4567=SFO",
        "shipment:4567=SFO",
        "shipment:4567=SFO",
        "shipment:4567=SFO",
        "shipment:4567=SFO",
        "shipment:4567=SFO",
        "shipment:4567=SFO",
        "shipment:4567=SFO",
        "shipment:4567=SFO",
        "shipment:4567=SFO",
        "shipment:4567=SFO",
        "shipment:4567=SFO",
        "shipment:4567=SFO",
        "shipment:4567=SFO",
        "shipment:4567=SFO",
        "shipment:4567=SFO",
        "shipment:4567=SFO",
        "shipment:4567=SFO",
        "shipment:4567=SFO",
        "shipment:4567=SFO",
        "shipment:4567=SFO",
        "shipment:4567=SFO",
        "shipment:4567=SFO",
        "shipment:4567=SFO",
        "shipment:4567=SFO",
        "shipment:4567=SFO",
        "shipment:4567=SFO",
        "shipment:4567=SFO",
        "shipment:4567=SFO",
        "shipment:4567=SFO",
        "shipment:4567=SFO",
        "shipment:4567=SFO",
        "shipment:4567=SFO",
        "shipment:4567=SFO",
        "shipment:4567=SFO",
        "shipment:4567=SFO",
        "shipment:4567=SFO",
        "shipment:4567=SFO",
        "shipment:4567=SFO",
        "shipment:4567=SFO",
        "shipment:4567=SFO",
        "shipment:4567=SFO",
        "shipment:4567=SFO",
        "shipment:4567=SFO",
        "shipment:4567=SFO",
        "shipment:4567=SFO",
        "shipment:4567=HKG",
        "shipment:1234=LAX")

  val scn = scenario("evaluatePolicy")
    .exec(restLogin(admin_id, admin_pwd))
    .during(duration) {
          exec(evaluatePolicyMultipleResources(resources,"${tokenId}"))
          .pause(pause seconds)
        }
  setUp(scn.inject(rampUsers(concurrency) during (warmup seconds))).protocols(httpProtocol).exponentialPauses
}
