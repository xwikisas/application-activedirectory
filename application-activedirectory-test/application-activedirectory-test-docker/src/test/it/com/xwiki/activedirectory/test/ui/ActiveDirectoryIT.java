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
package com.xwiki.activedirectory.test.ui;

import org.junit.jupiter.api.Test;
import org.xwiki.test.docker.junit5.UITest;
import org.xwiki.test.ui.TestUtils;
import org.xwiki.test.ui.po.editor.ObjectEditPage;

import com.xwiki.activedirectory.test.po.ActiveDirectoryAdminPage;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Functional tests for Active Directory application.
 * 
 * @version $Id$
 */
@UITest
class ActiveDirectoryIT
{
    @Test
    void activeDirectoryConfiguration(TestUtils setup) throws Exception
    {
        createAdminUser(setup);
        ActiveDirectoryAdminPage adminPage = ActiveDirectoryAdminPage.gotoPage();

        // Assert the default configuration values.
        assertEquals("127.0.0.1", adminPage.getServerAddress());
        assertEquals("389", adminPage.getServerPort());

        // TODO: Add more tests.
    }

    /**
     * TODO: Replace with TestUtils#createAdminUser() after upgrading parent to XWiki 12.10.
     */
    void createAdminUser(TestUtils setup)
    {
        setup.loginAsSuperAdmin();
        setup.createUser(TestUtils.ADMIN_CREDENTIALS.getUserName(), TestUtils.ADMIN_CREDENTIALS.getPassword(), null);
        setup.addObject("XWiki", "XWikiAdminGroup", "XWiki.XWikiGroups", "member", "XWiki.Admin");
        setup.addObject("XWiki", "XWikiPreferences", "XWiki.XWikiGlobalRights", "groups", "XWiki.XWikiAdminGroup",
            "allow", 1, "levels", "admin");
        // Make sure the wiki administration is not locked by the superadmin user.
        new ObjectEditPage().clickCancel();
        setup.loginAsAdmin();
    }
}
