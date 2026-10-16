/*
 * See the NOTICE file distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package com.xwiki.activedirectory.internal;

import java.security.Principal;
import java.util.Arrays;

import javax.servlet.http.HttpSession;

import org.apache.commons.lang3.StringUtils;
import org.securityfilter.filter.SecurityRequestWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xwiki.configuration.ConfigurationSource;
import org.xwiki.contrib.ldap.LDAPProfileXClass;
import org.xwiki.contrib.ldap.XWikiLDAPAuthServiceImpl;
import org.xwiki.contrib.ldap.XWikiLDAPConfig;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.model.reference.SpaceReference;

import com.xpn.xwiki.XWiki;
import com.xpn.xwiki.XWikiContext;
import com.xpn.xwiki.XWikiException;
import com.xpn.xwiki.doc.XWikiDocument;
import com.xpn.xwiki.objects.BaseObject;
import com.xpn.xwiki.user.api.XWikiAuthService;
import com.xpn.xwiki.user.api.XWikiUser;
import com.xpn.xwiki.user.impl.xwiki.XWikiAuthServiceImpl;
import com.xpn.xwiki.web.Utils;
import com.xpn.xwiki.web.XWikiRequest;
import com.xwiki.licensing.License;
import com.xwiki.licensing.Licensor;

/**
 * Custom Active Directory authenticator that currently only serves as a way to make sure that a valid License for the
 * Active Directory application exists in order to authenticate requests. In the future it'll also serve as the place
 * to add new features for Active Directory authentication.
 *
 * @version $Id$
 * @since 1.1
 */
public class ActiveDirectoryAuthServiceImpl extends XWikiLDAPAuthServiceImpl
{
    private static final String LDAP_SSL = "ldap_ssl";

    private static final String LDAP_SSL_KEYSTORE = "ldap_ssl.keystore";

    private static final String LDAP_SSL_SECURE_PROVIDER = "ldap_ssl.secure_provider";

    private static final String EXTENSION_ID = "com.xwiki.activedirectory:application-activedirectory-api";

    /**
     * Session attribute where the LDAP authenticator stores the SSO remote user, see
     * {@code XWikiLDAPAuthServiceImpl#checkAuthSSO}.
     */
    private static final String LDAP_REMOTE_USER_SESSION_KEY = "ldap.remoteuser";

    private static final SpaceReference AD_CODE_SPACE_REFERENCE =
        new SpaceReference("xwiki", Arrays.asList("ActiveDirectory", "Code"));

    private static final Logger LOGGER = LoggerFactory.getLogger(ActiveDirectoryAuthServiceImpl.class);

    private Licensor licensor = Utils.getComponent(Licensor.class);

    private XWikiAuthService fallbackAuthService = new XWikiAuthServiceImpl();

    private ConfigurationSource configurationSource = Utils.getComponent(ConfigurationSource.class, "activedirectory");

    @Override
    protected XWikiLDAPConfig createXWikiLDAPConfig(String authInput)
    {
        XWikiLDAPConfig xWikiLDAPConfig = new XWikiLDAPConfig(authInput, this.configurationSource);
        xWikiLDAPConfig.setFinalProperty("ldap_UID_attr", "sAMAccountName");
        xWikiLDAPConfig.setFinalProperty("ldap", "1");
        xWikiLDAPConfig.setFinalProperty("ldap_trylocal", "1");
        xWikiLDAPConfig.setFinalProperty("ldap_update_user", "1");
        xWikiLDAPConfig.setFinalProperty("ldap_fields_mapping",
            "last_name=sn,first_name=givenName,email=mail,company=company,comment=comment,phone=mobile");

        DocumentReference adConfigClassRef =
            new DocumentReference("ActiveDirectoryConfigClass", AD_CODE_SPACE_REFERENCE);
        DocumentReference adConfigRef = new DocumentReference("ActiveDirectoryConfig", AD_CODE_SPACE_REFERENCE);

        try {
            XWikiContext context = (XWikiContext) this.getExecutionContext().getProperty("xwikicontext");
            XWiki xwiki = context.getWiki();
            XWikiDocument adConfigDoc = xwiki.getDocument(adConfigRef, context);
            BaseObject adConfigObj = adConfigDoc.getXObject(adConfigClassRef);
            if (adConfigObj != null) {
                xWikiLDAPConfig.setFinalProperty(LDAP_SSL, adConfigObj.getStringValue(LDAP_SSL));
                xWikiLDAPConfig.setFinalProperty(LDAP_SSL_KEYSTORE, adConfigObj.getStringValue(LDAP_SSL_KEYSTORE));

                String provider = adConfigObj.getStringValue(LDAP_SSL_SECURE_PROVIDER);
                xWikiLDAPConfig.setFinalProperty(LDAP_SSL_SECURE_PROVIDER,
                    StringUtils.isNoneBlank(provider) ? provider : null);
            }
        } catch (XWikiException e) {
            e.printStackTrace();
        }

        return xWikiLDAPConfig;
    }

