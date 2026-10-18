package com.travelease.common.security.internal;

import java.io.IOException;

import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

/** Adds the internal-service credential to outgoing service-to-service calls (never a user's token). */
public class InternalServiceClientInterceptor implements ClientHttpRequestInterceptor {

    private final InternalServiceTokens tokens;

    public InternalServiceClientInterceptor(InternalServiceTokens tokens) {
        this.tokens = tokens;
    }

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution)
            throws IOException {
        request.getHeaders().set(InternalServiceTokens.HEADER, tokens.currentToken());
        return execution.execute(request, body);
    }
}
