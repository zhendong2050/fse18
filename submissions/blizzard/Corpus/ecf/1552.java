/****************************************************************************
 * Copyright (c) 2008 Composent, Inc. and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Composent, Inc. - initial API and implementation
 *****************************************************************************/
package org.eclipse.ecf.provider.filetransfer.browse;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.Authenticator;
import java.net.HttpURLConnection;
import java.net.PasswordAuthentication;
import java.net.URL;
import java.net.URLConnection;
import org.eclipse.ecf.core.security.Callback;
import org.eclipse.ecf.core.security.CallbackHandler;
import org.eclipse.ecf.core.security.IConnectContext;
import org.eclipse.ecf.core.security.NameCallback;
import org.eclipse.ecf.core.security.ObjectCallback;
import org.eclipse.ecf.core.security.UnsupportedCallbackException;
import org.eclipse.ecf.core.util.Proxy;
import org.eclipse.ecf.filetransfer.BrowseFileTransferException;
import org.eclipse.ecf.filetransfer.IRemoteFile;
import org.eclipse.ecf.filetransfer.IRemoteFileSystemListener;
import org.eclipse.ecf.filetransfer.IncomingFileTransferException;
import org.eclipse.ecf.filetransfer.identity.IFileID;
import org.eclipse.ecf.internal.provider.filetransfer.Activator;
import org.eclipse.ecf.internal.provider.filetransfer.IURLConnectionModifier;
import org.eclipse.ecf.internal.provider.filetransfer.Messages;
import org.eclipse.ecf.provider.filetransfer.util.JREProxyHelper;
import org.eclipse.osgi.util.NLS;

/**
 *
 */
public class URLFileSystemBrowser extends AbstractFileSystemBrowser {

    private static final String USERNAME_PREFIX = Messages.UrlConnectionRetrieveFileTransfer_USERNAME_PROMPT;

    //$NON-NLS-1$
    private static final String JRE_CONNECT_TIMEOUT_PROPERTY = "sun.net.client.defaultConnectTimeout";

    // 10/26/2009:  Added being able to set with system property with name org.eclipse.ecf.provider.filetransfer.browse.connectTimeout
    // for https://bugs.eclipse.org/bugs/show_bug.cgi?id=292995
    //$NON-NLS-1$ //$NON-NLS-2$
    private static final String DEFAULT_CONNECT_TIMEOUT = System.getProperty("org.eclipse.ecf.provider.filetransfer.browse.connectTimeout", "30000");

    //$NON-NLS-1$
    private static final String JRE_READ_TIMEOUT_PROPERTY = "sun.net.client.defaultReadTimeout";

    // 10/26/2009:  Added being able to set with system property with name org.eclipse.ecf.provider.filetransfer.browse.readTimeout
    // for https://bugs.eclipse.org/bugs/show_bug.cgi?id=292995
    //$NON-NLS-1$ //$NON-NLS-2$
    private static final String DEFAULT_READ_TIMEOUT = System.getProperty("org.eclipse.ecf.provider.filetransfer.browse.readTimeout", "30000");

    private JREProxyHelper proxyHelper = null;

    protected String username = null;

    protected String password = null;

    /**
	 * @param directoryOrFileID directory or file id
	 * @param listener listener
	 * @param directoryOrFileURL directory or file url
	 * @param connectContext connect context
	 * @param proxy proxy
	 */
    public  URLFileSystemBrowser(IFileID directoryOrFileID, IRemoteFileSystemListener listener, URL directoryOrFileURL, IConnectContext connectContext, Proxy proxy) {
        super(directoryOrFileID, listener, directoryOrFileURL, connectContext, proxy);
        proxyHelper = new JREProxyHelper();
    }

    private void setupTimeouts() {
        String existingTimeout = System.getProperty(JRE_CONNECT_TIMEOUT_PROPERTY);
        if (existingTimeout == null) {
            System.setProperty(JRE_CONNECT_TIMEOUT_PROPERTY, DEFAULT_CONNECT_TIMEOUT);
        }
        existingTimeout = System.getProperty(JRE_READ_TIMEOUT_PROPERTY);
        if (existingTimeout == null) {
            System.setProperty(JRE_READ_TIMEOUT_PROPERTY, DEFAULT_READ_TIMEOUT);
        }
    }

