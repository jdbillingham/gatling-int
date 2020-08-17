package openam.oauth2

import io.gatling.core.structure.ChainBuilder
import io.gatling.core.Predef._
import io.gatling.core.session
import io.gatling.http.Predef._
import java.time.Clock
import openam.OpenAMSimulation
//import pdi.jwt.Jwt
//import pdi.jwt.JwtAlgorithm
//import pdi.jwt.JwtClaim

trait OAuth2Simulation extends OpenAMSimulation {
  val redirect_uri = System.getProperty("redirect_uri", "http://fake.com")
  val audience = System.getProperty("audience", "http://fake.com")
  val oauth2_id = System.getProperty("oauth2_id", "clientOIDC_0")
  val oauth2_pwd = System.getProperty("oauth2_pwd", "password")
  // TODO: use a random state each time ?
  val state = System.getProperty("state", "1234")
  val scope = System.getProperty("scope", "openid profile email")
  val claims = System.getProperty("claims", "")
  val x_key = System.getProperty("x_key", "1")
  val cnf_key = System.getProperty("cnf_key", "1")
  val req_param = System.getProperty("req_param", "")
  val num_clients = Integer.getInteger("num_clients", 1)

  val clientFeeder = Iterator.continually(Map(
    """oauth2_id""" -> ("""clientOIDC_""" + random.nextInt(num_clients).toString)))

  /**
    * ******************************************************
    * OAuth2 / OIDC
    * ******************************************************
    */

  /**
    * Generate an Oauth2 authorization_code using OpenAM oauth2/authorize endpoint
    *
    * @param code_var_name the name of the session variable where the authorization code will be stored
    * @return chainBuilder instance to be executed in the scenario
    */
  def authorize(oauth2_id: String, redirect_uri: String, scope: String = "profile openid", realm: String = "/",
                code_var_name: String = "authorization_code", tokenid_var_name: String = "tokenid"): ChainBuilder = {
    // TODO: Generate a random state ?
    val state = 1234
    exec(http("Authorize")
      .post("/am/oauth2/authorize")
      .queryParam("client_id", oauth2_id)
      .queryParam("scope", scope)
      .queryParam("state", state)
      .queryParam("redirect_uri", redirect_uri)
      .queryParam("response_type", "code")
      .queryParam("realm", realm)
      .formParam("decision", "Allow")
      // TODO: See OPENAM-8575. Assume that tokenid var is present in the session for now
      .formParam("csrf", "${%s}".format(tokenid_var_name))
      .disableFollowRedirect
      .check(headerRegex("Location", """code=([^&\s]*)&?""")
        .saveAs(code_var_name))
      .check(status.is(302)))
  }

  /**
    * Generate an Oauth2 token and refresh token using OpenAM oauth2/access_token endpoint
    * with grant_type=autorization=code
    *
    * @param code the authorization_code to be used for generating the token
    * @return chainBuilder instance to be executed in the scenario
    */
  def accessToken(code: String, oauth2_id: String, oauth2_pwd: String, redirect_uri: String, realm: String = "/"):
  ChainBuilder = {
    exec(http("AccessToken")
      .post("/am/oauth2/access_token")
      .queryParam("realm", realm)
      .formParam("grant_type", "authorization_code")
      .formParam("code", code)
      .formParam("redirect_uri", redirect_uri)
      .basicAuth(oauth2_id, oauth2_pwd)
      .check(jsonPath("$.access_token").find.saveAs("access_token"))
      .check(jsonPath("$.refresh_token").find.saveAs("refresh_token"))
    )
  }

  // TODO: merge it with the previous one
  /**
    * Generate an Oauth2 access_token and id_token using OpenAM oauth2/access_token endpoint
    * with grant_type=autorization_code
    *
    * @param code the authorization_code to be used for generating the token
    * @return chainBuilder instance to be executed in the scenario
    */
  def accessToken2(code: String, oauth2_id: String, oauth2_pwd: String, redirect_uri: String, realm: String = "/",
                   id_token_var_name: String = "id_token"):
  ChainBuilder = {
    exec(http("AccessToken")
      .post("/am/oauth2/access_token")
      .queryParam("realm", realm)
      .formParam("grant_type", "authorization_code")
      .formParam("code", code)
      .formParam("redirect_uri", redirect_uri)
      .basicAuth(oauth2_id, oauth2_pwd)
      .header("Cache-Control", "no-cache")
      .check(jsonPath("$.access_token").find.saveAs("access_token"))
      //.check(jsonPath("$.id_token").find.saveAs(id_token_var_name))
    )
  }

