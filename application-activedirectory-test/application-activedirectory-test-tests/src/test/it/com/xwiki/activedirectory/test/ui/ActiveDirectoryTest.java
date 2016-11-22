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

import org.junit.Rule;
import org.junit.Test;
import org.xwiki.test.ui.AbstractTest;
import org.xwiki.test.ui.SuperAdminAuthenticationRule;
import org.xwiki.test.ui.po.ViewPage;

import static junit.framework.TestCase.assertEquals;

public class ActiveDirectoryTest extends AbstractTest
{
    private static final String ID = "com.xwiki.activedirectory:application-activedirectory-entry";

    private static final String VERSION = System.getProperty("activedirectory.version");

    @Rule
    public SuperAdminAuthenticationRule superAdminAuthenticationRule = new SuperAdminAuthenticationRule(getUtil());

    @Test
    public void validateActiveDirectoryFeatures() throws Exception
    {
        // Delete pages that we create in the test
        getUtil().rest().deletePage(getTestClassName(), "InstallADExtension");
        getUtil().rest().deletePage(getTestClassName(), "FakeLicensing");

        // Create a page in which we install the AD application and verify it's been installed correctly
        String content = "{{velocity}}\n"
            + "#set ($job = $services.extension.install('" + ID + "', '" + VERSION + "', 'wiki:xwiki'))\n"
            + "#set ($discard = $job.join())\n"
            + "installed: $services.extension.installed.getInstalledExtension('" + ID + "', 'wiki:xwiki').id\n"
            + "{{/velocity}}";
        ViewPage vp = getUtil().createPage(getTestClassName(), "InstallADExtension", content, "AD Test");
        assertEquals("installed: com.xwiki.activedirectory:application-activedirectory-entry-" + VERSION,
            vp.getContent());

        // Register fake Licensor components to avoid checking for licenses and be able to focus the test on the
        // Active Directory feature.
        content = "{{groovy}}\n"
            + "import javax.inject.*\n"
            + "import org.xwiki.component.annotation.*\n"
            + "import org.xwiki.extension.*\n"
            + "import org.xwiki.model.reference.*\n"
            + "import com.xwiki.licensing.*\n"
            + "import org.xwiki.component.descriptor.*\n"
            + "import org.xwiki.script.service.*\n"
            + "\n"
            + "class VoidLicensor implements Licensor\n"
            + "{\n"
            + "    License getLicense() { return null }\n"
            + "    License getLicense(ExtensionId extensionId) { return null }\n"
            + "    License getLicense(EntityReference entityReference) { return null }\n"
            + "    boolean hasLicensure() { return true }\n"
            + "    boolean hasLicensure(EntityReference entityReference) { return true }\n"
            + "    boolean hasLicensure(ExtensionId extensionId) { return true }\n"
            + "}\n"
            + "\n"
            + "class VoidLicensorScriptService implements ScriptService\n"
            + "{\n"
            + "    License getLicense() { return null }\n"
            + "    License getLicenseForExtension(ExtensionId extensionId) { return null }\n"
            + "    License getLicenseForEntity(EntityReference reference) { return null }\n"
            + "    boolean hasLicensure() { return true }\n"
            + "    boolean hasLicensureForExtension(ExtensionId extensionId) { return true }\n"
            + "    boolean hasLicensureForEntity(EntityReference reference) { return true }\n"
            + "    Licensor getLicensor() { return null }\n"
            + "    LicenseManager getLicenseManager() { return null }\n"
            + "    boolean addLicense(String license) { return true }\n"
            + "    boolean addLicense(byte[] license) { return true }\n"
            + "    void checkLicense() { }\n"
            + "}\n"
            + "\n"
            + "// Register them!\n"
            + "\n"
            + "def DefaultComponentDescriptor cd = new DefaultComponentDescriptor()\n"
            + "cd.setRoleType(Licensor.class)\n"
            + "cd.setInstantiationStrategy(ComponentInstantiationStrategy.SINGLETON)\n"
            + "services.component.getRootComponentManager().registerComponent(cd, new VoidLicensor())\n"
            + "\n"
            + "cd = new DefaultComponentDescriptor()\n"
            + "cd.setRoleType(ScriptService.class)\n"
            + "cd.setRoleHint(\"licensor\")\n"
            + "cd.setInstantiationStrategy(ComponentInstantiationStrategy.SINGLETON)\n"
            + "services.component.getRootComponentManager().registerComponent(cd, new VoidLicensorScriptService())\n"
            + "{{/groovy}}";
        vp = getUtil().createPage(getTestClassName(), "FakeLicensing", content, "Fake Licensing");
        assertEquals("", vp.getContent());
    }
}
