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

import java.util.Arrays;
import java.util.List;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Provider;
import javax.inject.Singleton;

import org.xwiki.bridge.event.ApplicationReadyEvent;
import org.xwiki.component.annotation.Component;
import org.xwiki.component.manager.ComponentLifecycleException;
import org.xwiki.component.phase.Disposable;
import org.xwiki.component.phase.Initializable;
import org.xwiki.component.phase.InitializationException;
import org.xwiki.observation.EventListener;
import org.xwiki.observation.event.Event;

import com.xpn.xwiki.XWiki;
import com.xpn.xwiki.XWikiContext;

/**
 * Initialize and Remove the AD authenticator by supporting the following use cases:
 * <ul>
 *     <li>XWiki is started and the AD extension is installed. We need to setup the AD authenticator</li>
 *     <li>The AD extension is installed at runtime. We need to setup the AD authenticator</li>
 *     <li>The AD extension is uninstalled at runtime. We need to remove the AD authenticator</li>
 *     <li>XWiki is stopped. We need to remove the AD authenticator (note that this use case is optional since
 *         the AD setup is done in memory only and is not persistent)</li>
 * </ul>
 *
 *
 * @version $Id$
 * @since 1.1
 */
@Component
@Named("ldap.ad.setup")
@Singleton
public class ActiveDirectorySetupListener implements EventListener, Initializable, Disposable
{
    @Inject
    private Provider<XWikiContext> xwikiContextProvider;

    @Override
    public String getName()
    {
        return "ldap.ad.setup";
    }

    @Override
    public void initialize() throws InitializationException
    {
        XWiki xwiki = getXWiki();

        // The returned XWiki object will be null on startup (since Event Listeners are initialized very early when
        // XWiki starts and before the first request has been done). However in this case we don't need to do anything
        // since we register our authenticator by listening to the ApplicationReadyEvent below.
        // The reason we need this initialize() method is when this extension is installed at runtime. The EM component
        // will then initialize this Event Listener at that time.
        if (xwiki != null) {
            setAuthService(xwiki);
        }
    }

    @Override
    public List<Event> getEvents()
    {
        // We need the XWiki object to exist to be able to set our authenticator and thus we need to wait for the first
        // request to be initialized. Note that the ApplicationReadyEvent event is sent at the end of the XWiki
        // initialization and before the first resource is served so our authenticator will be in place by then.
        return Arrays.asList(new ApplicationReadyEvent());
    }

    @Override
    public void onEvent(Event event, Object source, Object data)
    {
        // Set the Authentication Service
        setAuthService(getXWiki());
    }

    /**
     * Note that this dispose() will get called when this Extension is uninstalled which is the use case we want to
     * serve. The fact that it'll also be called when XWiki stops is a side effect that is ok.
     */
    @Override
    public void dispose() throws ComponentLifecycleException
    {
        XWiki xwiki = getXWiki();
        // XWiki can be null in the case when XWiki has been started and not accessed (no first request done and thus
        // no XWiki object initialized) and then stopped.
        if (xwiki != null) {
            // Unset the Authentication Service (next time XWiki.getAuthService() is called it'll be re-initialized)
            xwiki.setAuthService(null);
        }
    }

    private void setAuthService(XWiki xwiki)
    {
        xwiki.setAuthService(new ActiveDirectoryAuthServiceImpl(xwiki.getAuthService()));
    }

    private XWiki getXWiki()
    {
        XWiki result = null;
        XWikiContext xc = this.xwikiContextProvider.get();
        // XWikiContext could be null at startup when the Context Provider has not been initialized yet (it's
        // initialized after the first request).
        if (xc != null) {
            result = xc.getWiki();
        }
        return result;
    }
}
