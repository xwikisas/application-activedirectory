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
package org.xwiki.activedirectory.internal;

import javax.inject.Singleton;

import org.xwiki.component.annotation.Component;
import org.xwiki.extension.ExtensionId;
import org.xwiki.model.reference.EntityReference;

import com.xwiki.licensing.License;
import com.xwiki.licensing.Licensor;

/**
 * Bypass the Licensing module so that we can test the AD module without a license.
 *
 * @version $Id$
 */
@Component
@Singleton
public class VoidLicensor implements Licensor
{
    @Override
    public License getLicense()
    {
        return null;
    }

    @Override
    public License getLicense(ExtensionId extensionId)
    {
        return null;
    }

    @Override
    public License getLicense(EntityReference entityReference)
    {
        return null;
    }

    @Override
    public boolean hasLicensure()
    {
        return true;
    }

    @Override
    public boolean hasLicensure(EntityReference entityReference)
    {
        return true;
    }

    @Override
    public boolean hasLicensure(ExtensionId extensionId)
    {
        return true;
    }
}
