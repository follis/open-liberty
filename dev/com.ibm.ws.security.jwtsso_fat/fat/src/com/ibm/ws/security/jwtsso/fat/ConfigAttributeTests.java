/*******************************************************************************
 * Copyright (c) 2018 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package com.ibm.ws.security.jwtsso.fat;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.regex.Pattern;

import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import com.gargoylesoftware.htmlunit.Page;
import com.gargoylesoftware.htmlunit.WebClient;
import com.ibm.ws.security.fat.common.actions.TestActions;
import com.ibm.ws.security.fat.common.expectations.Expectations;
import com.ibm.ws.security.fat.common.expectations.ServerMessageExpectation;
import com.ibm.ws.security.fat.common.validation.TestValidationUtils;
import com.ibm.ws.security.jwtsso.fat.utils.CommonExpectations;
import com.ibm.ws.security.jwtsso.fat.utils.JwtFatConstants;
import com.ibm.ws.security.jwtsso.fat.utils.MessageConstants;

import componenttest.annotation.ExpectedFFDC;
import componenttest.annotation.Server;
import componenttest.custom.junit.runner.FATRunner;
import componenttest.custom.junit.runner.Mode;
import componenttest.custom.junit.runner.Mode.TestMode;
import componenttest.topology.impl.LibertyServer;

@Mode(TestMode.FULL)
@RunWith(FATRunner.class)
public class ConfigAttributeTests extends CommonJwtFat {

    protected static Class<?> thisClass = ConfigAttributeTests.class;

    @Server("com.ibm.ws.security.jwtsso.fat")
    public static LibertyServer server;

    private TestActions actions = new TestActions();
    private TestValidationUtils validationUtils = new TestValidationUtils();
    private WebClient webClient = new WebClient();

    String protectedUrl = "http://" + server.getHostname() + ":" + server.getHttpDefaultPort() + JwtFatConstants.SIMPLE_SERVLET_PATH;
    String defaultUser = JwtFatConstants.TESTUSER;
    String defaultPassword = JwtFatConstants.TESTUSERPWD;

    @BeforeClass
    public static void setUp() throws Exception {
        setUpAndStartServer(server, JwtFatConstants.COMMON_CONFIG_DIR + "/server_withFeature.xml");
    }

    @Before
    public void beforeTest() {
        webClient = new WebClient();
    }

    /**
     * Test the config attributes cookieName and includeLtpaCookie.
     * Invoke app on the happy path, check that jwt cookie with correct name came back,
     * and that ltpa cookie came back.
     */
    @Mode(TestMode.LITE)
    @Test
    public void test_cookieName_includeLtpa() throws Exception {
        server.reconfigureServer(JwtFatConstants.COMMON_CONFIG_DIR + "/server_testcookiename.xml");

        String cookieName = "easyrider";

        String currentAction = TestActions.ACTION_INVOKE_PROTECTED_RESOURCE;
        Expectations expectations = new Expectations();
        expectations.addExpectations(CommonExpectations.successfullyReachedLoginPage(currentAction));

        Page response = actions.invokeUrl(testName.getMethodName(), webClient, protectedUrl);
        validationUtils.validateResult(response, currentAction, expectations);

        currentAction = TestActions.ACTION_SUBMIT_LOGIN_CREDENTIALS;
        expectations.addExpectations(CommonExpectations.successfullyReachedUrl(currentAction, protectedUrl));
        expectations.addExpectations(CommonExpectations.jwtCookieExists(currentAction, webClient, cookieName));
        expectations.addExpectations(CommonExpectations.ltpaCookieExists(currentAction, webClient));
        expectations.addExpectations(CommonExpectations.getResponseTextExpectationsForJwtCookie(currentAction, cookieName, defaultUser));
        expectations.addExpectations(CommonExpectations.getJwtPrincipalExpectations(currentAction, defaultUser, JwtFatConstants.DEFAULT_ISS_REGEX));
        expectations.addExpectations(CommonExpectations.responseTextIncludesCookie(currentAction, JwtFatConstants.LTPA_COOKIE_NAME));

        response = actions.doFormLogin(response, defaultUser, defaultPassword);
        validationUtils.validateResult(response, currentAction, expectations);
    }

    /**
     * Test the jwtBuilderRef attribute. Specify a nonexistent builderRef,
     * login should fail and we should get a meaningful error message in the log.
     * The ltpa cookie is included, but fallback is false, so fallback should not occur.
     */
    @Mode(TestMode.LITE)
