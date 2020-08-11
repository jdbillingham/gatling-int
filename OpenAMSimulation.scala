package openam

import io.gatling.core.Predef._
import io.gatling.core.body.StringBody
import io.gatling.core.session
import io.gatling.core.structure.ChainBuilder
import io.gatling.http.Predef._
import io.gatling.http.check.HttpCheck
import io.gatling.http.protocol.HttpProtocolBuilder
import io.gatling.http.request.builder.HttpRequestBuilder
import scala.concurrent.duration._

import scala.collection.mutable

trait OpenAMSimulation extends Simulation {
  val userPoolSize = Integer.getInteger("users", 10000000)
  val concurrency = Integer.getInteger("concurrency", 10)
  val duration = Integer.getInteger("duration", 600)
  val warmup = Integer.getInteger("warmup", 10)
  val pause = Integer.getInteger("pause", 10)
  val logoutPercent = Integer.getInteger("logoutPercent", 0).toDouble
  val loginPercent = Integer.getInteger("loginPercent", 100).toDouble
  val am_host = System.getProperty("am_host", "login.test.kubernetes.org.uk")
  val am_port = System.getProperty("am_port", "443")
  val am_protocol = System.getProperty("am_protocol", "https")
  val am_url = am_protocol + "://" + am_host + ":" + am_port
  val realm = System.getProperty("realm", "/")
  val application = System.getProperty("application", "iPlanetAMWebAgentService")
  val am_version = System.getProperty("am_version", "14.0.0")
  val resource_version = System.getProperty("resource_version", "2.0")
  val use_trees = System.getProperty("use_trees", "false")
  val openam_prefix = System.getProperty("openam_prefix", "")
  val session_uid_generated = System.getProperty("session_uid_generated", "false").toBoolean
  val random = new util.Random
  val service_name = System.getProperty("service_name", "adminconsoleservice")
  val loop = Integer.getInteger("loop", 1).toInt

  val userFeeder = Iterator.continually(Map(
    """username""" -> ("""user.""" + random.nextInt(userPoolSize).toString),
    """password""" -> "password")
  )

  /**
    * An abstract super class (i.e. with no simulation) to provide methods to easily build load for OpenAM
    */
  def getHttpProtocol(protocol: String, host: String, port: String, caching: String = "false"): HttpProtocolBuilder = {
    var httpProtocol = http.baseUrl(protocol +"://" + host + ":" + port)
      .contentTypeHeader("application/x-www-form-urlencoded")
      .inferHtmlResources()
      .acceptHeader( """*/*""")
      .wsBaseUrl("ws://" + host + ":" + port)
      .shareConnections
    if (caching == "false") {
      httpProtocol = httpProtocol.disableCaching
    }
    httpProtocol
  }

  val httpProtocol = getHttpProtocol(am_protocol, am_host, am_port)

  def getXOpenAMHeaders(username: String, password: String): scala.collection.immutable.Map[String, String] = {
    scala.collection.immutable.Map(
      "X-OpenAM-Username" -> username,
      "X-OpenAM-Password" -> password)
  }

  def getAcceptAPIVersion(resource_version: String = resource_version): scala.collection.immutable.Map[String, String] = {
    val value = "protocol=1.0,resource=" + resource_version
    scala.collection.immutable.Map(
      "Accept-API-Version" -> value
    )
  }

  // default header value
  val acceptAPIVersion = getAcceptAPIVersion()

  def realm2url(realm: String): String = {
    var realm2url = realm
    if (!realm2url.startsWith("/"))
      realm2url = "/" + realm2url
    if (!realm2url.endsWith("/"))
      realm2url += "/"
    realm2url
  }

