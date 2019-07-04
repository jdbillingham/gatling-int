package openam.authntree

import openam.OpenAMSimulation
import io.gatling.core.structure.ChainBuilder
import io.gatling.core.Predef._
import io.gatling.core.body.StringBody
import io.gatling.core.session
import io.gatling.http.Predef._
import io.gatling.http.check.HttpCheck

import scala.collection.mutable

trait AuthNTreeSimulation extends OpenAMSimulation {

  /**
    * Login using the REST API in a multi-stage way. Initially the user is queried to provide their username
    * then their password.
    *
    * @param username         the username used for authentication
    * @param password         the password used for authentication
    * @param realm            the realm used for authentication (default: /)
    * @param requestName      the name of the request to be used in the report (default: RestLogin)
    * @param setCookie        if true set the iPlanetDirectoryPro cookie with the token value (default: true)
    * @param root_url         optional prefix url for authentication endpoint
    * @param tokenid_var_name the name of the session variable to store the tokenid value (default: "tokenid")
    * @return chainBuilder instance to be executed in the scenario
    */
  def multiStageRestLogin(username: String, password: String, realm: String = "/", requestName: String = "multiStageRestLogin",
                          setCookie: Boolean = true,
                          root_url: String = "", tokenid_var_name: String = "tokenid"): ChainBuilder = {

    val url = getJsonURL(category = "authenticate", realm = realm, root_url = root_url)
    val queryParamMap: mutable.Map[String, Any] = mutable.Map[String, Any]()
    queryParamMap.put("authIndexType", "service")
    queryParamMap.put("authIndexValue", service_name)
    if ((resource_version >= "2.0") && (realm != "/")) {
      queryParamMap.put("realm", realm)
    }
    println(url)

    exec(flushCookieJar)
      .exec(restLoginInitiate(queryParamMap, url))
      .pause(pause seconds)
      .exec(restLoginUsernameCallback(authId = "${authId}", username, queryParamMap, url))
      .pause(pause seconds)
      .exec(restLoginPasswordCallback(authId = "${authId}", password, queryParamMap, url))
      .pause(pause seconds)
      .exitHereIfFailed
  }

  /**
    * Login using the REST API in a single-stage way. The user is queried to provide their username
    * and their password.
    *
    * @param username         the username used for authentication
    * @param password         the password used for authentication
    * @param realm            the realm used for authentication (default: /)
    * @param requestName      the name of the request to be used in the report (default: RestLogin)
    * @param setCookie        if true set the iPlanetDirectoryPro cookie with the token value (default: true)
    * @param root_url         optional prefix url for authentication endpoint
    * @param tokenid_var_name the name of the session variable to store the tokenid value (default: "tokenid")
    * @return chainBuilder instance to be executed in the scenario
    */
  def usernamePasswordRestLogin(username: String, password: String, realm: String = "/", requestName: String = "usernamePasswordRestLogin",
                                setCookie: Boolean = true,
                                root_url: String = "", tokenid_var_name: String = "tokenid"): ChainBuilder = {

    val url = getJsonURL(category = "authenticate", realm = realm, root_url = root_url)
    val queryParamMap: mutable.Map[String, Any] = mutable.Map[String, Any]()
    queryParamMap.put("authIndexType", "service")
    queryParamMap.put("authIndexValue", service_name)
    if ((resource_version >= "2.0") && (realm != "/")) {
      queryParamMap.put("realm", realm)
    }

    exec(flushCookieJar)
      .exec(restLoginInitiate(queryParamMap, url))
      .pause(pause seconds)
      .exec(restLoginUsernamePasswordCallback(authId = "${authId}", username, password, queryParamMap, url))
      .pause(pause seconds)
      .exitHereIfFailed
  }

