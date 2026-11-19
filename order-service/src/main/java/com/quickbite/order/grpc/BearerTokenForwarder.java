package com.quickbite.order.grpc;

import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.ClientCall;
import io.grpc.ClientInterceptor;
import io.grpc.ForwardingClientCall;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.AbstractOAuth2TokenAuthenticationToken;

/**
 * Sends the caller's own access token with every gRPC call. restaurant-service and
 * customer-service authenticate gRPC calls with the same Keycloak tokens as HTTP, so the user's
 * identity travels end to end instead of order-service using a shared service credential.
 * <p>
 * The token is read when the call starts, on the request thread that holds the security context.
 */
public class BearerTokenForwarder implements ClientInterceptor {

    static final Metadata.Key<String> AUTHORIZATION =
            Metadata.Key.of("authorization", Metadata.ASCII_STRING_MARSHALLER);

    @Override
    public <Q, R> ClientCall<Q, R> interceptCall(MethodDescriptor<Q, R> method, CallOptions options, Channel next) {
        String token = currentToken();
        return new ForwardingClientCall.SimpleForwardingClientCall<>(next.newCall(method, options)) {
            @Override
            public void start(Listener<R> listener, Metadata headers) {
                if (token != null) {
                    headers.put(AUTHORIZATION, "Bearer " + token);
                }
                super.start(listener, headers);
            }
        };
    }

    private static String currentToken() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication instanceof AbstractOAuth2TokenAuthenticationToken<?> oauth
                ? oauth.getToken().getTokenValue()
                : null;
    }
}
