package io.github.defiro.identity.internal;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.defiro.TestPeople;
import io.github.defiro.TestcontainersConfiguration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;

// Two tabs, two first sign-ins at once: defiro keeps one person.
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class FirstSignInRaceIT {

    private static final int TABS = 8;

    @Autowired
    SignIn signIn;

    @Autowired
    JdbcClient jdbc;

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void firstSignInsAtOnceAreOnePerson(boolean emailVerified) throws Exception {
        new TestPeople(jdbc).clear();

        assertThat(new HashSet<>(signInAtOnce(emailVerified))).hasSize(1);
        assertThat(count("identity.app_user")).isEqualTo(1);
        assertThat(count("identity.external_identity")).isEqualTo(1);
    }

    @Test
    void firstSignInsAtOnceOfSomeoneRegisteredInAdvanceAreThatPerson() throws Exception {
        TestPeople people = new TestPeople(jdbc);
        people.clear();
        String registered = people.register("Ana Ferreira", "ana@cm.test").toString();

        assertThat(new HashSet<>(signInAtOnce(true))).containsExactly(registered);
        assertThat(count("identity.external_identity")).isEqualTo(1);
    }

    private List<String> signInAtOnce(boolean emailVerified) throws Exception {
        OidcUser fromProvider = new DefaultOidcUser(
                AuthorityUtils.createAuthorityList("OIDC_USER"),
                new OidcIdToken(
                        "id-token",
                        null,
                        null,
                        Map.of(
                                "iss", "https://idp.test",
                                "sub", "idp-sub-ana",
                                "name", "Ana Ferreira",
                                "email", "ana@cm.test",
                                "email_verified", emailVerified)));
        DefiroOidcUserService service = new DefiroOidcUserService(signIn, request -> fromProvider);
        OidcUserRequest request = Mockito.mock(OidcUserRequest.class);
        CountDownLatch together = new CountDownLatch(1);
        try (ExecutorService tabs = Executors.newFixedThreadPool(TABS)) {
            List<Future<String>> signIns = new ArrayList<>();
            for (int tab = 0; tab < TABS; tab++) {
                signIns.add(tabs.submit(() -> {
                    together.await();
                    return service.loadUser(request).getName();
                }));
            }
            together.countDown();
            List<String> names = new ArrayList<>();
            for (Future<String> each : signIns) {
                names.add(each.get());
            }
            return names;
        }
    }

    private long count(String table) {
        return jdbc.sql("select count(*) from " + table).query(Long.class).single();
    }
}