//    @Test
    @ExpectedFFDC({ "com.ibm.websphere.security.jwt.InvalidBuilderException",
                    "com.ibm.ws.security.jwt.internal.JwtTokenException",
                    "javax.security.auth.login.LoginException" })
    public void test_invalidBuilderRef_ltpaFallbackFalse() throws Exception {
        server.reconfigureServer(JwtFatConstants.COMMON_CONFIG_DIR + "/server_testbadbuilder.xml");

        String currentAction = TestActions.ACTION_INVOKE_PROTECTED_RESOURCE;
        Expectations expectations = new Expectations();
        expectations.addExpectations(CommonExpectations.successfullyReachedLoginPage(currentAction));

        Page response = actions.invokeUrl(testName.getMethodName(), protectedUrl); // get back the login page
        validationUtils.validateResult(response, currentAction, expectations);

        // things should have bombed and we should be back at the login page
        currentAction = TestActions.ACTION_SUBMIT_LOGIN_CREDENTIALS;
        expectations.addExpectations(CommonExpectations.successfullyReachedLoginPage(currentAction));
        expectations.addExpectation(new ServerMessageExpectation(currentAction, server, MessageConstants.CWWKS6201W_JWT_SSO_TOKEN_SERVICE_ERROR));

        response = actions.doFormLogin(response, defaultUser, defaultPassword);
        validationUtils.validateResult(response, currentAction, expectations);
    }

    /**
     * Test the jwtBuilderRef attribute. Specify an existing and valid jwtBuilderRef.
     * Authentication will fail because the issuer mismatches the consumer, but we should
     * see evidence in the logs that the customized issuer was presented.
     * That's all we care about.
     */
    @ExpectedFFDC({ "com.ibm.websphere.security.jwt.InvalidClaimException",
                    "com.ibm.websphere.security.jwt.InvalidTokenException",
                    "com.ibm.ws.security.authentication.AuthenticationException" })
    @Mode(TestMode.LITE)
    @Test
    public void test_validBuilderRef() throws Exception {
        server.reconfigureServer(JwtFatConstants.COMMON_CONFIG_DIR + "/server_testgoodbuilder.xml");

        String issuer = "https://flintstone:19443/jwt/defaultJWT";

        Page response = actions.invokeUrl(testName.getMethodName(), protectedUrl); // get back the login page

        // now confirm we got the login page
        String currentAction = TestActions.ACTION_INVOKE_PROTECTED_RESOURCE;
        Expectations expectations = new Expectations();
        expectations.addExpectations(CommonExpectations.successfullyReachedLoginPage(currentAction));

        validationUtils.validateResult(response, currentAction, expectations);

        currentAction = TestActions.ACTION_SUBMIT_LOGIN_CREDENTIALS;
        expectations.addExpectations(CommonExpectations.successfullyReachedLoginPage(currentAction));
        expectations.addExpectation(new ServerMessageExpectation(currentAction, server, MessageConstants.CWWKS6022E_JWT_ISSUER_NOT_TRUSTED + ".+" + Pattern.quote(issuer)));
        expectations.addExpectation(new ServerMessageExpectation(currentAction, server, MessageConstants.CWWKS6031E_JWT_ERROR_PROCESSING_JWT));
        expectations.addExpectation(new ServerMessageExpectation(currentAction, server, MessageConstants.CWWKS5524E_ERROR_CREATING_JWT));
        expectations.addExpectation(new ServerMessageExpectation(currentAction, server, MessageConstants.CWWKS5523E_ERROR_CREATING_JWT_USING_TOKEN_IN_REQ));

        // log in, which should drive building a token.
        response = actions.doFormLogin(response, defaultUser, defaultPassword);
        validationUtils.validateResult(response, currentAction, expectations);
    }

    /**
     * Test the jwtConsumerRef attribute. Specify an existing and valid jwtConsumerRef and jwtBuilderRef.
     * The issuer in the builder and consumer match, and are non-default.
     * A separate test checks that the non-default builder is used.
     * When both are used together, if we can authenticate to the app and then re-access the app,
     * the second access will cause the token to be checked by the consumer.
     * If the jwtConsumerRef is in use as it should be, then the second access will succeed.
     */
    @Mode(TestMode.LITE)
    @Test
    public void test_validConsumerRef() throws Exception {
        server.reconfigureServer(JwtFatConstants.COMMON_CONFIG_DIR + "/server_testgoodconsumer.xml");

        Expectations expectations = new Expectations();
        expectations.addExpectations(CommonExpectations.successfullyReachedLoginPage(TestActions.ACTION_INVOKE_PROTECTED_RESOURCE));

        WebClient wc = new WebClient();
        Page response = actions.invokeUrl(testName.getMethodName(), wc, protectedUrl); // get back the login page
        validationUtils.validateResult(response, TestActions.ACTION_INVOKE_PROTECTED_RESOURCE, expectations);

        response = actions.doFormLogin(response, defaultUser, defaultPassword);

        String responseStr = response.getWebResponse().getContentAsString();
        boolean check = responseStr.contains("\"iss\":\"https://flintstone:19443/jwt/defaultJWT\"");
        assertTrue("Issuer in token did not match the one configured in the builder", check);

        // now access resource a second time, force token to be examined by consumer
        response = actions.invokeUrl(testName.getMethodName(), wc, protectedUrl);
        responseStr = response.getWebResponse().getContentAsString();
        boolean check2 = responseStr.contains("SimpleServlet");
        assertTrue("Did not access protected resource with custom consumer", check2);
    }

    /**
     * Test the detection of the mpJwt server config element. Specify an extra element and try to authenticate.
     * We should get an error message about the extra element.
     */
    @Test
    @Mode(TestMode.LITE)
    @ExpectedFFDC({ "com.ibm.ws.security.authentication.AuthenticationException",
                    "com.ibm.ws.security.mp.jwt.error.MpJwtProcessingException" })
    public void test_invalidConsumerRef() throws Exception {
        server.reconfigureServer(JwtFatConstants.COMMON_CONFIG_DIR + "/server_testbadconsumer.xml");

        String currentAction = TestActions.ACTION_INVOKE_PROTECTED_RESOURCE;
        Expectations expectations = new Expectations();
        expectations.addExpectations(CommonExpectations.successfullyReachedLoginPage(currentAction));
        expectations.addExpectation(new ServerMessageExpectation(currentAction, server, MessageConstants.CWWKS5521E_MANY_JWT_CONSUMER_CONFIGS));

        Page response = actions.invokeUrl(testName.getMethodName(), protectedUrl); // get back the login page
        validationUtils.validateResult(response, currentAction, expectations);

        currentAction = TestActions.ACTION_SUBMIT_LOGIN_CREDENTIALS;
        expectations.addExpectations(CommonExpectations.successfullyReachedLoginPage(currentAction));
        expectations.addExpectation(new ServerMessageExpectation(currentAction, server, MessageConstants.CWWKS6301E_MANY_JWT_CONSUMER_CONFIGS));

        response = actions.doFormLogin(response, defaultUser, defaultPassword); // should fail and we should get login page again
        validationUtils.validateResult(response, currentAction, expectations);
    }

    /**
     * Test the useLtpaIfJwtAbsent attribute, formerly known as fallBackToLtpa
     * Specify an invalid builder, includeLtpa, and fallBackToLtpa. There should be no jwt cookie present,
     * there should be an ltpa cookie present, and because fallback is enabled, we should be able to access
     * the resource.
     */