  /**
    * Login using the REST API in a single page. The user is queried to provide their username
    * and their password.
    *
    * @param username         the username used for authentication
    * @param password         the password used for authentication
    * @param realm            the realm used for authentication (default: /)
    * @param requestName      the name of the request to be used in the report (default: RestLogin)
    * @param setCookie        if true set the iPlanetDirectoryPro cookie with the token value (default: true)
    * @param root_url         optional prefix url for authentication endpoint
    * @param tokenid_var_name the name of the session variable to store the tokenid value (default: "tokenid")
    * @return chainBuilder instance to be executed in the scenario
    */
  def usernamePasswordRestLoginPageNode(username: String, password: String, realm: String = "/", requestName: String = "usernamePasswordRestLoginPageNode",
                                setCookie: Boolean = true,
                                root_url: String = "", tokenid_var_name: String = "tokenid"): ChainBuilder = {

    val url = getJsonURL(category = "authenticate", realm = realm, root_url = root_url)
    val queryParamMap: mutable.Map[String, Any] = mutable.Map[String, Any]()
    queryParamMap.put("authIndexType", "service")
    queryParamMap.put("authIndexValue", service_name)
    if ((resource_version >= "2.0") && (realm != "/")) {
      queryParamMap.put("realm", realm)
    }

    exec(flushCookieJar)
      .exec(restLoginInitiate(queryParamMap, url))
      .pause(pause seconds)
      .exec(restLoginUsernamePasswordPageNodeCallback(authId = "${authId}", username, password, queryParamMap, url))
      .pause(pause seconds)
      .exitHereIfFailed
  }

  /**
    * Makes a single POST to the authentication endpoint, initiating but not progressing the authentication flow
    *
    * @param queryParamMap         the query parameters to be added to the POST
    * @param url                   the url to submit the POST to
    * @param requestName           the name of the request to be used in the report (default: RestLogin)
    * @return chainBuilder         instance to be executed in the scenario
    */
  def restLoginInitiate(queryParamMap: mutable.Map[String, Any], url: String, requestName: String = "restLoginInitiate"): ChainBuilder = {

    exec(
      http(requestName)
        .post(url)
        .queryParamMap(queryParamMap.toMap)
        .asJson
        .body(StringBody(""))
        .headers(acceptAPIVersion)
        .check(status.is(200))
        .check(jsonPath("$.authId").find.saveAs("authId"))
    )
  }

  /**
    * Makes a single POST to the authentication endpoint, providing the username of the authenticating user
    *
    * @param authId                the authID of this authentication session
    * @param username              the username used for authentication
    * @param queryParamMap         the query parameters to be added to the POST
    * @param url                   the url to submit the POST to
    * @param requestName           the name of the request to be used in the report (default: RestLogin)
    * @return chainBuilder         instance to be executed in the scenario
    */
  def restLoginUsernameCallback(authId: String, username: String, queryParamMap: mutable.Map[String, Any], url: String,
                                requestName: String = "restLoginUsernameCallback"): ChainBuilder = {

    exec(http(requestName)
      .post(url)
      .queryParamMap(queryParamMap.toMap)
      .headers(acceptAPIVersion)
      .asJson
      .body(StringBody(
        """{"authId":"%s",""".format(authId) +
          """"callbacks":[{"type":"NameCallback","output":[{"name":"prompt","value":"User Name:"}],""" +
          """"input":[{"name":"IDToken1","value":"%s"}]}]}""".format(username)
      ))
      .check(status.is(200))
      .check(jsonPath("$.authId").find.saveAs("authId"))
    )
  }

