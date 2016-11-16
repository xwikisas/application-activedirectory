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

import java.io.IOException;

import javax.inject.Named;
import javax.inject.Singleton;

import org.xwiki.component.annotation.Component;
import org.xwiki.extension.ExtensionId;
import org.xwiki.model.reference.EntityReference;
import org.xwiki.script.service.ScriptService;
import org.xwiki.security.authorization.AccessDeniedException;

import com.xwiki.licensing.License;
import com.xwiki.licensing.LicenseManager;
import com.xwiki.licensing.Licensor;

/**
 * Bypass the Licensing module so that we can test the AD module without a license.
 *
 * @version $Id$
 */
@Component
@Named("licensor")
@Singleton
public class VoidLicensorScriptService implements ScriptService
{
    public License getLicense()
    {
        return null;
    }

    public License getLicenseForExtension(ExtensionId extensionId)
    {
        return null;
    }

    public License getLicenseForEntity(EntityReference reference)
    {
        return null;
    }

    public boolean hasLicensure()
    {
        return true;
    }

    public boolean hasLicensureForExtension(ExtensionId extensionId)
    {
        return true;
    }

    public boolean hasLicensureForEntity(EntityReference reference)
    {
        return true;
    }

    public Licensor getLicensor()
    {
        return null;
    }

    public LicenseManager getLicenseManager()
    {
        return null;
    }

    public boolean addLicense(String license) throws AccessDeniedException, IOException
    {
        return true;
    }

    public boolean addLicense(byte[] license) throws AccessDeniedException, IOException
    {
        return true;
    }

    public void checkLicense()
    {
    }
}
