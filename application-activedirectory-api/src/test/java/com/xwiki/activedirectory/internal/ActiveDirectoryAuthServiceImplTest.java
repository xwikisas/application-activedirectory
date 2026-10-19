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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.xwiki.component.util.ReflectionUtils;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.test.LogLevel;
import org.xwiki.test.junit5.LogCaptureExtension;

import com.xpn.xwiki.XWikiContext;
import com.xpn.xwiki.doc.XWikiDocument;
import com.xwiki.licensing.License;
import com.xwiki.licensing.Licensor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

/**
 * Unit tests for {@link ActiveDirectoryAuthServiceImpl#shouldUseADAuthService(String, XWikiContext)}.
 *
 * @version $Id$
 */
class ActiveDirectoryAuthServiceImplTest
{
    private static final String EXTENSION_ID = "com.xwiki.activedirectory:application-activedirectory-api";

    private static final String USERNAME = "jdoe";

    private static final DocumentReference USER_REFERENCE = new DocumentReference("xwiki", "XWiki", USERNAME);

    private static final long DAY = 24L * 60 * 60 * 1000;

    @RegisterExtension
    private LogCaptureExtension logCapture = new LogCaptureExtension(LogLevel.WARN);

    private ActiveDirectoryAuthServiceImpl authService;

    private Licensor licensor;

    private XWikiContext context;

    private XWikiDocument userProfile;

    @BeforeEach
    void setUp() throws Exception
    {
        // The constructor looks up many components (also in the parent classes); the decision only needs the
        // licensor and the profile lookup.
        this.authService =
            mock(ActiveDirectoryAuthServiceImpl.class, withSettings().defaultAnswer(CALLS_REAL_METHODS));
        this.licensor = mock(Licensor.class);
        ReflectionUtils.setFieldValue(this.authService, "licensor", this.licensor);
        this.context = mock(XWikiContext.class);
        this.userProfile = mock(XWikiDocument.class);
        when(this.userProfile.getDocumentReference()).thenReturn(USER_REFERENCE);
        doReturn(null).when(this.authService).findLDAPProfile(any(), any());
    }

    @Test
    void noLicense() throws Exception
    {
        assertFalse(this.authService.shouldUseADAuthService(USERNAME, this.context));
        verify(this.authService, never()).findLDAPProfile(any(), any());
    }

    @Test
    void expiredLicense() throws Exception
    {
        setLicense(-DAY, 10);
        when(this.licensor.getUserCount()).thenReturn(1L);

        assertFalse(this.authService.shouldUseADAuthService(USERNAME, this.context));
        verify(this.authService, never()).findLDAPProfile(any(), any());
    }

    @Test
    void unlimitedUsers() throws Exception
    {
        setLicense(DAY, -1);
        when(this.licensor.getUserCount()).thenReturn(1000L);

        assertTrue(this.authService.shouldUseADAuthService(USERNAME, this.context));
        verify(this.authService, never()).findLDAPProfile(any(), any());
    }

    @Test
    void roomForOneMoreUser() throws Exception
    {
        setLicense(DAY, 10);
        when(this.licensor.getUserCount()).thenReturn(9L);

        // A new user can be created, so there's no need to look the profile up.
        assertTrue(this.authService.shouldUseADAuthService(USERNAME, this.context));
        verify(this.authService, never()).findLDAPProfile(any(), any());
    }

    @Test
    void fullLicenseWithNewUser() throws Exception
    {
        setLicense(DAY, 10);
        when(this.licensor.getUserCount()).thenReturn(10L);

        assertFalse(this.authService.shouldUseADAuthService(USERNAME, this.context));
        verify(this.licensor, never()).isUserUnderLimit(any(), anyLong());
    }

    @Test
    void fullLicenseWithExistingUserUnderLimit() throws Exception
    {
        setLicense(DAY, 10);
        when(this.licensor.getUserCount()).thenReturn(10L);
        doReturn(this.userProfile).when(this.authService).findLDAPProfile(USERNAME, this.context);
        when(this.licensor.isUserUnderLimit(USER_REFERENCE, 10)).thenReturn(true);

        assertTrue(this.authService.shouldUseADAuthService(USERNAME, this.context));
    }

    @Test
    void exceededLicenseWithExistingUserUnderLimit() throws Exception
    {
        setLicense(DAY, 10);
        when(this.licensor.getUserCount()).thenReturn(12L);
        doReturn(this.userProfile).when(this.authService).findLDAPProfile(USERNAME, this.context);
        when(this.licensor.isUserUnderLimit(USER_REFERENCE, 10)).thenReturn(true);

        assertTrue(this.authService.shouldUseADAuthService(USERNAME, this.context));
    }

    @Test
    void exceededLicenseWithExistingUserOverLimit() throws Exception
    {
        setLicense(DAY, 10);
        when(this.licensor.getUserCount()).thenReturn(12L);
        doReturn(this.userProfile).when(this.authService).findLDAPProfile(USERNAME, this.context);
        when(this.licensor.isUserUnderLimit(USER_REFERENCE, 10)).thenReturn(false);

        assertFalse(this.authService.shouldUseADAuthService(USERNAME, this.context));
    }

    @Test
    void failureToCountUsers() throws Exception
    {
        setLicense(DAY, 10);
        when(this.licensor.getUserCount()).thenThrow(new Exception("Failed to count the users."));

        assertFalse(this.authService.shouldUseADAuthService(USERNAME, this.context));
        assertEquals("Failed to determine Active Directory access status.", this.logCapture.getMessage(0));
    }

    private void setLicense(long expirationFromNow, long maxUserCount)
    {
        License license = new License();
        license.setExpirationDate(System.currentTimeMillis() + expirationFromNow);
        license.setMaxUserCount(maxUserCount);
        when(this.licensor.getLicense(EXTENSION_ID)).thenReturn(license);
    }
}