  /**
    *
    * @param category end point category (i.e. users, authenticate, policies...)
    * @param realm    the realm of the url
    * @param root_url (optional) root_url to be added as a prefix to the url
    * @return the url of the REST endpoint
    */
  def getJsonURL(category: String, resourceName: String = "", realm: String = "/",
                 resource_version: String = resource_version, root_url: String = ""): String = {
    var url = root_url
    var _resourceName = resourceName
    if (resource_version < "2.0") {
      if (openam_prefix == "") {
        url += "/json%s%s".format(realm2url(realm), category)
      } else {
        url += "/"+openam_prefix+"/json%s%s".format(realm2url(realm), category)
      }
    } else {
        if (openam_prefix == "") {
          url += "/json/%s".format(category)
        } else {
          url += "/"+openam_prefix+"/json%s%s".format(realm2url(realm), category)
        }
      if (category == "sessions") {
        _resourceName = ""
      }
    }
    if (_resourceName != "") {
      url += "/" + _resourceName
    }
    url
  }

  /**
    * Read a resource using the REST API
    *
    * @param requestName  the name of the request to be used for the report
    * @param category     resource category such as users, sessions
    * @param resourceName the name of the resource
    * @param realm        the real (default: /)
    * @param root_url     an optional prefix to be added to the url
    * @return
    */
  def jsonRead(requestName: String, category: String, resourceName: String, realm: String = "/",
               resource_version: String = resource_version, root_url: String = ""): HttpRequestBuilder = {
    val url = getJsonURL(category = category, resourceName = resourceName, realm = realm,
      resource_version = resource_version, root_url = root_url)
    val queryParamMap: mutable.Map[String, Any] = mutable.Map[String, Any]()
    if (resource_version >= "2.0") {
      //queryParamMap.put("realm", realm)
    }
    http(requestName)
      .get(url)
      .queryParamMap(queryParamMap.toMap)
      .headers(getAcceptAPIVersion(resource_version))
  }

  /**
    * Update a resource using the REST API - PATCH
    * Hard coded to update password
    * @param requestName  the name of the request to be used for the report
    * @param category     resource category such as users, sessions
    * @param resourceName the name of the resource
    * @param realm        the real (default: /)
    * @param root_url     an optional prefix to be added to the url
    * @return
    */
  def jsonUpdate(requestName: String, category: String, resourceName: String, realm: String = "/",
               resource_version: String = resource_version, root_url: String = ""): HttpRequestBuilder = {
    val url = getJsonURL(category = category, resourceName = resourceName, realm = realm,
      resource_version = resource_version, root_url = root_url)
    val queryParamMap: mutable.Map[String, Any] = mutable.Map[String, Any]()
    if (resource_version >= "2.0") {
      //queryParamMap.put("realm", realm)
    }
    http(requestName)
      .patch(url)
      .queryParamMap(queryParamMap.toMap)
      .body(StringBody("""[{ "operation":"replace", "field" : "/userPassword", "value" :  "password"}]""")).asJson
      .headers(getAcceptAPIVersion("4.0"))
  }

  /**
    * WRITE a resource using the REST API - PUT
    * Hard coded to update password
    * @param requestName  the name of the request to be used for the report
    * @param category     resource category such as users, sessions
    * @param resourceName the name of the resource
    * @param realm        the real (default: /)
    * @param root_url     an optional prefix to be added to the url
    * @return
    */
  def jsonWrite(requestName: String, category: String, resourceName: String, realm: String = "/",
               resource_version: String = resource_version, root_url: String = ""): HttpRequestBuilder = {
    val url = getJsonURL(category = category, resourceName = resourceName, realm = realm,
      resource_version = resource_version, root_url = root_url)
    val queryParamMap: mutable.Map[String, Any] = mutable.Map[String, Any]()
    if (resource_version >= "2.0") {
      //queryParamMap.put("realm", realm)
    }
    http(requestName)
      .put(url)
      .queryParamMap(queryParamMap.toMap)
      .body(StringBody("""{"userPassword":"password","mail":"test@example.com"}""")).asJson
      .headers(getAcceptAPIVersion("4.0"))
      .header("If-None-Match", "*")
  }