    /* (non-Javadoc)
	 * @see org.eclipse.ecf.provider.filetransfer.browse.AbstractFileSystemBrowser#runRequest()
	 */
    protected void runRequest() throws Exception {
        int code = -1;
        try {
            setupProxies();
            setupAuthentication();
            setupTimeouts();
            URLConnection urlConnection = directoryOrFile.openConnection();
            // this is for addressing bug https://bugs.eclipse.org/bugs/show_bug.cgi?id=235933
            if (//$NON-NLS-1$
            directoryOrFile.getProtocol().equalsIgnoreCase("jar")) {
                urlConnection.setUseCaches(false);
            }
            // Add http 1.1 'Connection: close' header in order to potentially avoid
            // server issue described here https://bugs.eclipse.org/bugs/show_bug.cgi?id=234916#c13
            // See bug https://bugs.eclipse.org/bugs/show_bug.cgi?id=247197
            // also see http 1.1 rfc section 14-10 in http://www.w3.org/Protocols/rfc2616/rfc2616-sec14.html
            //$NON-NLS-1$ //$NON-NLS-2$
            urlConnection.setRequestProperty("Connection", "close");
            IURLConnectionModifier connectionModifier = Activator.getDefault().getURLConnectionModifier();
            if (connectionModifier != null) {
                connectionModifier.setSocketFactoryForConnection(urlConnection);
            }
            if (urlConnection instanceof HttpURLConnection) {
                HttpURLConnection httpConnection = (HttpURLConnection) urlConnection;
                //$NON-NLS-1$
                httpConnection.setRequestMethod(//$NON-NLS-1$
                "HEAD");
                httpConnection.connect();
            } else {
                InputStream ins = urlConnection.getInputStream();
                ins.close();
            }
            code = getResponseCode(urlConnection);
            if (isHTTP()) {
                if (code == HttpURLConnection.HTTP_OK) {
                // do nothing
                } else if (code == HttpURLConnection.HTTP_NOT_FOUND) {
                    throw new //$NON-NLS-1$
                    BrowseFileTransferException(//$NON-NLS-1$
                    NLS.bind("File not found: {0}", directoryOrFile.toString()), //$NON-NLS-1$
                    code);
                } else if (code == HttpURLConnection.HTTP_UNAUTHORIZED) {
                    throw new //$NON-NLS-1$
                    BrowseFileTransferException(//$NON-NLS-1$
                    "Unauthorized", //$NON-NLS-1$
                    code);
                } else if (code == HttpURLConnection.HTTP_FORBIDDEN) {
                    throw new //$NON-NLS-1$
                    BrowseFileTransferException(//$NON-NLS-1$
                    "Forbidden", //$NON-NLS-1$
                    code);
                } else if (code == HttpURLConnection.HTTP_PROXY_AUTH) {
                    throw new //$NON-NLS-1$
                    BrowseFileTransferException(//$NON-NLS-1$
                    "Proxy auth required", //$NON-NLS-1$
                    code);
                } else {
                    throw new //$NON-NLS-1$
                    BrowseFileTransferException(//$NON-NLS-1$
                    NLS.bind("General connection error with response code={0} and header(0)={1}", new Integer(code), urlConnection.getHeaderField(0)), //$NON-NLS-1$
                    code);
                }
            }
            remoteFiles = new IRemoteFile[1];
            remoteFiles[0] = new URLRemoteFile(urlConnection.getLastModified(), urlConnection.getContentLength(), fileID);
        } catch (final FileNotFoundException e) {
            throw new IncomingFileTransferException(NLS.bind("File not found: {0}", directoryOrFile.toString()), 404);
        } catch (Exception e) {
            Exception except = (e instanceof BrowseFileTransferException) ? e : new BrowseFileTransferException(NLS.bind("Could not connect to {0}", directoryOrFile), e, code);
            throw except;
        }
    }

    private boolean isHTTP() {
        final String protocol = directoryOrFile.getProtocol();
        if (//$NON-NLS-1$ //$NON-NLS-2$
        protocol.equalsIgnoreCase("http") || protocol.equalsIgnoreCase("https"))
            return true;
        return false;
    }

    private int getResponseCode(URLConnection urlConnection) {
        int responseCode = -1;
        String response = urlConnection.getHeaderField(0);
        if (response == null) {
            responseCode = -1;
            return responseCode;
        }
        if (//$NON-NLS-1$
        !response.startsWith("HTTP/"))
            return -1;
        response = response.trim();
        //$NON-NLS-1$
        final int mark = response.indexOf(" ") + 1;
        if (mark == 0)
            return -1;
        int last = mark + 3;
        if (last > response.length())
            last = response.length();
        responseCode = Integer.parseInt(response.substring(mark, last));
        return responseCode;
    }

    protected void setupAuthentication() throws IOException, UnsupportedCallbackException {
        if (connectContext == null)
            return;
        final CallbackHandler callbackHandler = connectContext.getCallbackHandler();
        if (callbackHandler == null)
            return;
        final NameCallback usernameCallback = new NameCallback(USERNAME_PREFIX);
        final ObjectCallback passwordCallback = new ObjectCallback();
        // Call callback with username and password callbacks
        callbackHandler.handle(new Callback[] { usernameCallback, passwordCallback });
        username = usernameCallback.getName();
        Object o = passwordCallback.getObject();
        if (!(o instanceof String))
            throw new UnsupportedCallbackException(passwordCallback, Messages.UrlConnectionRetrieveFileTransfer_UnsupportedCallbackException);
        password = (String) passwordCallback.getObject();
        // Now set authenticator to our authenticator with user and password
        Authenticator.setDefault(new UrlConnectionAuthenticator());
    }

    class UrlConnectionAuthenticator extends Authenticator {

        /* (non-Javadoc)
		 * @see java.net.Authenticator#getPasswordAuthentication()
		 */
        protected PasswordAuthentication getPasswordAuthentication() {
            return new PasswordAuthentication(username, password.toCharArray());
        }
    }

    protected void setupProxy(final Proxy proxy2) {
        proxyHelper.setupProxy(proxy2);
    }
}