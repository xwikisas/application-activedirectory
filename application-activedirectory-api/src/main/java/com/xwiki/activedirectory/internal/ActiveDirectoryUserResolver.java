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

import java.util.List;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import org.apache.commons.lang3.exception.ExceptionUtils;
import org.slf4j.Logger;
import org.xwiki.component.annotation.Component;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.query.Query;
import org.xwiki.query.QueryException;
import org.xwiki.query.QueryManager;

import com.xpn.xwiki.XWikiContext;
import com.xpn.xwiki.XWikiException;

/**
 * Set the Active Directory authentication service as the default one when the application is installed.
 *
 * @version $Id$
 * @since 1.17.0
 */
@Component(roles = ActiveDirectoryUserResolver.class)
@Singleton
@Named("activedirectory-username-resolver")
public class ActiveDirectoryUserResolver
{
    private static final String XWIKI_SPACE_NAME = "XWiki";

    private static final String USER_DOC_QUERY = "select doc.name from XWikiDocument doc, BaseObject as XWikiUserObj,"
        + " BaseObject as LDAPObj, StringProperty as prop where doc.space = 'XWiki'"
        + " and doc.fullName = XWikiUserObj.name and XWikiUserObj.className = 'XWiki.XWikiUsers'"
        + " and doc.fullName = LDAPObj.name and LDAPObj.className = 'XWiki.LDAPProfileClass'"
        + " and LDAPObj.id=prop.id.id and prop.id.name='uid' and prop.value = :username order by doc.creationDate";

    @Inject
    private Logger logger;

    @Inject
    private QueryManager queryManager;

    /**
     * Since Active Directory usernames differ from the XWiki page they are stored in, we need to resolve the usernames
     * separately.
     *
     * @param username the username of the user to resolve
     * @param context the current context
     * @return a document reference if the user page is found, null otherwise
     */
    public DocumentReference resolve(String username, XWikiContext context)
    {
        if (username == null) {
            return null;
        }

        // First, check the normal user page location.
        try {
            DocumentReference userRef = new DocumentReference(context.getWikiId(), XWIKI_SPACE_NAME, username);
            if (context.getWiki().exists(userRef, context)) {
                return userRef;
            }
        } catch (XWikiException e) {
            logger.error("Failed to verify existence of user page for username [{}]. Cause: [{}]", username,
                ExceptionUtils.getRootCauseMessage(e));
            return null;
        }

        // Then, look for LDAP users, for which the page name might be different from the username.
        List<String> results;
        try {
            Query query = this.queryManager.createQuery(USER_DOC_QUERY, Query.HQL);
            query.setWiki(context.getWikiId()).bindValue("username", username).setLimit(1);
            results = query.execute();
        } catch (QueryException e) {
            logger.error("Error while querying LDAP user pages. Cause: [{}]", ExceptionUtils.getRootCauseMessage(e));
            results = List.of();
        }
        if (results.isEmpty()) {
            return null;
        } else {
            return new DocumentReference(context.getWikiId(), XWIKI_SPACE_NAME, results.get(0));
        }
    }
}
