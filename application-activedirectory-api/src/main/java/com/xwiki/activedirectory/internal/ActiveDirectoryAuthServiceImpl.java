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
import java.util.Date;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xwiki.configuration.ConfigurationSource;
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
import com.xwiki.licensing.License;
import com.xwiki.licensing.Licensor;
import com.xwiki.licensing.internal.AuthExtensionUserManager;
import com.xwiki.licensing.internal.UserCounter;

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
    protected static final String EXTENSION_ID = "com.xwiki.activedirectory:application-activedirectory-api";

    private static final String LDAP_SSL = "ldap_ssl";

    private static final String LDAP_SSL_KEYSTORE = "ldap_ssl.keystore";

    private static final String LDAP_SSL_SECURE_PROVIDER = "ldap_ssl.secure_provider";

    private static final SpaceReference AD_CODE_SPACE_REFERENCE =
        new SpaceReference("xwiki", Arrays.asList("ActiveDirectory", "Code"));

    private static final Logger LOGGER = LoggerFactory.getLogger(ActiveDirectoryAuthServiceImpl.class);

    private Licensor licensor = Utils.getComponent(Licensor.class);

    private XWikiAuthService fallbackAuthService = new XWikiAuthServiceImpl();

    private ConfigurationSource configurationSource = Utils.getComponent(ConfigurationSource.class, "activedirectory");

    private UserCounter userCounter = Utils.getComponent(UserCounter.class);

    private AuthExtensionUserManager activeDirectoryUserManager =
        Utils.getComponent(AuthExtensionUserManager.class, EXTENSION_ID);

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
        if (isLicensed()) {
            return super.checkAuth(context);
        } else {
            return this.fallbackAuthService.checkAuth(context);
        }
    }

    @Override
    public XWikiUser checkAuth(String username, String password, String rememberme, XWikiContext context)
        throws XWikiException
    {
        if (isLicensed()) {
            return super.checkAuth(username, password, rememberme, context);
        } else {
            return this.fallbackAuthService.checkAuth(username, password, rememberme, context);
        }
    }

    @Override
    public Principal authenticate(String userId, String password, XWikiContext context) throws XWikiException
    {
        if (shouldUseADAuthService(userId, context)) {
            return super.authenticate(userId, password, context);
        } else {
            return this.fallbackAuthService.authenticate(userId, password, context);
        }
    }

    private boolean isLicensed()
    {
        return this.licensor.hasLicensure(EXTENSION_ID);
    }

    /**
     * When on the login screen, decide if the LDAPAuthService should be used. Using LDAP means creating a new user if
     * the given credentials are valid and point to a user wasn't imported in XWiki yet. This might lead to
     * invalidation of the license.
     * <br>
     * This function will block logins of valid AD users which are not yet imported into XWiki, if the license cannot
     * support another user.
     *
     * @param username the user to check
     * @param context xwiki context
     * @return true if the given user should use the LDAPAuthService to log in
     */
    private boolean shouldUseADAuthService(String username, XWikiContext context)
    {
        License license = licensor.getLicense(EXTENSION_ID);
        DocumentReference userPage = activeDirectoryUserManager.getUserDocFromUsername(username, context);
        if (null == license) {
            return false;
        }
        try {
            if (isLicensed()) {
                // Don't allow the creation of new LDAP users if the license cannot hold more users.
                return !(license.getMaxUserCount() == userCounter.getUserCount() && null == userPage);
            } else {
                return license.getExpirationDate() > new Date().getTime() && userCounter.isUserUnderLimit(userPage,
                    license.getMaxUserCount());
            }
        } catch (Exception e) {
            LOGGER.error("Failed to decide if the Active Directory user should be allowed to log in. Cause: [{}]",
                ExceptionUtils.getRootCauseMessage(e));
            return false;
        }
    }
}
