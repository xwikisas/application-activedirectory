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
package com.xwiki.activedirectory.test.po;

import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.FindBy;
import org.xwiki.administration.test.po.AdministrationSectionPage;

/**
 * The administration section page used to configure the Active Directory.
 * 
 * @version $Id$
 * @since 1.14
 */
public class ActiveDirectoryAdminPage extends AdministrationSectionPage
{
    private static final String SECTION_ID = "activeDirectory";

    private static final String VALUE = "value";

    @FindBy(id = "XWiki.XWikiPreferences_0_ldap_server")
    private WebElement serverAddressField;

    @FindBy(id = "XWiki.XWikiPreferences_0_ldap_port")
    private WebElement serverPortField;

    public static ActiveDirectoryAdminPage gotoPage()
    {
        AdministrationSectionPage.gotoPage(SECTION_ID);
        return new ActiveDirectoryAdminPage();
    }

    public ActiveDirectoryAdminPage()
    {
        super(SECTION_ID);
    }

    public String getServerAddress()
    {
        return this.serverAddressField.getAttribute(VALUE);
    }

    public String getServerPort()
    {
        return this.serverPortField.getAttribute(VALUE);
    }
}