    @Override
    public XWikiUser checkAuth(XWikiContext context) throws XWikiException
    {
        // checkAuth runs on every request, so the license is checked only when LDAP is about to log in an SSO user.
        // Password logins are checked in authenticate().
        String ssoUser = getSSOUserToAuthenticate(context);
        if (ssoUser == null || shouldUseADAuthService(ssoUser, context)) {
            try {
                return super.checkAuth(context);
            } catch (Exception e) {
                LOGGER.error("Active Directory checkAuth failed, falling back to XWiki authentication.", e);
            }
        }
        // Skip the LDAP SSO login. Existing sessions and password logins still work.
        return this.fallbackAuthService.checkAuth(context);
    }

    @Override
    public XWikiUser checkAuth(String username, String password, String rememberme, XWikiContext context)
        throws XWikiException
    {
        if (shouldUseADAuthService(username, context)) {
            try {
                return super.checkAuth(username, password, rememberme, context);
            } catch (Exception e) {
                LOGGER.error("Active Directory credentials check failed, falling back to XWiki authentication.", e);
            }
        }
        return this.fallbackAuthService.checkAuth(username, password, rememberme, context);
    }

    @Override
    public Principal authenticate(String userId, String password, XWikiContext context) throws XWikiException
    {
        if (shouldUseADAuthService(userId, context)) {
            try {
                return super.authenticate(userId, password, context);
            } catch (Exception e) {
                LOGGER.error("Active Directory authentication failed, falling back to XWiki authentication.", e);
                return this.fallbackAuthService.authenticate(userId, password, context);
            }
        }
        return this.fallbackAuthService.authenticate(userId, password, context);
    }

    /**
     * Decide if the LDAP authentication should be used for the given user. Using LDAP means creating a new user if the
     * given credentials are valid and point to a user which wasn't imported in XWiki yet.
     * <br>
     * Note that once any license condition is broken (e.g. expiration date, number of users), that license becomes
     * invalid. But we still want to accept older AD users once the user limit is exceeded, in order to not block
     * everyone out:
     * <ul>
     * <li>no license, or expired license: don't use AD</li>
     * <li>there is room for one more user: use AD</li>
     * <li>the license is full (5/5) or exceeded (6/5): only the AD users created before the limit was reached can use
     * AD</li>
     * </ul>
     *
     * @param username the login input (form login) or the SSO remote user
     * @param context xwiki context
     * @return true if the given user should use the LDAP authentication
     */
    private boolean shouldUseADAuthService(String username, XWikiContext context)
    {
        try {
            License license = this.licensor.getLicense(EXTENSION_ID);
            // The expiration check must match DefaultLicenseValidator#isValid. Checked first, so that an unlicensed or
            // expired instance doesn't look the user profile up for nothing.
            if (license == null || license.getExpirationDate() < System.currentTimeMillis()) {
                return false;
            }
            long maxUserCount = license.getMaxUserCount();
            // Room for a new user: no need to know if this one already exists.
            if (maxUserCount < 0 || this.licensor.getUserCount() < maxUserCount) {
                return true;
            }
            XWikiDocument userProfile = findLDAPProfile(username, context);
            boolean underLimit = userProfile != null
                && this.licensor.isUserUnderLimit(userProfile.getDocumentReference(), maxUserCount);
            if (!underLimit) {
                LOGGER.debug("Not using Active Directory for [{}]: the license user limit [{}] is reached.", username,
                    maxUserCount);
            }
            return underLimit;
        } catch (Exception e) {
            LOGGER.error("Failed to determine Active Directory access status.", e);
            return false;
        }
    }

    /**
     * Find the profile of an existing LDAP user, the same way the LDAP authenticator does it before binding: by the
     * trimmed login input, in the current wiki and then in the main wiki.
     */
    private XWikiDocument findLDAPProfile(String username, XWikiContext context) throws XWikiException
    {
        if (username == null) {
            return null;
        }
        // Note: the LDAP authenticator also finds profiles by DN after the bind (e.g. a user imported as "jdoe"
        // logging in as "jdoe@domain"). Such users are seen as new here, which only matters when the license is full.
        String uid = username.trim();
        XWikiDocument profile = new LDAPProfileXClass(context).searchDocumentByUid(uid);
        if (profile == null && !context.isMainWiki()) {
            String wiki = context.getWikiId();
            try {
                context.setWikiId(context.getMainXWiki());
                profile = new LDAPProfileXClass(context).searchDocumentByUid(uid);
            } finally {
                context.setWikiId(wiki);
            }
        }
        return profile;
    }

    /**
     * Cheap check, run on every request. Mirrors {@code XWikiLDAPAuthServiceImpl#checkSessionPrincipal}.
     *
     * @return the SSO remote user if LDAP is about to log it in (not bound to the session yet), null otherwise
     */
    private String getSSOUserToAuthenticate(XWikiContext context)
    {
        XWikiRequest request = context.getRequest();
        if (request == null) {
            return null;
        }
        String header = getConfiguration().getHttpHeader();
        String remoteUser = StringUtils.isEmpty(header) ? request.getRemoteUser() : request.getHeader(header);
        if (remoteUser == null) {
            return null;
        }
        HttpSession session = request.getSession(false);
        boolean alreadyLoggedIn = session != null
            && session.getAttribute(SecurityRequestWrapper.PRINCIPAL_SESSION_KEY) != null
            && remoteUser.equals(session.getAttribute(LDAP_REMOTE_USER_SESSION_KEY));
        return alreadyLoggedIn ? null : remoteUser;
    }
}
