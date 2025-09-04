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

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import org.slf4j.Logger;
import org.xwiki.bridge.event.DocumentCreatingEvent;
import org.xwiki.bridge.event.DocumentUpdatingEvent;
import org.xwiki.component.annotation.Component;
import org.xwiki.extension.InstalledExtension;
import org.xwiki.extension.repository.InstalledExtensionRepository;
import org.xwiki.model.reference.LocalDocumentReference;
import org.xwiki.observation.AbstractEventListener;
import org.xwiki.observation.event.Event;
import org.xwiki.observation.event.filter.EventFilter;
import org.xwiki.observation.event.filter.RegexEventFilter;

import com.xpn.xwiki.XWikiContext;
import com.xpn.xwiki.doc.XWikiDocument;
import com.xwiki.licensing.Licensor;

/**
 * Ensure disabled LDAP users remain disabled when they have no license for Active Directory.
 *
 * @version $Id$
 * @since 1.17.0
 */
@Component
@Singleton
@Named(UserLicenseEnforcer.ROLE_NAME)
public class UserLicenseEnforcer extends AbstractEventListener
{
    /**
     * The role name of this component.
     */
    public static final String ROLE_NAME = "com.xwiki.activedirectory.internal.UserLicenseEnforcer";

    private static final String EXTENSION_ID = "com.xwiki.activedirectory:application-activedirectory-api";

    private static final EventFilter XWIKI_SPACE_FILTER = new RegexEventFilter("(.*:)?XWiki\\..*");

    private static final String XWIKI = "XWiki";

    private static final LocalDocumentReference LDAP_USER_CLASS_REFERENCE =
        new LocalDocumentReference(XWIKI, "LDAPProfileClass");

    private static final LocalDocumentReference XWIKI_USER_CLASS_REFERENCE =
        new LocalDocumentReference(XWIKI, "XWikiUsers");

    @Inject
    private Logger logger;

    @Inject
    private Licensor licensor;

    @Inject
    private InstalledExtensionRepository repository;

    /**
     * Default constructor.
     */
    public UserLicenseEnforcer()
    {
        super(ROLE_NAME,
            Arrays.asList(new DocumentCreatingEvent(XWIKI_SPACE_FILTER),
                new DocumentUpdatingEvent(XWIKI_SPACE_FILTER)));
    }

    @Override
    public void onEvent(Event event, Object source, Object data)
    {
        boolean isLicensed = false;
        XWikiDocument sourceDocument = (XWikiDocument) source;
        XWikiContext xcontext = (XWikiContext) data;
        // Only disable LDAP users.
        if (null == sourceDocument.getXObject(LDAP_USER_CLASS_REFERENCE)) {
            return;
        }

        InstalledExtension mainExtension = this.repository.getInstalledExtension(EXTENSION_ID, null);
        if (mainExtension != null) {
            isLicensed = this.licensor.hasLicensure(mainExtension.getId(), sourceDocument.getDocumentReference());
        }

        if (!isLicensed) {
            sourceDocument.getXObject(XWIKI_USER_CLASS_REFERENCE).set("active", 0, xcontext);
//            try {
//                xcontext.getWiki().saveDocument(sourceDocument, "Enforce Active Directory license.", xcontext);
//            } catch (XWikiException e) {
//                logger.error("Failed to enforce Active Directory license. Cause: [{}]",
//                    ExceptionUtils.getRootCauseMessage(e));
//            }
        }
    }
}
