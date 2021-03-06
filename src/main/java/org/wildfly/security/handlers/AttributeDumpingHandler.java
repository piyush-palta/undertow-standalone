/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2020 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package org.wildfly.security.handlers;

import static org.wildfly.common.Assert.checkMinimumParameter;
import static org.wildfly.common.Assert.checkMaximumParameter;

import java.io.DataOutputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.util.Collections;
import java.util.Deque;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import io.undertow.server.handlers.Cookie;
import io.undertow.UndertowLogger;
import io.undertow.attribute.StoredResponse;
import io.undertow.security.api.SecurityContext;
import io.undertow.server.ExchangeCompletionListener;
import io.undertow.server.HandlerWrapper;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.builder.HandlerBuilder;
import io.undertow.server.handlers.form.FormData;
import io.undertow.server.handlers.form.FormDataParser;
import io.undertow.util.HeaderValues;
import io.undertow.util.Headers;
import io.undertow.util.LocaleUtils;


/**
 * Handler that dumps a exchange to a log.
 * @author <a href="mailto:piyush.palta@outlook.com">Piyush Palta</a>
 */

public class AttributeDumpingHandler implements HttpHandler {

    private final HttpHandler next;
    private String AttributeDumpAddress;
    private int AttributeDumpPort;

    /**
     * Construct a new instance.
     *
     * @param next the next HttpHandler
     * Assigning default values of AttibuteDumpAddress & AttributeDumpPort
     */

    public AttributeDumpingHandler(final HttpHandler next) {
        this.next = next;
        this.AttributeDumpAddress = "127.0.0.1";
        this.AttributeDumpPort = 1575;
    }

    /**
     * Construct a new instance.
     * @param next the next HttpHandler
     * @param AttributeDumpAddress the IP address of attibute dump server
     * @param AttributeDumpPort the port number of attribute dump, must be >=1 and <=65535
     */

    public AttributeDumpingHandler(final HttpHandler next, String AttributeDumpAddress, int AttributeDumpPort) {
        this.next = next;
        checkMinimumParameter("AttributeDumpPort",1,AttributeDumpPort);
        checkMaximumParameter("AttributeDumpPort",65535,AttributeDumpPort);
        this.AttributeDumpAddress = AttributeDumpAddress;
        this.AttributeDumpPort = AttributeDumpPort;
    }

    /**
     * Sends JSON formatted message to the Attributes Dump server
     *
     * @param message JSON formatted message containing all attributes
     */
     private void sendAttributes(String message){
        try{
        Socket socket=new Socket(AttributeDumpAddress,AttributeDumpPort);
        OutputStream output= socket.getOutputStream();
        DataOutputStream out = new DataOutputStream(output);
        out.writeUTF(message);

        socket.close();
        }catch (Exception e) {
            throw new RuntimeException(e);
        }

    }


