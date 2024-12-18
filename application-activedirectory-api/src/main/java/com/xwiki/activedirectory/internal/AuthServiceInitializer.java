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
import java.util.Collections;
import java.util.List;

import javax.annotation.Priority;
import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Provider;
import javax.inject.Singleton;

import org.apache.commons.lang3.StringUtils;
import org.xwiki.component.annotation.Component;
import org.xwiki.component.phase.Initializable;
import org.xwiki.component.phase.InitializationException;
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
 * Some info.
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
     * The spaces in which the authentication configuration is stored.
     */
    public static final String SPACES_STRING = "XWiki.AuthService";

    /**
     * The reference of the document holding the configuration of the authentication.
     */
    public static final LocalDocumentReference DOC_REFERENCE = new LocalDocumentReference(SPACES, "Configuration");

    /**
     * Some stuff.
     */
    public static final String ROLE_NAME = "com.xwiki.activedirectory.internal.AuthServiceInitializer";

    /**
     * Some stuff.
     */
    public static final LocalDocumentReference FLAG_REFERENCE =
        new LocalDocumentReference(Arrays.asList("ActiveDirectory", "Code"), "Flag - Version 1.17.0 Installed");

    /**
     * Some stuff.
     */
    public static final LocalDocumentReference CLASS_REFERENCE =
        new LocalDocumentReference(SPACES, "ConfigurationClass");

    @Inject
    private Provider<XWikiContext> contextProvider;

    /**
     * Default const.
     */
    public AuthServiceInitializer()
    {
        super(ROLE_NAME, Collections.emptyList());
    }

    @Override
    public void onEvent(Event event, Object source, Object data)
    {
        // Do nothing.
    }

    @Override
    public void initialize() throws InitializationException
    {
        XWikiContext xcontext = contextProvider.get();
        if (xcontext == null) {
            return;
        }
        XWiki wiki = xcontext.getWiki();
        if (wiki == null) {
            return;
        }

        try {
            DocumentReference flagRef = new DocumentReference(FLAG_REFERENCE, new WikiReference(xcontext.getWikiId()));
            if (wiki.exists(flagRef, xcontext)) {
                return;
            }

            XWikiDocument configurationDocument =
                wiki.getDocument(new DocumentReference(DOC_REFERENCE, new WikiReference(xcontext.getWikiId())),
                    xcontext);
//            if (configurationDocument.isNew()) {
//                return;
//            }
            BaseObject configurationObject =
                configurationDocument.getXObject(CLASS_REFERENCE, true, xcontext);

            configurationObject.setStringValue("authService", StringUtils.defaultString(ActiveDirectoryAuthService.ID));

            wiki.saveDocument(configurationDocument, "Change authenticator service", xcontext);

            wiki.saveDocument(wiki.getDocument(flagRef, xcontext), xcontext);
        } catch (XWikiException e) {
        }
    }
}
