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
import javax.inject.Named;
import javax.inject.Singleton;

import org.xwiki.component.annotation.Component;
import org.xwiki.configuration.internal.AbstractXWikiPreferencesConfigurationSource;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.model.reference.SpaceReference;

import com.xpn.xwiki.XWikiException;
import com.xpn.xwiki.doc.XWikiDocument;
import com.xpn.xwiki.objects.BaseObject;

/**
 * Active Directory LDAP configuration is stored in {@code ActiveDirectory.Code.ActiveDirectoryConfig}. The stored
 * configuration is ignored when it was not last saved by a wiki administrator (see
 * {@link ActiveDirectoryConfigurationChecker}).
 *
 * @version $Id$
 * @since 1.1
 */
@Component
@Named("activedirectory")
@Singleton
public class ActiveDirectoryConfigurationSource extends AbstractXWikiPreferencesConfigurationSource
{
    @Inject
    private ActiveDirectoryConfigurationChecker configurationChecker;

    @Override
    protected String getCacheId()
    {
        return "configuration.activedirectory.wiki";
    }

    @Override
    protected String getCacheKeyPrefix()
    {
        return this.wikiManager.getCurrentWikiId();
    }

    @Override
    protected DocumentReference getDocumentReference()
    {
        return new DocumentReference("ActiveDirectoryConfig", new SpaceReference("Code",
            new SpaceReference("ActiveDirectory", getCurrentWikiReference())));
    }

    @Override
    protected BaseObject getBaseObject() throws XWikiException
    {
        BaseObject baseObject = super.getBaseObject();
        return baseObject != null && isTrusted(baseObject.getOwnerDocument()) ? baseObject : null;
    }

    @Override
    protected BaseObject getBaseObject(XWikiDocument document, String language)
    {
        return isTrusted(document) ? super.getBaseObject(document, language) : null;
    }

    private boolean isTrusted(XWikiDocument document)
    {
        boolean trusted = this.configurationChecker.isTrusted(document);
        if (!trusted && document != null && !document.isNew()) {
            this.logger.debug("Ignoring the Active Directory configuration from [{}] because its author [{}] is not"
                + " allowed to administrate the wiki.", document.getDocumentReference(), document.getAuthorReference());
        }
        return trusted;
    }
}