  def jsonAction(requestName: String, action: String, category: String, paramName: String = "",
                 resourceName: String = "", realm: String = "/",
                 queryParamMap: mutable.Map[String, Any] = mutable.Map[String, Any](),
                 root_url: String = "", resource_version: String = resource_version): HttpRequestBuilder = {

    queryParamMap.put("_action", action)
    if (resource_version >= "2.0") {
      queryParamMap.put(paramName, resourceName)
    }
    val url = getJsonURL(category = category, resourceName = resourceName, realm = realm, root_url = root_url, resource_version = resource_version)
    http(requestName)
      .post(url)
      .queryParamMap(queryParamMap.toMap)
      .asJson
      .headers(getAcceptAPIVersion(resource_version))
  }

  /**
    * Log in using OpenAM REST api
    *
    * @param username         the username used for authentication
    * @param password         the password used for authentication
    * @param realm            the realm used for authentication (default: /)
    * @param requestName      the name of the request to be used in the report (default: RestLogin)
    * @param setCookie        if true set the iPlanetDirectoryPro cookie with the token value (default: true)
    * @param root_url         optional prefix url for authentication endpoint
    * @param tokenid_var_name the name of the session variable to store the tokenid value (default: "tokenid")
    * @param persistentCookie if true will check for session jwt and save it
    * @param session_jwt      captured session jwt when persistent cookie is enabled
    * @return chainBuilder instance to be executed in the scenario
    */
  def restLogin(username: String, password: String, realm: String = "/", requestName: String = "RestLogin",
                setCookie: Boolean = true,
                root_url: String = "", tokenid_var_name: String = "tokenid", session_jwt: String = "session_jwt",
                persistentCookie : Boolean = false
               ): ChainBuilder = {
    val url = getJsonURL(category = "authenticate", realm = realm, root_url = root_url)
    val queryParamMap: mutable.Map[String, Any] = mutable.Map[String, Any]()
    if ((resource_version >= "2.0") && (realm != "/")) {
      queryParamMap.put("realm", realm)
    }
    if (use_trees == "true") {
      queryParamMap.put("authIndexType", "service")
      queryParamMap.put("authIndexValue", "adminconsoleservice")
    }
    tryMax(2, counter = "try") {
      exec(flushCookieJar)
        .exec(http(requestName)
          .post(url)
          .queryParamMap(queryParamMap.toMap)
          .disableUrlEncoding
          .headers(getXOpenAMHeaders(username, password))
          .headers(acceptAPIVersion)
          .asJson
          .body(StringBody("{}"))
          .check(status.is(200))
          .check(jsonPath("$.tokenId").find.saveAs(tokenid_var_name))
         //.check(checkIf(persistentCookie)(headerRegex("Set-Cookie", """session-jwt=(.*)""").saveAs("session_jwt"))))
          .check(headerRegex("Set-Cookie", """session-jwt=(.*);""").optional.saveAs(session_jwt)))
        .doIf(setCookie) {
          exec(addCookie(Cookie("iPlanetDirectoryPro", "${%s}".format(tokenid_var_name))))
      }
        .exitHereIfFailed
    }
  }
  /***
    * Login with Persistent cookie
    * @param realm the realm used for authentication (default: /)
    * @param session_jwt captured session-jwt to be set as cookie
    * @param setCookie if true set the iPlanetDirectoryPro cookie with the token value (default: true)
    * @param tokenid_var_name the name of the session variable to store the tokenid value (default: "tokenid")
    * @return
    */
  def persistentLogin(realm: String = "/", session_jwt: String,
                      setCookie: Boolean = true, tokenid_var_name: String = "tokenid"): ChainBuilder = {
    val url = am_url + "/openam/json/authenticate?realm=" + realm
    exec(flushSessionCookies)
        .exec(addCookie(Cookie("session-jwt", "${session_jwt}")))
      .exec(http("PersistentURL")
        .post(url)
        .headers(acceptAPIVersion)
        .asJson
        .check(status.is(200))
        .check(jsonPath("$.tokenId").find.saveAs(tokenid_var_name)))
        .doIf(setCookie) {
          exec(addCookie(Cookie("iPlanetDirectoryPro", "${%s}".format(tokenid_var_name))))
        }
  }

  def addSSOCookie(tokenId: String, cookieName: String = "iPlanetDirectoryPro"): ChainBuilder = {
    exec(addCookie(Cookie(cookieName, tokenId)))
  }

