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
import java.util.Comparator;
import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Provider;
import javax.inject.Singleton;

import org.apache.commons.lang3.exception.ExceptionUtils;
import org.slf4j.Logger;
import org.xwiki.component.annotation.Component;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.model.reference.LocalDocumentReference;
import org.xwiki.query.Query;
import org.xwiki.query.QueryException;
import org.xwiki.query.QueryFilter;
import org.xwiki.query.QueryManager;
import org.xwiki.wiki.descriptor.WikiDescriptorManager;
import org.xwiki.wiki.manager.WikiManagerException;

import com.xpn.xwiki.XWikiContext;
import com.xpn.xwiki.XWikiException;
import com.xpn.xwiki.doc.XWikiDocument;
import com.xpn.xwiki.user.impl.xwiki.XWikiAuthServiceImpl;
import com.xwiki.licensing.internal.AuthExtensionUserManager;

/**
 * Set the Active Directory authentication service as the default one when the application is installed.
 *
 * @version $Id$
 * @since 1.17.0
 */
@Component
@Singleton
@Named(ActiveDirectoryAuthServiceImpl.EXTENSION_ID)
public class ActiveDirectoryAuthExtensionUserManager implements AuthExtensionUserManager
{
    private static final String XWIKI_SPACE_NAME = "XWiki";

    private static final String USER_QUERY_LDAP_FILTER = "where doc.space = 'XWiki' "
        + "and doc.fullName = XWikiUserObj.name and XWikiUserObj.className = 'XWiki.XWikiUsers' "
        + "and doc.fullName = LDAPObj.name and LDAPObj.className = 'XWiki.LDAPProfileClass' ";

    private static final String USER_QUERY_SORT = "order by doc.creationDate";

    private static final String LDAP_USER_QUERY =
        "select doc from XWikiDocument doc, BaseObject as XWikiUserObj, BaseObject as LDAPObj " + USER_QUERY_LDAP_FILTER
            + USER_QUERY_SORT;

    private static final String LDAP_ACTIVE_USER_QUERY =
        "select doc from XWikiDocument doc, BaseObject as XWikiUserObj, BaseObject as LDAPObj"
            + ", IntegerProperty as IsActive " + USER_QUERY_LDAP_FILTER
            + "and XWikiUserObj.id=IsActive.id.id and IsActive.id.name='active' and IsActive.value = 1 "
            + USER_QUERY_SORT;

    private static final LocalDocumentReference LDAP_USER_CLASS_REFERENCE =
        new LocalDocumentReference(XWIKI_SPACE_NAME, "LDAPProfileClass");

    private static final LocalDocumentReference XWIKI_USER_CLASS_REFERENCE =
        new LocalDocumentReference(XWIKI_SPACE_NAME, "XWikiUsers");

    @Inject
    @Named("unique")
    private QueryFilter uniqueFilter;

    @Inject
    private Logger logger;

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

    @Override
    public boolean managesUser(XWikiDocument user)
    {
        return user.getXObject(XWIKI_USER_CLASS_REFERENCE) != null
            && user.getXObject(LDAP_USER_CLASS_REFERENCE) != null;
    }

    @Override
    public List<XWikiDocument> getManagedUsers()
    {
        return executeQueryOnAllWikis(LDAP_USER_QUERY);
    }

    @Override
    public List<XWikiDocument> getActiveManagedUsers()
    {
        return executeQueryOnAllWikis(LDAP_ACTIVE_USER_QUERY);
    }

    /**
     * Find the user's profile page by the username. Copied with changes from
     * {@link XWikiAuthServiceImpl#findUser(String, XWikiContext)} to fix XWIKI-21117: NPE in XWikiHibernateStore.search
     * in older versions of XWiki.
     * <p>
     * {@inheritDoc}
     */
    @Override
    public DocumentReference getUserDocFromUsername(String username, XWikiContext context)
    {
        if (username == null) {
            return null;
        }

        // First let's look in the cache.
        boolean matchesExactly = false;
        try {
            matchesExactly = context.getWiki()
                .exists(new DocumentReference(context.getWikiId(), XWIKI_SPACE_NAME, username), context);
        } catch (XWikiException e) {
            logger.error("Failed to verify existence of user page for username [{}]. Cause: [{}]", username,
                ExceptionUtils.getRootCauseMessage(e));
            return null;
        }

        String user;
        if (matchesExactly) {
            user = username;
        } else {
            // Note: The result of this search depends on the Database. If the database is
            // case-insensitive (like MySQL) then users will be able to log in by entering their
            // username in any case. For case-sensitive databases (like HSQLDB) they'll need to
            // enter it exactly as they've created it.
            List<String> results;
            try {
                // First, look for LDAP users.
                Query query = this.queryManager.createQuery(LDAP_USER_QUERY, Query.HQL);
                query.setWiki(context.getWikiId()).bindValue("username", username).setLimit(1);
                results = query.execute();
            } catch (QueryException e) {
                logger.error("Error while querying LDAP user pages. Cause: [{}]",
                    ExceptionUtils.getRootCauseMessage(e));
                return null;
            }
            if (results.isEmpty()) {
                return null;
            } else {
                user = results.get(0);
            }
        }

        return new DocumentReference(context.getWikiId(), XWIKI_SPACE_NAME, user);
    }

    private List<XWikiDocument> executeQueryOnAllWikis(String query)
    {
        SortedSet<XWikiDocument> ldapUsers = new TreeSet<>(Comparator.comparing(XWikiDocument::getCreationDate));
        try {
            for (String wikiId : wikiDescriptorManager.getAllIds()) {
                ldapUsers.addAll(executeQueryOnWiki(query, wikiId));
            }
        } catch (WikiManagerException e) {
            logger.error("Failed to get subwiki names. Cause: [{}]", ExceptionUtils.getRootCauseMessage(e));
        }
        return new ArrayList<>(ldapUsers);
    }

    private List<XWikiDocument> executeQueryOnWiki(String query, String wikiId)
    {
        try {
            return this.queryManager.createQuery(query, Query.HQL).setWiki(wikiId).addFilter(uniqueFilter).execute();
        } catch (QueryException e) {
            logger.error("Failed to get user count for wiki [{}]", wikiId);
            return List.of();
        }
    }
}
