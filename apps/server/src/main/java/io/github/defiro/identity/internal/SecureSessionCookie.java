package io.github.defiro.identity.internal;

import jakarta.servlet.ServletException;
import java.io.IOException;
import org.apache.catalina.connector.Request;
import org.apache.catalina.connector.Response;
import org.apache.catalina.valves.ValveBase;
import org.springframework.boot.tomcat.servlet.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.stereotype.Component;

/**
 * Behind TLS, the session cookie is Secure. Tomcat makes that cookie itself, from its own request, which
 * never sees the forwarded headers that Spring reads (server.forward-headers-strategy).
 */
@Component
class SecureSessionCookie implements WebServerFactoryCustomizer<TomcatServletWebServerFactory> {

    @Override
    public void customize(TomcatServletWebServerFactory factory) {
        factory.addEngineValves(new ValveBase(true) {
            @Override
            public void invoke(Request request, Response response) throws IOException, ServletException {
                if ("https".equalsIgnoreCase(request.getHeader("X-Forwarded-Proto"))) {
                    request.setSecure(true);
                }
                getNext().invoke(request, response);
            }
        });
    }
}