  /**
    * Log out using OpenAM REST api
    *
    * @return chainBuilder instance to be executed in the scenario
    */
  def restLogout(root_url: String = "", requestName: String = "RestLogout"): ChainBuilder = {
    exec(logoutSession(requestName = requestName, tokenId = "${tokenid}", root_url = root_url))
      .exec(flushSessionCookies)
  }

  /**
    * ******************************************************
    * UI Login
    * ******************************************************
    */

  /**
    * parse goto value from UILogin page
    */
  def _parseGoto: HttpCheck = {
    // char '=' at the end of some goto values is unexpectedly encoded to '&#x3d;'
    // (url encoded twice ? '=' => '%3d' => &#x3d; ?
    // => replace  &#x3d; with '='
    regex( """<input type="hidden" name="goto" value="([^"]*)" />""").transform(_.replaceAll("&#x3d;", "="))
      .saveAs("goto")
  }

  /**
    * parse SunQueryParamsString from UILogin page
    */
  def _parseSunQueryParamsString: HttpCheck = {
    regex( """<input type="hidden" name="SunQueryParamsString" value="([^"]*)" />""")
      .saveAs("SunQueryParamsString")
  }

  /**
    * Full UILogin
    *
    * @param username    the username to login
    * @param password    the password to login
    * @param realm       realm (default: /)
    * @param url         the login relative url (default: "/openam/UI/Login")
    * @param checkString the regular expression we want to check in the reponse
    * @param requestName the name of the request to be used for the report (default 'UILogin')
    * @return
    */

  def UILogin(username: String, password: String, url: String = "/openam/UI/Login", checkString: String = "openam",
              requestName: String = "UILogin", realm: String = "/"): ChainBuilder = {
    group(requestName) {
      exec(UILoginStep1(realm = realm))
        .exitHereIfFailed
        .exec(UILoginStep2(username, password, "${goto}", "${SunQueryParamsString}", checkString = "console"))
    }
  }


  /**
    * UILogin step 1
    *
    * @param realm       realm (default: /)
    * @param requestName the name of the request to be used for the report
    * @param root_url
    * @return
    */
  def UILoginStep1(realm: String = "/", requestName: String = "UILogin_step1", root_url: String = "") = {
    exec(http(requestName)
      .get(root_url + "/openam/UI/Login")
      .queryParam("realm", realm)
      .check(_parseGoto)
      .check(_parseSunQueryParamsString)
    )
  }

  /**
    * UILogin step 2
    *
    * @param username             the username to login
    * @param password             the password to login
    * @param goto                 the goto string retrieved from UILogin phase 1
    * @param SunQueryParamsString the SunQueryParamsString string retrieved from UILogin phase 1
    * @param url                  the login relative url (default: "/openam/UI/Login")
    * @param checkString          the regular expression we want to check in the reponse
    * @param requestName          the name of the request to be used for the report
    * @return
    */
  def UILoginStep2(username: String, password: String, goto: String, SunQueryParamsString: String,
                   url: String = "/openam/UI/Login", checkString: String = "openam",
                   requestName: String = "UILogin_step2"): ChainBuilder = {
    exec(http("UILogin2")
      .post(url)
      .disableUrlEncoding
      .formParam("IDToken1", username)
      .formParam("IDToken2", password)
      .formParam("IDButton", "LogIn")
      .formParam("goto", goto)
      .formParam("gotoOnFail", "")
      .formParam("SunQueryParamsString", SunQueryParamsString)
      .formParam("encoded", "true")
      .formParam("gx_charset", "UTF-8")
      .check(currentLocation.saveAs("new_url"))
      .check(regex(checkString))
    )
  }

  /**
    *
    * @param requestName the name of the request to be used for the report
    * @param root_url    an optional prefix to be added to the url
    * @param checkString the regular expression we want to check in the reponse
    * @return
    */
  def UILogout(requestName: String = "UILogout", root_url: String = "", checkString: String = "You are logged out") = {
    group(requestName) {
      exec(http(requestName)
        .get("/openam/UI/Logout")
        .check(regex(checkString))
      )
    }
  }

