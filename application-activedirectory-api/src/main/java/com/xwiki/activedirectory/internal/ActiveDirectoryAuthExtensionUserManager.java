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

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Provider;
import javax.inject.Singleton;

import org.apache.commons.lang3.exception.ExceptionUtils;
import org.slf4j.Logger;
import org.xwiki.component.annotation.Component;
import org.xwiki.extension.ExtensionId;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.model.reference.LocalDocumentReference;
import org.xwiki.query.Query;
import org.xwiki.query.QueryException;
import org.xwiki.query.QueryManager;
import org.xwiki.wiki.descriptor.WikiDescriptorManager;
import org.xwiki.wiki.manager.WikiManagerException;

import com.xpn.xwiki.XWikiContext;
import com.xpn.xwiki.XWikiException;
import com.xpn.xwiki.doc.XWikiDocument;
import com.xwiki.licensing.Licensor;
import com.xwiki.licensing.internal.AuthExtensionUserManager;

/**
 * Set the Active Directory authentication service as the default one when the application is installed.
 *
 * @version $Id$
 * @since 1.17.0
 */
@Component
@Singleton
@Named(ActiveDirectoryAuthExtensionUserManager.EXTENSION_ID)
public class ActiveDirectoryAuthExtensionUserManager implements AuthExtensionUserManager
{
    protected static final String EXTENSION_ID = "com.xwiki.activedirectory:application-activedirectory-api";

    private static final String XWIKI_SPACE_NAME = "XWiki";

    private static final String LDAP_USER_QUERY =
        "select doc from XWikiDocument doc, BaseObject as XWikiUserObj, BaseObject as LDAPObj "
            + "where doc.space = 'XWiki' "
            + "and doc.fullName = XWikiUserObj.name and XWikiUserObj.className = 'XWiki.XWikiUsers' "
            + "and doc.fullName = LDAPObj.name and LDAPObj.className = 'XWiki.LDAPProfileClass' ";

    private static final LocalDocumentReference LDAP_USER_CLASS_REFERENCE =
        new LocalDocumentReference(XWIKI_SPACE_NAME, "LDAPProfileClass");

    private static final LocalDocumentReference XWIKI_USER_CLASS_REFERENCE =
        new LocalDocumentReference(XWIKI_SPACE_NAME, "XWikiUsers");

    @Inject
    private Logger logger;

    @Inject
    private Licensor licensor;

//    @Inject
//    private InstalledExtensionRepository repository;
//
//    @Inject
//    private UserCounter userCounter;

    @Inject
    private WikiDescriptorManager wikiDescriptorManager;

    @Inject
    private QueryManager queryManager;

    @Inject
    private Provider<XWikiContext> contextProvider;

    @Override
    public boolean managesUser(DocumentReference user)
    {
        if (user == null) {
            return false;
        }
        try {
            return managesUser(contextProvider.get().getWiki().getDocument(user, contextProvider.get()));
        } catch (XWikiException e) {
            throw new RuntimeException(e);
        }
    }

//    //    @Inject
////    private DocumentReferenceResolver<UserReference> documentReferenceResolver;
//    @Override
//    public boolean managesUser(UserReference userReference)
//    {
//        if (userReference.equals(SuperAdminUserReference.INSTANCE) || userReference.equals(
//            GuestUserReference.INSTANCE))
//        {
//            return false;
//        }
////        DocumentReference user;
////        if (CurrentUserReference.INSTANCE
//        return false;

    /// /        return managesUser(documentReferenceResolver.resolve(userReference));
//    }
    @Override
    public boolean managesUser(XWikiDocument user)
    {
        return user.getXObject(XWIKI_USER_CLASS_REFERENCE) != null
            && user.getXObject(LDAP_USER_CLASS_REFERENCE) != null;
    }

    @Override
    public boolean shouldBeActive(DocumentReference user)
    {
//        InstalledExtension mainExtension = this.repository.getInstalledExtension(EXTENSION_ID, null);
//        License license = licensor.getLicense(mainExtension.getId());
//        try {
//            if (license.getExpirationDate() > new Date().getTime()) {
//                return false;
//            } else if (userCounter.getUserCount() <= license.getMaxUserCount()) {
////                 getManagedUsers() <= license.getMaxUserCount()
//                return true;
//            }
//        } catch (Exception e) {
//            logger.error("Failed to get license user count for Active Directory user license check. Cause: [{}]",
//                ExceptionUtils.getRootCauseMessage(e));
//            return true;
//        }
        return licensor.hasLicensure(new ExtensionId(EXTENSION_ID), user);
    }

    @Override
    public Set<XWikiDocument> getManagedUsers()
    {
        Set<XWikiDocument> ldapUsers = new HashSet<>();
        try {
            for (String wikiId : wikiDescriptorManager.getAllIds()) {
                try {
                    ldapUsers.addAll(getUsersOnWiki(wikiId));
                } catch (QueryException e) {
                    logger.error("Failed to get users managed by Active Directory on wiki [{}]. Cause: [{}]", wikiId,
                        ExceptionUtils.getRootCauseMessage(e));
                }
            }
        } catch (WikiManagerException e) {
            logger.error("Failed to get subwiki names. Cause: [{}]", ExceptionUtils.getRootCauseMessage(e));
        }
        return ldapUsers;
    }

    private List<XWikiDocument> getUsersOnWiki(String wikiId) throws QueryException
    {
        return this.queryManager.createQuery(LDAP_USER_QUERY, Query.HQL).setWiki(wikiId).execute();
    }
}