  /**
    * Makes a single POST to the authentication endpoint, providing the password of the authenticating user
    *
    * @param authId                the authID of this authentication session
    * @param password              the password used for authentication
    * @param queryParamMap         the query parameters to be added to the POST
    * @param url                   the url to submit the POST to
    * @param requestName           the name of the request to be used in the report (default: RestLogin)
    * @return chainBuilder instance to be executed in the scenario
    */
  def restLoginPasswordCallback(authId: String, password: String, queryParamMap: mutable.Map[String, Any], url: String,
                                requestName: String = "restLoginPasswordCallback"): ChainBuilder = {

    exec(http(requestName)
      .post(url)
      .queryParamMap(queryParamMap.toMap)
      .headers(acceptAPIVersion)
      .asJson
      .body(StringBody(
        """{"authId":"%s",""".format(authId) +
          """"callbacks":[{"type":"PasswordCallback",""" +
          """"output":[{"name":"prompt","value":"Password:"}],"input":[{"name":"IDToken2","value":"%s"}]}]}""".format(password)
      ))
      .check(status.is(200))
      .check(jsonPath("$.tokenId").find.saveAs("tokenId"))
    )
      .exec(addCookie(Cookie("iPlanetDirectoryPro", "${tokenId}")))
  }

  /**
    * Makes a single POST to the endpoint using an authentication chain, providing the username and
    * password of the authenticating user
    *
    * @param authId                the authID of this authentication session
    * @param username              the username used for authentication
    * @param queryParamMap         the query parameters to be added to the POST
    * @param url                   the url to submit the POST to
    * @param requestName           the name of the request to be used in the report (default: RestLogin)
    * @return chainBuilder         instance to be executed in the scenario
    */
  def restLoginUsernamePasswordCallback(authId: String, username: String, password: String, queryParamMap: mutable.Map[String, Any], url: String,
                                        requestName: String = "restLoginUsernamePasswordCallback"): ChainBuilder = {

    exec(http(requestName)
      .post(url)
      .queryParamMap(queryParamMap.toMap)
      .headers(acceptAPIVersion)
      .asJson
      .body(StringBody(
        """{"authId":"%s",""".format(authId) +
          """"callbacks":[{"type": "NameCallback",""" +
          """"output": [{"name": "prompt","value": "User Name:"}],"input": [{"name": "IDToken1","value": "%s"}]},""".format(username) +
          """{"type": "PasswordCallback",""" +
          """"output": [{"name": "prompt","value": "Password:"}],"input": [{"name": "IDToken2","value": "%s"}]}]}""".format(password)
      ))
      .check(status.is(200))
      .check(jsonPath("$.tokenId").find.saveAs("tokenId"))
    )
      .exec(addCookie(Cookie("iPlanetDirectoryPro", "${tokenId}")))
  }

  /**
    * Makes a single POST to the endpoint using an authentication tree with a page node providing the username and
    * password of the authenticating user
    *
    * @param authId                the authID of this authentication session
    * @param username              the username used for authentication
    * @param queryParamMap         the query parameters to be added to the POST
    * @param url                   the url to submit the POST to
    * @param requestName           the name of the request to be used in the report (default: RestLogin)
    * @return chainBuilder         instance to be executed in the scenario
    */
  def restLoginUsernamePasswordPageNodeCallback(authId: String, username: String, password: String, queryParamMap: mutable.Map[String, Any], url: String,
                                        requestName: String = "restLoginUsernamePasswordPageNodeCallback"): ChainBuilder = {

    exec(http(requestName)
      .post(url)
      .queryParamMap(queryParamMap.toMap)
      .headers(acceptAPIVersion)
      .asJson
      .body(StringBody(
        """{"authId":"%s",""".format(authId) +
          """"callbacks":[{"type": "NameCallback",""" +
          """"output": [{"name": "prompt","value": "User Name:"}],"input": [{"name": "IDToken1","value": "%s"}],"_id":0},""".format(username) +
          """{"type": "PasswordCallback",""" +
          """"output": [{"name": "prompt","value": "Password:"}],"input": [{"name": "IDToken2","value": "%s"}],"_id":1}]}""".format(password)
      ))
      .check(status.is(200))
      .check(jsonPath("$.tokenId").find.saveAs("tokenId"))
    )
      .exec(addCookie(Cookie("iPlanetDirectoryPro", "${tokenId}")))
  }
}
