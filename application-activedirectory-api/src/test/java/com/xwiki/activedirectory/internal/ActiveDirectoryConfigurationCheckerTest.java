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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.model.reference.WikiReference;
import org.xwiki.security.authorization.AuthorizationManager;
import org.xwiki.security.authorization.Right;
import org.xwiki.test.junit5.mockito.ComponentTest;
import org.xwiki.test.junit5.mockito.InjectMockComponents;
import org.xwiki.test.junit5.mockito.MockComponent;

import com.xpn.xwiki.doc.XWikiDocument;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link ActiveDirectoryConfigurationChecker}.
 *
 * @version $Id$
 */
@ComponentTest
class ActiveDirectoryConfigurationCheckerTest
{
    private static final WikiReference WIKI = new WikiReference("wiki");

    private static final DocumentReference ADMIN = new DocumentReference("wiki", "XWiki", "Admin");

    private static final DocumentReference USER = new DocumentReference("wiki", "XWiki", "User");

    @InjectMockComponents
    private ActiveDirectoryConfigurationChecker checker;

    @MockComponent
    private AuthorizationManager authorizationManager;

    private XWikiDocument configurationDocument;

    @BeforeEach
    void setUp()
    {
        this.configurationDocument = mock(XWikiDocument.class);
        when(this.configurationDocument.getDocumentReference())
            .thenReturn(new DocumentReference("wiki", List.of("ActiveDirectory", "Code"), "ActiveDirectoryConfig"));
        when(this.authorizationManager.hasAccess(Right.ADMIN, ADMIN, WIKI)).thenReturn(true);
    }

    @Test
    void isTrustedWhenSavedByWikiAdministrator()
    {
        when(this.configurationDocument.getAuthorReference()).thenReturn(ADMIN);

        assertTrue(this.checker.isTrusted(this.configurationDocument));
    }

    @Test
    void isNotTrustedWhenSavedByUserWithoutAdministrationRight()
    {
        when(this.configurationDocument.getAuthorReference()).thenReturn(USER);

        assertFalse(this.checker.isTrusted(this.configurationDocument));
    }

    @Test
    void isNotTrustedWithoutAuthor()
    {
        assertFalse(this.checker.isTrusted(this.configurationDocument));
    }

    @Test
    void isNotTrustedWithoutDocument()
    {
        assertFalse(this.checker.isTrusted(null));
    }
}