  /**
    * ******************************************************
    * XUI Login
    * ******************************************************
    */
  val headers_nosession = Map(
    "X-NoSession" -> "true",
    "X-Username" -> "anonymous",
    "X-Password" -> "anonymous",
    "X-Requested-With" -> "XMLHttpRequest")


  /**
    * Get serverinfo
    *
    * @param realm the realm where to get the user
    * @return
    */
  def serverInfo(realm: String = "/", root_url: String = ""): HttpRequestBuilder = {
    // Only resource_version 1.0 seems to be supported, even with 14.0.0
    jsonRead(requestName = "ServerInfo", category = "serverinfo", resourceName = "*", realm = realm,
      root_url = root_url, resource_version = "1.0")
      .headers(Map(
        "Origin" -> am_url,
        "X-Requested-With" -> "XMLHttpRequest"
      ))
      .check(status.in(200, 304)
      )
  }

  /**
    * Full XUILogin
    *
    * @param username    login username
    * @param password    login password
    * @param realm       realm (default: /)
    * @param requestName name of the request for the report (default "XUILogin")
    * @param root_url    root_url to be used for building authenticate url (default "")
    */
  def XUILogin(username: String, password: String, realm: String = "/",
               requestName: String = "XUILogin", root_url: String = ""): ChainBuilder = {
    group(requestName) {
      exec(XUILoginStep1(realm = realm, root_url = root_url))
        .exitHereIfFailed
        .exec(XUILoginStep2(authId = "${authId}", username = username, password = password, realm = realm,
          root_url = root_url))
        .exitHereIfFailed
        .exec(XUILoginStep3(realm = realm, root_url = root_url))
    }
  }

  /**
    * First REST call to authenticate endpoint done during XUI Login
    *
    * @param realm    realm (default: /)
    * @param root_url root_url to be used for building authenticate url (default "")
    */
  def XUILoginStep1(realm: String = "/", requestName: String = "XUILogin_step1", root_url: String = ""): ChainBuilder = {
    val url = getJsonURL(category = "authenticate", realm = realm, root_url = root_url)
    exec(
      http(requestName)
        .post(url)
        .body(StringBody(""))
        .headers(headers_nosession)
        .headers(acceptAPIVersion)
        .check(jsonPath("$.authId").find.saveAs("authId"))
    )
  }

  /**
    * 2nd REST call to authenticate endpoint done during XUI Login
    *
    * @param authId      the authId string returned by the 1st authenticate call
    * @param username    login username
    * @param password    login password
    * @param realm       realm (default: /)
    * @param requestName name of the request for the report (default "XUILogin2")
    * @param root_url    root_url to be used for building authenticate url (default "")
    */
  def XUILoginStep2(authId: String, username: String, password: String, realm: String = "/",
                    requestName: String = "XUILogin_step2", root_url: String = ""): ChainBuilder = {
    val url = getJsonURL(category = "authenticate", realm = realm, root_url = root_url)
    exec(http(requestName)
      .post(url)
      .headers(acceptAPIVersion)
      .asJson
      .body(StringBody(
        """{"authId":"%s","template":"","stage":"DataStore1","header":"Sign in to OpenAM",""".format(authId) +
          """"callbacks":[{"type":"NameCallback","output":[{"name":"prompt","value":"User Name:"}],""" +
          """"input":[{"name":"IDToken1","value":"%s"}]},{"type":"PasswordCallback",""".format(username) +
          """"output":[{"name":"prompt","value":"Password:"}],"input":[{"name":"IDToken2","value":"%s"}]}]}"""
            .format(password)
      ))
      .check(jsonPath("$.tokenId").find.saveAs("tokenId"))
    )
      .exec(addCookie(Cookie("iPlanetDirectoryPro", "${tokenId}")))
  }

  def XUILoginStep3(realm: String = "/", root_url: String = ""): ChainBuilder = {
    exec(idFromSession(root_url = root_url))
      .exitHereIfFailed
      .exec(readUser(username = "${id}", realm = realm, root_url = root_url))
  }

