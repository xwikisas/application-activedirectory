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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import org.apache.commons.lang3.exception.ExceptionUtils;
import org.slf4j.Logger;
import org.xwiki.bridge.event.DocumentCreatedEvent;
import org.xwiki.bridge.event.DocumentDeletedEvent;
import org.xwiki.bridge.event.DocumentUpdatedEvent;
import org.xwiki.component.annotation.Component;
import org.xwiki.extension.InstalledExtension;
import org.xwiki.extension.repository.InstalledExtensionRepository;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.model.reference.LocalDocumentReference;
import org.xwiki.observation.AbstractEventListener;
import org.xwiki.observation.event.Event;
import org.xwiki.observation.event.filter.EventFilter;
import org.xwiki.observation.event.filter.RegexEventFilter;
import org.xwiki.query.Query;
import org.xwiki.query.QueryException;
import org.xwiki.query.QueryManager;
import org.xwiki.wiki.descriptor.WikiDescriptorManager;
import org.xwiki.wiki.manager.WikiManagerException;

import com.xpn.xwiki.XWikiContext;
import com.xpn.xwiki.XWikiException;
import com.xpn.xwiki.doc.XWikiDocument;
import com.xpn.xwiki.doc.rcs.XWikiRCSNodeInfo;
import com.xwiki.licensing.License;
import com.xwiki.licensing.Licensor;
import com.xwiki.licensing.internal.UserCounter;

/**
 * Disable LDAP users who are over the user limit of the Active Directory license.
 *
 * @version $Id$
 * @since 1.17.0
 */
@Component
@Singleton
@Named(UserLicenseDisabler.ROLE_NAME)
public class UserLicenseDisabler extends AbstractEventListener
{
    /**
     * The role name of this component.
     */
    public static final String ROLE_NAME = "com.xwiki.activedirectory.internal.UserLicenseDisabler";

    private static final String EDIT_MESSAGE_DISABLE = "Active Directory user disabled to enforce license.";

    private static final String EDIT_MESSAGE_ENABLE = "Active Directory user activated to enforce license.";

    private static final String EXTENSION_ID = "com.xwiki.activedirectory:application-activedirectory-api";

    private static final EventFilter XWIKI_SPACE_FILTER = new RegexEventFilter("(.*:)?XWiki\\..*");

    private static final String ACTIVE = "active";

    private static final DocumentReference XWIKI_USER_CLASS_REFERENCE =
        new DocumentReference("xwiki", "XWiki", "XWikiUsers");

    private static final String LDAP_USER_QUERY =
        "select doc from XWikiDocument doc, BaseObject as XWikiUserObj, BaseObject as LDAPObj "
            + "where doc.space = 'XWiki' "
            + "and doc.fullName = XWikiUserObj.name and XWikiUserObj.className = 'XWiki.XWikiUsers' "
            + "and doc.fullName = LDAPObj.name and LDAPObj.className = 'XWiki.LDAPProfileClass' ";
//            + "and prop.id.id = XWikiUserObj.id and prop.id.name = 'active' and prop.value = '1'";

    @Inject
    private Logger logger;

    @Inject
    private Licensor licensor;

//    @Inject
//    private LicenseValidator licenseValidator;

    @Inject
    private InstalledExtensionRepository repository;

    @Inject
    private UserCounter userCounter;

    @Inject
    private WikiDescriptorManager wikiDescriptorManager;

    @Inject
    private QueryManager queryManager;

//    @Inject
//    private DocumentRevisionProvider documentRevisionProvider;

    /**
     * Default constructor.
     */
    public UserLicenseDisabler()
    {
        super(ROLE_NAME,
            Arrays.asList(new DocumentCreatedEvent(XWIKI_SPACE_FILTER), new DocumentUpdatedEvent(XWIKI_SPACE_FILTER),
                new DocumentDeletedEvent(XWIKI_SPACE_FILTER)));
        //TODO: Add license updated event? Does something like that even exist?
    }

