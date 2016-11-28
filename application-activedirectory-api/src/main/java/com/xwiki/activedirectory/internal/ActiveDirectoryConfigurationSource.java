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
import org.xwiki.configuration.ConfigurationSource;
import org.xwiki.configuration.internal.AbstractXWikiPreferencesConfigurationSource;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.model.reference.SpaceReference;

/**
 * Active Directory LDAP configuration is stored in {@code ActiveDirectory.Code.ActiveDirectoryConfig}.
 *
 * @version $Id$
 * @since 1.1
 */
@Component
@Named("activedirectory")
@Singleton
public class ActiveDirectoryConfigurationSource extends AbstractXWikiPreferencesConfigurationSource
{
    private static final String TRYLOCAL_KEY = "ldap_trylocal";

    @Inject
    @Named("xwikicfg")
    private ConfigurationSource xwikicfgSource;

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
    protected <T> T getPropertyValue(String key, Class<T> valueClass)
    {
        T result = super.getPropertyValue(key, valueClass);

        // If the user has not set the trylocal property in the wiki and it's not defined in xwiki.cfg either
        // then set it to true by default (so that when AD license is active, if the user logs out, he can still log
        // in!)
        if (TRYLOCAL_KEY.equals(key) && result == null && this.xwikicfgSource.getProperty(TRYLOCAL_KEY) == null) {
            return (T) "1";
        }

        return result;
    }
}