  /**
    *
    * @param tokenId     the tokenId of the session to logout
    * @param requestName the name of the request ot be used for the report
    * @return
    */
  def XUILogout(tokenId: String, requestName: String = "XUILogout", root_url: String = ""): ChainBuilder = {
    // TODO: extract the tokenId from the cookies ?
    group(requestName) {
      exec(validateSession(tokenId = tokenId, root_url = root_url))
        .exitHereIfFailed
        .exec(logoutSession(tokenId = tokenId, root_url = root_url))
        .exec(flushSessionCookies)
    }
  }

  /**
    * ******************************************************
    * Identity management
    * *******************************************************
    */

  /**
    * Read a user profile using REST API
    *
    * @param username the name of the user
    * @param realm    the realm where to get the user
    * @return
    */
  def readUser(username: String, realm: String = "/", root_url: String = ""): HttpRequestBuilder = {
    jsonRead(requestName = "ReadUser", category = "users", resourceName = username, realm = realm, root_url = root_url)
      .check(jsonPath("$.username").is(username)
      )
  }

  /**
    * Update a user profile using REST API - PATCH
    *
    * @param username the name of the user
    * @param realm    the realm where to get the user
    * @return
    */
  def updateUser(username: String, realm: String = "/", root_url: String = ""): HttpRequestBuilder = {
    jsonUpdate(requestName = "updateUser", category = "users", resourceName = username, realm = realm, root_url = root_url)
      .check(jsonPath("$.username").is(username)
      )
  }

  /**
    * Write a user profile using REST API - PUT
    *
    * @param username the name of the user
    * @param realm    the realm where to get the user
    * @return
    */
  def writeUser(username: String, realm: String = "/", root_url: String = ""): HttpRequestBuilder = {
    jsonWrite(requestName = "writeUser", category = "users", resourceName = username, realm = realm, root_url = root_url)
      .check(jsonPath("$.username").is(username)
      )
  }

  /**
    * Get profile id form session cookie
    * Save id as id
    */
  def idFromSession(root_url: String = ""): HttpRequestBuilder = {
    jsonAction(requestName = "IdFromSession", action = "idFromSession", category = "users", resourceName = "",
      root_url = root_url)
      .headers(headers_nosession)
      .asJson
      .headers(acceptAPIVersion)
      .check(status.is(200))
      .check(jsonPath("$.id").saveAs("id"))
  }

  /**
    * Refresh an SSO token using an amadmin tokenId
    *
    * @param tokenId      the token to refresh
    * @param adminTokenId amadmin token to use for authentication
    * @param root_url     (optional) root_url to be added as a prefix to the url
    * @return
    */
  def refreshSSOToken(tokenId: String, adminTokenId: String, root_url: String = ""): ChainBuilder = {
    var action = "refresh"
    val queryParamMap = mutable.Map[String, Any]()
    var path = "$.idletime"
    var value = "0"
    if (resource_version < "2.0") {
      action = "isActive"
      queryParamMap.put("refresh", "true")
      path = "$.active"
      value = "true"
    }
    exec(addCookie(Cookie("iPlanetDirectoryPro", adminTokenId)))
      .exec(jsonAction(requestName = "RefreshSSOToken", action = action, category = "sessions",
        resourceName = tokenId, paramName = "tokenId", root_url = root_url, queryParamMap = queryParamMap)
        .check(status.is(200))
        .check(jsonPath(path).is(value)))
  }

  /**
    * ******************************************************
    * Sessions
    * *******************************************************
    */

  /**
    * Validate a session using REST API
    *
    * @param tokenId the tokenId of the session
    */
  def validateSession(tokenId: String, root_url: String = ""): ChainBuilder = {
    if (resource_version < "2.0") {
      exec(jsonAction(requestName = "ValidateSession", action = "validate", category = "sessions", resourceName = tokenId,
        root_url = root_url)
        .check(jsonPath("$.valid").is("true")))
        .doIf(session => resource_version.>=("1.1")) {
          exec(getMaxIdle(tokenId = tokenId, root_url = root_url))
        }
    }
    else {
      exec(getSessionInfo(tokenId = tokenId, root_url = root_url))
    }
  }