  /**
    * Generates access token by passing additional base64 encoded Json web key
    *
    * @param code
    * @param oauth2_id
    * @param oauth2_pwd
    * @param redirect_uri
    * @param realm
    * @param cnf_key
    * @param id_token_var_name
    * @return
    */
  def accessTokenPoP(code: String, oauth2_id: String, oauth2_pwd: String, redirect_uri: String, realm: String = "/",
                     cnf_key: String, id_token_var_name: String = "id_token"):
  ChainBuilder = {
    exec(http("AccessToken")
      .post("/openam/oauth2/access_token")
      .queryParam("realm", realm)
      .formParam("grant_type", "authorization_code")
      .formParam("code", code)
      .formParam("redirect_uri", redirect_uri)
      .formParam("cnf_key", cnf_key)
      .basicAuth(oauth2_id, oauth2_pwd)
      .check(jsonPath("$.access_token").find.saveAs("access_token"))
      .check(jsonPath("$.id_token").find.saveAs(id_token_var_name))
    )
  }

  /**
    * Get oauth2 token info (and therefore validate it) using OpenAM REST api
    *
    * @param access_token the access_token id
    * @return chainBuilder instance to be executed in the scenario
    */
  def tokenInfo(access_token: String): ChainBuilder = {
    exec(http("TokenInfo")
      .get("/am/oauth2/tokeninfo")
      .queryParam("access_token", access_token)
    )
  }

  /**
    * Validate cnf key from token info endpoint reponse
    *
    * @param access_token
    * @param x_key
    * @return checks x_key from cnf_key present in response body
    */
  def tokenInfo_Pop(access_token: String, x_key: String): ChainBuilder = {
    exec(http("TokenInfoPoP")
      .get("/am/oauth2/tokeninfo")
      .queryParam("access_token", access_token)
      .check(regex(x_key).exists)
      .check(jsonPath("$.access_token").find.saveAs("access_token"))
    )
  }

  /**
    * Get oauth2 id token info using OpenAM REST api
    *
    * @param id_token the id_token
    * @return chainBuilder instance to be executed in the scenario
    */
  def idTokenInfo(id_token: String): ChainBuilder = {
    exec(http("IdTokenInfo")
      .post("/am/oauth2/idtokeninfo")
      .basicAuth(oauth2_id, oauth2_pwd)
      .formParam("id_token", id_token))
  }

  /**
    * Get oauth2 user info using OpenAM REST api
    *
    * @param access_token the access_token id
    * @return chainBuilder instance to be executed in the scenario
    */
  def userInfo(access_token: String, oauth2_id: String, realm: String = "/"): ChainBuilder = {
    val value = "Bearer %s".format(access_token)
    exec(http("UserInfo")
      .get("/openam/oauth2/userinfo")
      .header("Authorization", value)
      //      .queryParam("client_id", oauth2_id)
      .queryParam("realm", realm))
  }

  /**
    * Refresh an oauth2 token OpenAM REST api
    *
    * @param refresh_token thre refresh_token id
    * @return chainBuilder instance to be executed in the scenario
    */
  def refreshToken(refresh_token: String, oauth2_id: String, oauth2_pwd: String, realm: String = "/"): ChainBuilder = {
    exec(http("RefreshToken")
      .post("/openam/oauth2/access_token")
      .queryParam("realm", realm)
      .formParam("grant_type", "refresh_token")
      .formParam("refresh_token", "${refresh_token}")
      .basicAuth(oauth2_id, oauth2_pwd)
      .check(jsonPath("$.access_token").find.saveAs("access_token")))
  }

  def getRevokeURL(access_token: String): session.Expression[String] = {
    val url = "/openam/frrest/oauth2/token/" + access_token
    url
  }

  /**
    * Revoke an oauth2 token using OpenAM REST api
    *
    * @param access_token the access_token id
    * @return chainBuilder instance to be executed in the scenario
    */
  def revokeToken(access_token: String): ChainBuilder = {
    exec(http("RevokeToken")
      .delete(getRevokeURL(access_token))
      .header("Content-Type", "application/json"))
  }


  def getNewRevokeURL(realm: String): session.Expression[String] = {
    val url = "/openam/oauth2/" + realm + "/token/revoke"
    url
  }

  /**
    * Revoke an oauth2 token using OpenAM REST api
    *
    * @param token the access_token id
    * @return chainBuilder instance to be executed in the scenario
    */
  def revokeToken2(token: String, oauth2_id: String, oauth2_pwd: String, realm: String): ChainBuilder = {
    exec(http("RevokeToken2")
      .post(getNewRevokeURL(realm))
      .queryParam("token", token)
      .queryParam("client_id", oauth2_id)
      .queryParam("client_secret", oauth2_pwd))
  }

  // TODO: keep it ?
  /**
    * Close current connection (kind of a hack ?)
    *
    * @return chainBuilder instance to be executed in the scenario
    */
  def closeConnection: ChainBuilder = {
    exec(http("CloseConnection")
      .get("/")
      .header("Connection", "close"))
  }
}
