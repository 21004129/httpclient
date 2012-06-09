/*
 * ====================================================================
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 * ====================================================================
 *
 * This software consists of voluntary contributions made by many
 * individuals on behalf of the Apache Software Foundation.  For more
 * information on the Apache Software Foundation, please see
 * <http://www.apache.org/>.
 *
 */

package org.apache.http.impl.client.exec;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.URISyntaxException;

import org.apache.http.annotation.NotThreadSafe;

import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpEntityEnclosingRequest;
import org.apache.http.HttpHost;
import org.apache.http.HttpRequest;
import org.apache.http.ProtocolException;
import org.apache.http.ProtocolVersion;
import org.apache.http.RequestLine;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.entity.HttpEntityWrapper;
import org.apache.http.message.AbstractHttpMessage;
import org.apache.http.message.BasicRequestLine;
import org.apache.http.params.HttpProtocolParams;
import org.apache.http.protocol.HTTP;

/**
 * A wrapper class for {@link HttpRequest}s that can be used to change
 * properties of the current request without modifying the original
 * object.
 *
 * @since 4.3
 */
@NotThreadSafe
public class HttpRequestWrapper extends AbstractHttpMessage implements HttpRequest {

    private final HttpRequest original;

    private URI uri;
    private String method;
    private ProtocolVersion version;
    private HttpHost virtualHost;

    HttpRequestWrapper(
            final ProtocolVersion version,
            final URI uri,
            final String method,
            final HttpRequest request) {
        super();
        this.original = request;
        this.uri = uri;
        this.method = method;
        this.version = version;
        setParams(request.getParams());
        setHeaders(request.getAllHeaders());
    }

    public ProtocolVersion getProtocolVersion() {
        if (this.version != null) {
            return this.version;
        } else {
            return HttpProtocolParams.getVersion(getParams());
        }
    }

    public void setProtocolVersion(final ProtocolVersion version) {
        this.version = version;
    }

    public URI getURI() {
        return this.uri;
    }

    public void setURI(final URI uri) {
        this.uri = uri;
    }

    public RequestLine getRequestLine() {
        String uritext = null;
        if (this.uri != null) {
            uritext = uri.toASCIIString();
        }
        if (uritext == null || uritext.length() == 0) {
            uritext = "/";
        }
        return new BasicRequestLine(this.method, uritext, getProtocolVersion());
    }

    public String getMethod() {
        return this.method;
    }

    public HttpRequest getOriginal() {
        return this.original;
    }

    public HttpHost getVirtualHost() {
        return this.virtualHost;
    }

    public void setVirtualHost(final HttpHost virtualHost) {
        this.virtualHost = virtualHost;
    }

    public boolean isRepeatable() {
        return true;
    }

    @Override
    public String toString() {
        return this.method + " " + this.uri + " " + this.headergroup;
    }

    static class HttpEntityEnclosingRequestWrapper extends HttpRequestWrapper
        implements HttpEntityEnclosingRequest {

        private HttpEntity entity;
        private boolean consumed;

        public HttpEntityEnclosingRequestWrapper(
                final ProtocolVersion version,
                final URI uri,
                final String method,
                final HttpEntityEnclosingRequest request)
            throws ProtocolException {
            super(version, uri, method, request);
            setEntity(request.getEntity());
        }

        public HttpEntity getEntity() {
            return this.entity;
        }

        public void setEntity(final HttpEntity entity) {
            this.entity = entity != null ? new EntityWrapper(entity) : null;
        }

        public boolean expectContinue() {
            Header expect = getFirstHeader(HTTP.EXPECT_DIRECTIVE);
            return expect != null && HTTP.EXPECT_CONTINUE.equalsIgnoreCase(expect.getValue());
        }

        @Override
        public boolean isRepeatable() {
            return this.entity == null || this.entity.isRepeatable() || !this.consumed;
        }

        class EntityWrapper extends HttpEntityWrapper {

            EntityWrapper(final HttpEntity entity) {
                super(entity);
            }

            @Deprecated
            @Override
            public void consumeContent() throws IOException {
                consumed = true;
                super.consumeContent();
            }

            @Override
            public InputStream getContent() throws IOException {
                return super.getContent();
            }

            @Override
            public void writeTo(final OutputStream outstream) throws IOException {
                consumed = true;
                super.writeTo(outstream);
            }
        }
        
    }

    public static HttpRequestWrapper wrap(final HttpRequest request) throws ProtocolException {
        if (request == null) {
            return null;
        }
        ProtocolVersion version;
        URI uri;
        String method;
        if (request instanceof HttpUriRequest) {
            version = ((HttpUriRequest) request).getProtocolVersion();
            uri = ((HttpUriRequest) request).getURI();
            method = ((HttpUriRequest) request).getMethod();
        } else {
            RequestLine requestLine = request.getRequestLine();
            version = request.getProtocolVersion();
            try {
                uri = new URI(requestLine.getUri());
            } catch (URISyntaxException ex) {
                throw new ProtocolException("Invalid request URI: " + requestLine.getUri(), ex);
            }
            method = request.getRequestLine().getMethod();
        }
        if (request instanceof HttpEntityEnclosingRequest) {
            return new HttpEntityEnclosingRequestWrapper(version, uri, method,
                    (HttpEntityEnclosingRequest) request);
        } else {
            return new HttpRequestWrapper(version, uri, method, request);
        }
    }

}