    @Override
    public void onEvent(Event event, Object source, Object data)
    {
        InstalledExtension mainExtension = this.repository.getInstalledExtension(EXTENSION_ID, null);
        License license = licensor.getLicense(mainExtension.getId());
        try {
//            licenseValidator.isValid(license);
            if (userCounter.getUserCount() <= license.getMaxUserCount()) {
                return;
            }
        } catch (Exception e) {
            logger.error("Failed to get license user count for Active Directory user license check. Cause: [{}]",
                ExceptionUtils.getRootCauseMessage(e));
            return;
        }

        try {
            Set<XWikiDocument> LDAPUsers = getLDAPUsers();
            // Remove LDAP users under limit.
            new ArrayList<>(userCounter.getOldestUsers()).subList(0, (int) license.getMaxUserCount())
                .forEach(LDAPUsers::remove);
            LDAPUsers.forEach(user -> updateUserPage(user, (XWikiContext) data));
        } catch (Exception e) {
            logger.error("Failed to update allowed LDAP users for the license. Cause: [{}]",
                ExceptionUtils.getRootCauseMessage(e));
        }
    }

    private void updateUserPage(XWikiDocument user, XWikiContext xcontext)
    {
        InstalledExtension mainExtension = this.repository.getInstalledExtension(EXTENSION_ID, null);
        boolean isLicensed = false;
        if (mainExtension != null) {
            isLicensed = this.licensor.hasLicensure(mainExtension.getId(), user.getDocumentReference());
        }
        boolean changeNeeded = isLicensed == (1 == user.getXObject(XWIKI_USER_CLASS_REFERENCE).getIntValue(ACTIVE));

        if (changeNeeded) {
            boolean activeToSet = isLicensed;
            if (isLicensed) {
                XWikiRCSNodeInfo lastVersion = user.getDocumentArchive().getNodes().stream()
                    .dropWhile(ver -> !ver.getComment().equals(EDIT_MESSAGE_DISABLE)).skip(1).findFirst().orElse(null);

                if (lastVersion != null) {
                    // If the user was disabled before the licensor came into effect, preserve that last
                    // known state by treating the revision comment as a flag.
                    try {
//                        XWikiDocument xdoc = documentRevisionProvider.getRevision(user.getDocumentReference(),
//                            lastVersion.getVersion().toString());
                        XWikiDocument xdoc = xcontext.getWiki()
                            .getDocument(user.getDocumentReference(), lastVersion.getVersion().toString(), xcontext);
                        activeToSet = 1 == xdoc.getXObject(XWIKI_USER_CLASS_REFERENCE).getIntValue(ACTIVE);
                    } catch (XWikiException e) {
                        logger.warn("Failed to get last revision of user profile page [{}] when enforcing Active "
                            + "Directory license. Cause: [{}]", user, ExceptionUtils.getRootCauseMessage(e));
                    }
                }
            }
            String editMessage = isLicensed ? EDIT_MESSAGE_ENABLE : EDIT_MESSAGE_DISABLE;
            user.getXObject(XWIKI_USER_CLASS_REFERENCE).set(ACTIVE, activeToSet, xcontext);
            try {
                xcontext.getWiki().saveDocument(user, editMessage, xcontext);
            } catch (XWikiException e) {
                logger.error("Failed to save LDAP user profile for [{}] when enforcing the Active Directory license. "
                    + "Cause: [{}]", user, ExceptionUtils.getRootCauseMessage(e));
            }
        }
    }

    private Set<XWikiDocument> getLDAPUsers() throws QueryException, WikiManagerException
    {
        Set<XWikiDocument> LDAPUsers = new HashSet<>();
        for (String wikiId : wikiDescriptorManager.getAllIds()) {
            LDAPUsers.addAll(getUsersOnWiki(wikiId));
        }
        return LDAPUsers;
    }

    private List<XWikiDocument> getUsersOnWiki(String wikiId) throws QueryException
    {
        return this.queryManager.createQuery(LDAP_USER_QUERY, Query.HQL).setWiki(wikiId).execute();
    }
}