//    @Test
    @Mode(TestMode.LITE)
    @ExpectedFFDC({ "com.ibm.websphere.security.jwt.InvalidBuilderException",
                    "com.ibm.ws.security.jwt.internal.JwtTokenException",
                    "javax.security.auth.login.LoginException" })
    public void test_fallbackToLtpaTrue() throws Exception {
        server.reconfigureServer(JwtFatConstants.COMMON_CONFIG_DIR + "/server_testfallbacktoltpatrue.xml");

        Expectations expectations = new Expectations();
        expectations.addExpectations(CommonExpectations.successfullyReachedLoginPage(TestActions.ACTION_INVOKE_PROTECTED_RESOURCE));

        Page response = actions.invokeUrl(testName.getMethodName(), protectedUrl); // get back the login page
        validationUtils.validateResult(response, TestActions.ACTION_INVOKE_PROTECTED_RESOURCE, expectations);

        response = actions.doFormLogin(response, defaultUser, defaultPassword);

        String responseStr = response.getWebResponse().getContentAsString();
        boolean check = responseStr.contains("SimpleServlet");
        assertTrue("did not access protected resource", check);
        assertTrue("did not find expected ltpa cookie", responseStr.contains("cookie: LtpaToken2"));
        assertFalse("found unexpected jwt cookie", responseStr.contains("cookie: jwtToken"));
    }

    /**
     * Test the setCookieSecureFlag attribute. Use the default setting (true),
     * and inspect cookie to see that it happened.
     */
    @Test
    @Mode(TestMode.LITE)
    public void test_cookieSecureTrue_httpOnlyTrue() throws Exception {
        server.reconfigureServer(JwtFatConstants.COMMON_CONFIG_DIR + "/server_testcookiesecure.xml");

        WebClient wc = new WebClient();

        Page response = actions.invokeUrl(testName.getMethodName(), wc, protectedUrl); // get back the login page

        // now disable redirect so we can see the cookies in the 302
        wc.getOptions().setRedirectEnabled(false);
        wc.getOptions().setThrowExceptionOnFailingStatusCode(false);

        String currentAction = TestActions.ACTION_SUBMIT_LOGIN_CREDENTIALS;
        Expectations expectations = new Expectations();
        // check for warning that secure cookie is being set on http
        expectations.addExpectation(new ServerMessageExpectation(currentAction, server, MessageConstants.CWWKS9127W_JWT_COOKIE_SECURITY_MISMATCH));
        expectations.addExpectations(CommonExpectations.jwtCookieExists(currentAction, wc, JwtFatConstants.JWT_COOKIE_NAME, JwtFatConstants.SECURE, JwtFatConstants.HTTPONLY));

        response = actions.doFormLogin(response, defaultUser, defaultPassword);
        validationUtils.validateResult(response, currentAction, expectations);
    }

    /**
     * Test that the jwtsso cookie respects the webAppSecurity httpOnlyCookies attribute setting.
     * Set webAppSecurity httpOnlyCookies="false" and inspect the cookie.
     */
    @Test
    @Mode(TestMode.LITE)
    public void test_cookieSecureTrue_httpOnlyFalse() throws Exception {
        server.reconfigureServer(JwtFatConstants.COMMON_CONFIG_DIR + "/server_testcookiesecure_httponlyfalse.xml");

        WebClient wc = new WebClient();

        Page response = actions.invokeUrl(testName.getMethodName(), wc, protectedUrl); // get back the login page

        // now disable redirect so we can see the cookies in the 302
        wc.getOptions().setRedirectEnabled(false);
        wc.getOptions().setThrowExceptionOnFailingStatusCode(false);

        String currentAction = TestActions.ACTION_SUBMIT_LOGIN_CREDENTIALS;
        Expectations expectations = new Expectations();
        expectations.addExpectation(new ServerMessageExpectation(currentAction, server, MessageConstants.CWWKS9127W_JWT_COOKIE_SECURITY_MISMATCH));
        expectations.addExpectations(CommonExpectations.jwtCookieExists(currentAction, wc, JwtFatConstants.JWT_COOKIE_NAME, JwtFatConstants.SECURE, JwtFatConstants.NOT_HTTPONLY));

        response = actions.doFormLogin(response, defaultUser, defaultPassword);
        validationUtils.validateResult(response, currentAction, expectations);
    }

}
