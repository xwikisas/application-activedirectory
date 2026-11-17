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

import javax.inject.Inject;
import javax.inject.Singleton;

import org.xwiki.component.annotation.Component;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.security.authorization.AuthorizationManager;
import org.xwiki.security.authorization.Right;

import com.xpn.xwiki.doc.XWikiDocument;

/**
 * Decides whether the Active Directory configuration stored in a document can be used. The configuration controls who
 * can authenticate and with which rights, so it is only used when it was last saved by a user who can administrate
 * the wiki holding it.
 *
 * @version $Id$
 * @since 1.18.3
 */
@Component(roles = ActiveDirectoryConfigurationChecker.class)
@Singleton
public class ActiveDirectoryConfigurationChecker
{
    @Inject
    private AuthorizationManager authorizationManager;

    /**
     * @param configurationDocument the document holding the Active Directory configuration
     * @return {@code true} if the configuration of the given document can be used, {@code false} otherwise
     */
    public boolean isTrusted(XWikiDocument configurationDocument)
    {
        if (configurationDocument == null) {
            return false;
        }
        DocumentReference author = configurationDocument.getAuthorReference();
        return author != null && this.authorizationManager.hasAccess(Right.ADMIN, author,
            configurationDocument.getDocumentReference().getWikiReference());
    }
}
