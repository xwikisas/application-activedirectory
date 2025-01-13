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

import java.util.Arrays;
import java.util.List;

import javax.annotation.Priority;
import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Provider;
import javax.inject.Singleton;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.slf4j.Logger;
import org.xwiki.component.annotation.Component;
import org.xwiki.component.phase.Initializable;
import org.xwiki.component.phase.InitializationException;
import org.xwiki.extension.InstalledExtension;
import org.xwiki.extension.event.ExtensionInstalledEvent;
import org.xwiki.extension.event.ExtensionUpgradedEvent;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.model.reference.LocalDocumentReference;
import org.xwiki.model.reference.WikiReference;
import org.xwiki.observation.AbstractEventListener;
import org.xwiki.observation.event.Event;

import com.xpn.xwiki.XWiki;
import com.xpn.xwiki.XWikiContext;
import com.xpn.xwiki.XWikiException;
import com.xpn.xwiki.doc.XWikiDocument;
import com.xpn.xwiki.objects.BaseObject;

/**
 * Set the Active Directory authentication service as the default one when the application is installed.
 *
 * @version $Id$
 * @since 1.17.0
 */
@Component
@Singleton
@Named(AuthServiceInitializer.ROLE_NAME)
@Priority(2000)
public class AuthServiceInitializer extends AbstractEventListener implements Initializable
{
    /**
     * The spaces in which the authentication configuration is stored.
     */
    public static final List<String> SPACES = Arrays.asList("XWiki", "AuthService");

    /**
     * The reference of the document holding the configuration of the authentication.
     */
    public static final LocalDocumentReference DOC_REFERENCE = new LocalDocumentReference(SPACES, "Configuration");

    /**
     * The role name of this component.
     */
    public static final String ROLE_NAME = "com.xwiki.activedirectory.internal.AuthServiceInitializer";

    /**
     * The reference to the document that acts as a flag. Its presence denotes that this listener was executed.
     */
    public static final LocalDocumentReference FLAG_REFERENCE =
        new LocalDocumentReference(Arrays.asList("ActiveDirectory", "Code"), "Flag - Version 1.17.0 Installed");

    /**
     * The reference for the Authentication service configuration object.
     */
    public static final LocalDocumentReference CLASS_REFERENCE =
        new LocalDocumentReference(SPACES, "ConfigurationClass");

    private static final String APPLICATION_ID = "com.xwiki.activedirectory:application-activedirectory-ui";

    @Inject
    private Provider<XWikiContext> contextProvider;

    @Inject
    private Logger logger;

    /**
     * Default constructor.
     */
    public AuthServiceInitializer()
    {
        super(ROLE_NAME, Arrays.asList(new ExtensionUpgradedEvent(), new ExtensionInstalledEvent()));
    }

    @Override
    public void onEvent(Event event, Object source, Object data)
    {
        InstalledExtension installedExtension = (InstalledExtension) source;

        if (APPLICATION_ID.equals(installedExtension.getId().getId())) {
            // We set the authenticator at app install/upgrade time to cover for the cases when the app is upgraded
            // or installed on a subwiki. The api needs to be installed on root, so it's not possible for this
            // listener to be initialized in a subwiki. Since the app can be configured per wiki, we need to try to
            // set it as a default authenticator at install time.
            String namespace = maybeGetNamespace(event);
            maybeSetActiveDirectoryAsDefaultAuthService(namespace);
        }
    }

    private String maybeGetNamespace(Event event)
    {
        // If the application ui is installed in a subwiki, the wiki present in the context seems to point at the
        // main wiki - possibly due to the fact that this module is installed as root OR this is how extension
        // installs work.
        String namespace = "";
        if (event instanceof ExtensionUpgradedEvent) {
            namespace = ((ExtensionUpgradedEvent) event).getNamespace();
        } else if (event instanceof ExtensionInstalledEvent) {
            namespace = ((ExtensionInstalledEvent) event).getNamespace();
        }
        return namespace;
    }

    @Override
    public void initialize() throws InitializationException
    {
        // We set the authenticator at initialization time to cover for the cases when the app is upgraded or
        // installed on the main wiki (the upgrade and install events are not caught since the listener was just
        // installed).
        maybeSetActiveDirectoryAsDefaultAuthService(null);
    }

    private void maybeSetActiveDirectoryAsDefaultAuthService(String namespace)
    {
        XWikiContext xcontext = contextProvider.get();
        if (xcontext == null) {
            return;
        }
        XWiki wiki = xcontext.getWiki();
        if (wiki == null) {
            return;
        }

        String wikiId =
            namespace != null && namespace.startsWith("wiki:") ? namespace.substring(5) : xcontext.getWikiId();

        WikiReference installationWiki = new WikiReference(wikiId);
        try {
            DocumentReference flagRef = new DocumentReference(FLAG_REFERENCE, installationWiki);
            if (wiki.exists(flagRef, xcontext)) {
                return;
            }
            XWikiDocument flagDoc = wiki.getDocument(flagRef, xcontext);
            flagDoc.setHidden(true);

            XWikiDocument configurationDocument =
                wiki.getDocument(new DocumentReference(DOC_REFERENCE, installationWiki), xcontext);
            configurationDocument.setHidden(true);

            BaseObject configurationObject =
                configurationDocument.getXObject(CLASS_REFERENCE, true, xcontext);

            // The contrib version of the Auth Backport app uses "service" key while the platform version uses
            // "authService" key.
            configurationObject.setStringValue("authService",
                StringUtils.defaultString(ActiveDirectoryAuthService.ID));
            configurationObject.setStringValue("service", ActiveDirectoryAuthService.ID);

            wiki.saveDocument(configurationDocument, "Change authenticator service", xcontext);
            wiki.saveDocument(wiki.getDocument(flagRef, xcontext), xcontext);
        } catch (XWikiException e) {
            logger.warn("Could not set the Active Directory authenticator as default. Cause: [{}].",
                ExceptionUtils.getRootCauseMessage(e));
        }
    }
}