    @Override
    public void handleRequest(final HttpServerExchange exchange) throws Exception {
        final StringBuilder sb = new StringBuilder();
// Log pre-service information
        final SecurityContext sc = exchange.getSecurityContext();
// sb.append("\n----------------------------REQUEST---------------------------\n");
        sb.append("{");
        sb.append("\"URI\":\"" + exchange.getRequestURI() + "\",");
        sb.append("\"characterEncoding\":\"" + exchange.getRequestHeaders().get(Headers.CONTENT_ENCODING) + "\",");
        sb.append("\"contentLength\":\"" + exchange.getRequestContentLength()+ "\",");
        sb.append("\"contentType\":\"" + exchange.getRequestHeaders().get(Headers.CONTENT_TYPE) + "\",");
        //sb.append("\"contextPath\":\"" + exchange.getContextPath()+ "\",");
        if (sc != null) {
            if (sc.isAuthenticated()) {
                sb.append("\"authType\":\"" + sc.getMechanismName() + "\",");
                sb.append("\"principle\":\"" + sc.getAuthenticatedAccount().getPrincipal() + "\",");
            } else {
                sb.append("\"authType\":\"none\",");
            }
        }

        Map<String, Cookie> cookies = exchange.getRequestCookies();
        if (cookies != null) {
            for (Map.Entry<String, Cookie> entry : cookies.entrySet()) {
                Cookie cookie = entry.getValue();
                sb.append("\"cookie\":\"" + cookie.getName() + "=" +
                        cookie.getValue() + "\",");
            }
        }
        for (HeaderValues header : exchange.getRequestHeaders()) {
            for (String value : header) {
                sb.append("\"header\":\"" + header.getHeaderName() + "=" + value + "\",");
            }
        }
        sb.append("\"locale\":\"" + LocaleUtils.getLocalesFromHeader(exchange.getRequestHeaders().get(Headers.ACCEPT_LANGUAGE)) + "\",");
        sb.append("\"method\":\"" + exchange.getRequestMethod() + "\",");
        Map<String, Deque<String>> pnames = exchange.getQueryParameters();
        for (Map.Entry<String, Deque<String>> entry : pnames.entrySet()) {
            String pname = entry.getKey();
            Iterator<String> pvalues = entry.getValue().iterator();
            sb.append("\"parameter\":\"");
            sb.append(pname);
            sb.append('=');
            while (pvalues.hasNext()) {
                sb.append(pvalues.next());
                if (pvalues.hasNext()) {
                    sb.append(", ");
                }
            }
            sb.append("\",");
        }
        //sb.append("\"pathInfo\":\"" + exchange.getPathInfo());
        sb.append("\"protocol\":\"" + exchange.getProtocol() + "\",");
        sb.append("\"queryString\":\"" + exchange.getQueryString() + "\",");
        sb.append("\"remoteAddr\":\"" + exchange.getSourceAddress() + "\",");
        sb.append("\"remoteHost\":\"" + exchange.getSourceAddress().getHostName() + "\",");
        //sb.append("\"requestedSessionId\":\"" + exchange.getRequestedSessionId());
        sb.append("\"scheme\":\"" + exchange.getRequestScheme() + "\",");
        sb.append("\"host\":\"" + exchange.getRequestHeaders().getFirst(Headers.HOST) + "\",");
        sb.append("\"serverPort\":\"" + exchange.getDestinationAddress().getPort() + "\",");
        //sb.append("\"servletPath\":\"" + exchange.getServletPath());
        sb.append("\"isSecure\":\"" + exchange.isSecure() + "\",");

        exchange.addExchangeCompleteListener(new ExchangeCompletionListener() {
            @Override
            public void exchangeEvent(final HttpServerExchange exchange, final NextListener nextListener) {

                dumpRequestBody(exchange, sb);

                // Log post-service information
                // sb.append("--------------------------RESPONSE--------------------------\n");
                if (sc != null) {
                    if (sc.isAuthenticated()) {
                        sb.append("\"authType\":\"" + sc.getMechanismName() + "\",");
                        sb.append("\"principle\":\"" + sc.getAuthenticatedAccount().getPrincipal() + "\",");
                    } else {
                        sb.append("\"authType\":\"none\"," );
                    }
                }
                sb.append("\"contentLength\":\"" + exchange.getResponseContentLength() + "\",");
                sb.append("\"contentType\":\"" + exchange.getResponseHeaders().getFirst(Headers.CONTENT_TYPE) + "\",");
                Map<String, Cookie> cookies = exchange.getResponseCookies();
                if (cookies != null) {
                    for (Cookie cookie : cookies.values()) {
                        sb.append("\"cookie\":\"" + cookie.getName() + "=" + cookie.getValue() + "; domain=" + cookie.getDomain() + "; path=" + cookie.getPath() + "\",");
                    }
                }
                for (HeaderValues header : exchange.getResponseHeaders()) {
                    for (String value : header) {
                        sb.append("\"header\":\"" + header.getHeaderName() + "=" + value + "\",");
                    }
                }
                sb.append("\"status\":\"" + exchange.getStatusCode() + "\",");
                String storedResponse = StoredResponse.INSTANCE.readAttribute(exchange);
                if (storedResponse != null) {
                    sb.append("body=\n");
                    sb.append(storedResponse);
                }

                sb.append("}\n");

                nextListener.proceed();
                sendAttributes(sb.toString());
                UndertowLogger.REQUEST_DUMPER_LOGGER.info(sb.toString());
            }
        });


        // Perform the exchange
        next.handleRequest(exchange);
    }

    private void dumpRequestBody(HttpServerExchange exchange, StringBuilder sb) {
        try {
            FormData formData = exchange.getAttachment(FormDataParser.FORM_DATA);
            if (formData != null) {
                sb.append("body=\n");

                for (String formField : formData) {
                    Deque<FormData.FormValue> formValues = formData.get(formField);

                    sb.append(formField)
                            .append("=");
                    for (FormData.FormValue formValue : formValues) {
                        sb.append(formValue.isFileItem() ? "[file-content]" : formValue.getValue());
                        sb.append("\n");

                        if (formValue.getHeaders() != null) {
                            sb.append("headers=\n");
                            for (HeaderValues header : formValue.getHeaders()) {
                                sb.append("\t")
                                        .append(header.getHeaderName()).append("=").append(header.getFirst()).append("\n");

                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }


    public static class Builder implements HandlerBuilder {

        @Override
        public String name() {
            return "dump-request";
        }

        @Override
        public Map<String, Class<?>> parameters() {
            return Collections.emptyMap();
        }

        @Override
        public Set<String> requiredParameters() {
            return Collections.emptySet();
        }

        @Override
        public String defaultParameter() {
            return null;
        }

        @Override
        public HandlerWrapper build(Map<String, Object> config) {

            return new Wrapper();
        }

    }

    private static class Wrapper implements HandlerWrapper {
        @Override
        public HttpHandler wrap(HttpHandler handler) {
            return new AttributeDumpingHandler(handler);
        }
    }
}
