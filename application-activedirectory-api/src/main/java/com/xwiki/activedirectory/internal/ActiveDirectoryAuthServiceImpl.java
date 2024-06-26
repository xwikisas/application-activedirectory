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

import org.apache.commons.lang3.StringUtils;
import org.xwiki.configuration.ConfigurationSource;
import org.xwiki.contrib.ldap.XWikiLDAPAuthServiceImpl;
import org.xwiki.contrib.ldap.XWikiLDAPConfig;
import org.xwiki.extension.InstalledExtension;
import org.xwiki.extension.repository.InstalledExtensionRepository;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.model.reference.SpaceReference;

import com.xpn.xwiki.XWiki;
import com.xpn.xwiki.XWikiContext;
import com.xpn.xwiki.XWikiException;
import com.xpn.xwiki.doc.XWikiDocument;
import com.xpn.xwiki.objects.BaseObject;
import com.xpn.xwiki.user.api.XWikiAuthService;
import com.xpn.xwiki.user.api.XWikiUser;
import com.xpn.xwiki.web.Utils;
import com.xwiki.licensing.Licensor;

/**
 * Custom Active Directory authenticator that currently only serves as a way to make sure that a valid License for the
 * Active Directory application exists in order to authenticate requests. In the future it'll also serve as the place to
 * add new features for Active Directory authentication.
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

    private static final SpaceReference AD_CODE_SPACE_REFERENCE =
        new SpaceReference("xwiki", Arrays.asList("ActiveDirectory", "Code"));

    private Licensor licensor = Utils.getComponent(Licensor.class);

    private InstalledExtensionRepository repository = Utils.getComponent(InstalledExtensionRepository.class);

    private final XWikiAuthService fallbackAuthService;

    private ConfigurationSource configurationSource = Utils.getComponent(ConfigurationSource.class, "activedirectory");

    /**
     * @param oldAuthService the auth service that was registered before this one. Used to fallback in case the
     *     authentication fails or if there is no license.
     */
    public ActiveDirectoryAuthServiceImpl(XWikiAuthService oldAuthService)
    {
        this.fallbackAuthService = oldAuthService;
    }

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
            XWikiUser user = super.checkAuth(context);
            if (user != null) {
                return user;
            }
        }
        return this.fallbackAuthService.checkAuth(context);
    }

    @Override
    public XWikiUser checkAuth(String username, String password, String rememberme, XWikiContext context)
        throws XWikiException
    {
        if (isLicensed()) {
            XWikiUser user = super.checkAuth(username, password, rememberme, context);
            if (user != null) {
                return user;
            }
        }
        return this.fallbackAuthService.checkAuth(username, password, rememberme, context);
    }

    @Override
    public Principal authenticate(String userId, String password, XWikiContext context) throws XWikiException
    {
        if (isLicensed()) {
            Principal principal = super.authenticate(userId, password, context);
            if (principal != null) {
                return principal;
            }
        }
        return this.fallbackAuthService.authenticate(userId, password, context);
    }

    private boolean isLicensed()
    {
        boolean isLicensed = false;

        // The Licensor expects a version so we need to discover the installed version for now. Hopefully in the future
        // the Licensor could find it how automatically.
        // Note: The AD authenticator is installed in the root namespace, hence the passing of "null" in the call below.
        InstalledExtension mainExtension = this.repository.getInstalledExtension(EXTENSION_ID, null);
        if (mainExtension != null) {
            isLicensed = this.licensor.hasLicensure(mainExtension.getId());
        }
        return isLicensed;
    }
}
