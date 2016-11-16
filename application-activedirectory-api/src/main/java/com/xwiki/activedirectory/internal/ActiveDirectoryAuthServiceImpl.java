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

import org.xwiki.configuration.ConfigurationSource;
import org.xwiki.contrib.ldap.XWikiLDAPAuthServiceImpl;
import org.xwiki.contrib.ldap.XWikiLDAPConfig;
import org.xwiki.extension.InstalledExtension;
import org.xwiki.extension.repository.InstalledExtensionRepository;

import com.xpn.xwiki.XWikiContext;
import com.xpn.xwiki.XWikiException;
import com.xpn.xwiki.user.api.XWikiAuthService;
import com.xpn.xwiki.user.api.XWikiUser;
import com.xpn.xwiki.user.impl.xwiki.XWikiAuthServiceImpl;
import com.xpn.xwiki.web.Utils;
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
    private static final String EXTENSION_ID = "com.xwiki.activedirectory:application-activedirectory-api";

    private Licensor licensor = Utils.getComponent(Licensor.class);

    private InstalledExtensionRepository repository = Utils.getComponent(InstalledExtensionRepository.class);

    private XWikiAuthService fallbackAuthService = new XWikiAuthServiceImpl();

    private ConfigurationSource configurationSource = Utils.getComponent(ConfigurationSource.class, "activedirectory");

    @Override
    protected XWikiLDAPConfig createXWikiLDAPConfig(String authInput)
    {
        return new XWikiLDAPConfig(authInput, this.configurationSource);
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
        if (isLicensed()) {
            return super.authenticate(userId, password, context);
        } else {
            return this.fallbackAuthService.authenticate(userId, password, context);
        }
    }

    private boolean isLicensed()
    {
        boolean isLicensed = false;

        // The Licensor expects a version so we need to discover the installed version for now. Hopefully in the future
        // the Licensor could find it how automatically.
        InstalledExtension mainExtension = this.repository.getInstalledExtension(EXTENSION_ID, null);
        if (mainExtension != null) {
            isLicensed = this.licensor.hasLicensure(mainExtension.getId());
        }
        return isLicensed;
    }
}