  /**
    * Validate a session using REST API (only available with resource_version >= 2.0)
    *
    * @param tokenId the tokenId of the session
    */
  def getSessionInfo(tokenId: String, root_url: String = ""): HttpRequestBuilder = {
    jsonAction(requestName = "GetSessionInfo", action = "getSessionInfo", category = "sessions",
      resourceName = tokenId, root_url = root_url)
      .check(jsonPath("$.maxIdleExpirationTime").saveAs("maxIdleExpirationTime"))
      .check(jsonPath("$.latestAccessTime").lte("${maxIdleExpirationTime}"))

  }

  /**
    * Retrieves the property value for the corresponding property Id from the users session. The value is stored in the
    * gatling session.
    *
    * @param propertyId the session property Id
    * @param tokenId the token Id of the session
    * @param root_url the root url
    */
  def getSessionProperty(propertyId: String, saveAsKey: String , tokenId: String, root_url: String = ""): HttpRequestBuilder = {
    jsonAction(requestName = "GetSessionProperties", action = "getSessionProperties",
      category = "sessions", root_url = root_url, resource_version = "2.0")
      .check(jsonPath("$." + propertyId).saveAs(saveAsKey))
  }

  /**
    * Logout a session using REST API
    *
    * @param tokenId the tokenId of the session
    */
  def logoutSession(tokenId: String, requestName: String = "LogoutSession", root_url: String = ""): HttpRequestBuilder = {
    jsonAction(requestName = requestName, action = "logout", category = "sessions", root_url = root_url)
      .check(status.is(200))
      .check(jsonPath("$.result").is("Successfully logged out"))

  }

  /**
    * Get max dile time for a session
    * Note: only supported on 13.0.0 and +
    * using tokenId as a queryParam
    *
    * @param tokenId the tokenId of the session
    */
  def getMaxIdle(tokenId: String, root_url: String = ""): HttpRequestBuilder = {
    jsonAction(requestName = "GetMaxIdle", action = "getMaxIdle", category = "sessions", paramName = "tokenId",
      resourceName = tokenId, root_url = root_url)
      .check(status.is(200))
      .check(jsonPath("$.maxidletime").saveAs("maxidletime"))
  }

  def policyApiVersionHeader(): scala.collection.immutable.Map[String, String] =
  {
    if (resource_version >= "2.0"){
      val value: String = "protocol=1.0,resource=" + resource_version
      scala.collection.immutable.Map(
        "Accept-API-Version" -> value
      )}
    else{
      scala.collection.immutable.Map(
        "Accept" -> "*/*"
      )
    }
  }

  def evaluatePolicy(resourceName: String, tokenId: String,
                     method: String = "GET", requestName: String = "EvaluatePolicy"): ChainBuilder = {
        exec(http(requestName)
          .post(s"""/openam/json/realms/root/policies?_action=evaluate""")
          .headers(Map("Content-Type" -> "application/json"))
          .headers(policyApiVersionHeader)
          .asJson
          .body(getPolicyRequest(resourceName, tokenId))
          //.check(jsonPath(s"$$[0].actions.${method}").is("true"))   // dollar sign is escaped as $$
          .check(status.is(200)))
  }

// Multiple resources to evaluate
  def evaluatePolicyMultipleResources(resourceNames: Array[String], tokenId: String): ChainBuilder = {
        exec(evaluatePolicy(resourceNames.mkString("\",\""),tokenId))
  }

  def getPolicyRequest(resourceName: String, tokenId: String):
  StringBody = {
    val policy =
      s"""{
  "resources": [
    "$resourceName"
   ],
  "application": "$application"
}"""

    StringBody(policy)
  }

  def getEvaluateURL(realm: String): session.Expression[String] = {
    if(!realm.equals("/")) {
      if(realm.startsWith("/")) {
        s"""/json/realms$realm/policies?_action=evaluate"""
      } else {
        s"""/json/realms/$realm/policies?_action=evaluate"""
      }
    } else {
      "/json/policies?_action=evaluate"
    }
  }
}
