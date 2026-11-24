package io.github.defiro.acceptance;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.Resource;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

/**
 * The web app, served by the server under test. In production nginx serves the web app and passes
 * the server's paths on (apps/web/nginx.conf); here the server does both, on one address, so a
 * browser signs in and calls the API as it does through nginx. The web app is the build in
 * apps/web/dist: run {@code npm run build} there before the {@code @browser} scenarios.
 */
@TestConfiguration(proxyBeanMethods = false)
public class WebApp implements WebMvcConfigurer {

    static final Path BUILD = Path.of("..", "web", "dist").toAbsolutePath().normalize();

    // The paths nginx passes to the server; every other path is the web app.
    private static final Pattern SERVER = Pattern.compile("^/(api|oauth2|login/oauth2|logout|actuator)(/|$)");

    static void register(DynamicPropertyRegistry registry) {
        // Only this configuration serves files, so that a path nobody answers stays a 404.
        registry.add("spring.web.resources.add-mappings", () -> "false");
    }

    /** The web app's files are public, as nginx serves them; the server's paths keep their security. */
    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE)
    SecurityFilterChain webAppFiles(HttpSecurity http) {
        http.securityMatcher(request -> !SERVER.matcher(request.getRequestURI()).find())
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
        return http.build();
    }

    /** The home page is the web app's index.html, as nginx's index is (an empty path is no file). */
    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        registry.addViewController("/").setViewName("forward:/index.html");
    }

    /** Each route of the web app (/workflows/criar/…) gets its index.html, as nginx's try_files does. */
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/**")
                .addResourceLocations(BUILD.toUri().toString())
                .resourceChain(false)
                .addResolver(new PathResourceResolver() {
                    @Override
                    protected Resource getResource(String path, Resource location) throws IOException {
                        Resource file = location.createRelative(path);
                        if (file.isReadable()) {
                            return file;
                        }
                        return SERVER.matcher("/" + path).find() ? null : location.createRelative("index.html");
                    }
                });
    }

    /** Whether the web app was built, so that a scenario can say what is missing instead of failing oddly. */
    static boolean built() {
        return Files.isReadable(BUILD.resolve("index.html"));
    }
}
