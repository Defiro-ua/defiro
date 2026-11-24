<p align="center">
  <a href="https://defiro-ua.github.io/website/"><img src=".github/assets/logo.png" width="320" alt="Defiro"/></a>
</p>

<p align="center">
  <a href="https://github.com/Defiro-ua/defiro/actions/workflows/ci.yml?query=branch%3Amain"><img src="https://github.com/Defiro-ua/defiro/actions/workflows/ci.yml/badge.svg?branch=main" alt="CI"/></a>
  <a href="https://sonarcloud.io/summary/new_code?id=Defiro-ua_Defiro"><img src="https://img.shields.io/sonar/quality_gate/Defiro-ua_Defiro?server=https%3A%2F%2Fsonarcloud.io&label=quality%20gate&logo=sonarqubecloud" alt="Quality gate"/></a>
  <a href="https://sonarcloud.io/summary/overall?id=Defiro-ua_Defiro"><img src="https://img.shields.io/sonar/coverage/Defiro-ua_Defiro?server=https%3A%2F%2Fsonarcloud.io&logo=sonarqubecloud" alt="Coverage"/></a>
  <a href="LICENSE"><img src="https://img.shields.io/badge/License-EUPL%201.2-blue" alt="License: EUPL-1.2"/></a>
</p>

<p align="center">
  <b>Build your workflows without writing any code.</b> A fully adaptable workflow automation platform.
</p>

Draw a workflow in the designer: the steps it goes through, the people involved at each step, and the information they need. Once published, it's ready for people to make requests. Defiro takes care of the forms, inboxes, approvals and notifications along the way. Our first case study is municipal administration, starting with reimbursing an employee's travel expenses.

- **Versioned on every publish.** Each time you publish a workflow, Defiro keeps it as a new version.
- **Every request accounted for.** Each request is tracked from step to step, and every change to it is recorded.
- **Extensible by plugins.** Whatever a workflow needs beyond the basics comes as a plugin, loaded at runtime from the Plugin Registry.

**Get started (30 seconds).** Needs JDK 21 and Docker:

```bash
git clone https://github.com/Defiro-ua/defiro.git
cd defiro
./mvnw verify                                        # builds everything and runs the tests
docker compose -f deploy/compose.yaml up -d --wait   # Postgres, Keycloak and the web app
java -jar apps/server/target/server-0.1.0-SNAPSHOT.jar --spring.profiles.active=dev
```

That's it: open <http://localhost:3000> and sign in as `sofia.almeida`, password `defiro` (a development account only).

**Any identity provider.** People sign in through any OpenID Connect provider. Development uses Keycloak: [connect yours](#sign-in).

---

## Prerequisites

| Requirement | Minimum | Check | Install |
|---|---|---|---|
| JDK | 21 | `java -version` | [Eclipse Temurin](https://adoptium.net/) |
| Docker | running | `docker info` | [Docker Desktop](https://www.docker.com/products/docker-desktop/) |
| Node.js *(web app only)* | 26 | `node --version` | [nodejs.org](https://nodejs.org/) |

You don't need to install Maven: the repository includes the Maven wrapper (`./mvnw`, or `mvnw.cmd` in a Windows terminal).

**Windows quick install:**
```powershell
winget install EclipseAdoptium.Temurin.21.JDK Docker.DockerDesktop
```

**macOS quick install (Homebrew):**
```bash
brew install --cask temurin@21 docker-desktop
```

**Ubuntu/Debian:**
```bash
sudo apt install openjdk-21-jdk docker.io
```

> [!IMPORTANT]
> Build with JDK 21. If the formatting check fails with a `NoSuchFieldError` from `com.sun.tools.javac`, Maven is running on a newer JDK that palantir-java-format doesn't support yet.

---

## Run it locally

The quick start is at the top of this README. This section covers the IDE, the web app and sign-in.

**In IntelliJ IDEA**, open the folder, set the project SDK to 21 (**File → Project Structure**), and run `DefiroApplication`. Set the active profile to `dev` to sign in with the Keycloak of `deploy/compose.yaml`; start it first with `docker compose -f deploy/compose.yaml up -d --wait keycloak`, because the server doesn't start while its provider is down. To skip Postgres, run `TestDefiroApplication` (in `apps/server/src/test`) instead: it starts its own throwaway one.

> [!IMPORTANT]
> Migrations of use cases that aren't in use anywhere yet are edited in place. When one you have already run changes, Flyway refuses to start the server with a checksum mismatch: recreate your development database with `docker compose -f deploy/compose.yaml down -v`, which deletes its data, and start again.

### Web app

```bash
docker compose -f deploy/compose.yaml up -d --build web          # http://localhost:3000
docker compose -f deploy/compose.yaml --profile dev up web-dev   # live reload at http://localhost:5173
```

Or, with Node installed, `npm install` and `npm run dev` in `apps/web`. See `apps/web/README.md`.

### Sign-in

The identity provider only says who someone is. From its token defiro reads `iss`, `sub`, `name`, `email` and `email_verified`, and nothing else: roles and groups are in defiro's database. Any OpenID Connect provider works, and changing provider is changing these four variables. Never put their values in the repository.

| Variable | Example |
|---|---|
| `SPRING_SECURITY_OAUTH2_CLIENT_PROVIDER_OIDC_ISSUER_URI` | `https://idp.example.org/realms/defiro` |
| `SPRING_SECURITY_OAUTH2_CLIENT_REGISTRATION_OIDC_CLIENT_ID` | `defiro` |
| `SPRING_SECURITY_OAUTH2_CLIENT_REGISTRATION_OIDC_CLIENT_SECRET` | the client's secret |
| `SPRING_SECURITY_OAUTH2_CLIENT_REGISTRATION_OIDC_SCOPE` | `openid,profile,email` |

Register defiro in the provider as a confidential client (a web app with a secret) with the authorization code flow and PKCE (S256), and these two addresses, where `https://defiro.example.org` is where browsers open the web app:

- **Redirect URI**: `https://defiro.example.org/login/oauth2/code/oidc`
- **Post-logout redirect URI**: `https://defiro.example.org/?aviso=sessao-terminada`

The `dev` profile sets all of this for the Keycloak of `deploy/compose.yaml`, whose realm is `deploy/keycloak/defiro-realm.json`. Its secret and password are for development only.

> [!IMPORTANT]
> - **One issuer address.** The browser and the server must reach the provider at the same address: it goes in every token's `iss`, which is compared as text. In development it is `http://localhost:8081`, because the server runs on the host. If the server ever runs in a container, give the provider a DNS name that both can resolve; never `localhost` on one side and the service's name on the other.
> - **Always the issuer URI.** Don't replace it with the provider's endpoints, one by one: without it, Spring doesn't compare the token's `iss`.
> - **The server only behind its proxy.** It believes the `X-Forwarded-*` headers of whoever calls it (nginx, or Vite in development). Don't publish its port.
> - Without these variables nobody can sign in: only `/actuator/health` answers, and every other request is refused. With them, the server only starts if it reaches the provider.

**Who someone is in defiro.** The first time an account signs in, it is linked to the person registered in advance with its email, if the provider says the email is verified and that person has no account yet. Otherwise defiro creates a new person, with no roles and no groups. From then on the account is known by its issuer and subject.

**Roles and groups.** There is no administration screen yet: an administrator of the database makes these changes in SQL, and triggers record who joins, leaves or gets what in `identity.audit_log`: people, roles and group members. They count in 60 seconds at most, without signing in again. For example, the first administrator, before they ever sign in:

```sql
insert into identity.app_user (name, email) values ('Ana Dias', 'ana.dias@example.org');

insert into identity.user_role (user_id, role)
select id, role
from identity.app_user, (values ('ADMINISTRATOR'), ('WORKFLOW_MANAGER')) as r (role)
where lower(email) = 'ana.dias@example.org';

insert into identity.app_group (name) values ('Divisão Financeira') on conflict do nothing;

insert into identity.group_member (group_id, user_id)
select g.id, u.id
from identity.app_group g, identity.app_user u
where g.name = 'Divisão Financeira' and lower(u.email) = 'ana.dias@example.org';

-- People are never deleted. Inactive, they can't sign in, and their open sessions end.
update identity.app_user set active = false where lower(email) = 'ana.dias@example.org';
```

`WORKFLOW_MANAGER` opens the designer. Nothing asks for `ADMINISTRATOR` yet. A workflow's step keeps its group's name, so after renaming a group, choose it again in the drafts that use it.

---

## Project layout

| Path | What it is |
|---|---|
| `apps/server` | The Spring Boot app, split into modules: `designer`, `engine`, `registry`, `identity` |
| `apps/web` | The React web app |
| `packages/spec` | Specification model, plain Java |
| `packages/plugin-sdk` | The interface plugins implement, plain Java |
| `plugins/` | Built-in plugins, loaded at runtime from the Plugin Registry |
| `deploy/` | Local Postgres, Keycloak and the web app, and the Keycloak realm |

---

## Rules to know before writing code

- **Modules.** Code goes in one of the server's modules. A module's root package is its public API; everything else goes in its sub-packages, which other modules can't use. Each module's `package-info.java` lists the modules it may depend on. `ModularityTests` fails the build if code breaks these rules.
- **Data.** Each module owns one database schema (`identity`, `designer`, `engine`, `registry`). Only `identity` knows people, roles and groups: the other modules know a person by `authentication.getName()`, their id in defiro, and may reference `identity.app_user` and `identity.app_group` with foreign keys. The designer also reads, in SQL, the groups' names and the name of who publishes; nobody but `identity` writes there. Flyway migrations go in `apps/server/src/main/resources/db/migration` and always use schema-qualified table names, such as `engine.instance`. Name a new one `V<date>_<n>__<what>.sql`, with a date after the last one in `develop`: Flyway refuses one that arrives out of order.
- **Plugins.** The server never depends on a plugin. A plugin's id is `name@version`, generated by `plugins/pom.xml`.
- **Tests.** Name a test after what it needs: `*Test` when a JDK is enough, `*IT` when it starts Docker containers.
- **Acceptance tests.** Each user story has one feature file in `apps/server/src/test/resources/features`, tagged with its use case (such as `@UC03`), with one `Rule:` per acceptance criterion. Step definitions go in `io.github.defiro.acceptance`, one sub-package per module, and call the REST API with REST Assured. `FakeOidcProvider` is the identity provider that signs people in, and `Organization` has the people, roles and groups. Tag a scenario `@wip` until it passes: the pipeline skips `@wip`. To run one story locally: `./mvnw verify -DskipUTs -Dcucumber.filter.tags="@UC03"`. The report is in `apps/server/target/cucumber/report.html`.
- **Browser scenarios.** A scenario tagged `@browser` opens the web app in Chromium through Playwright for Java (`acceptance.WebBrowser`), and the server under test serves the web app's build, as nginx does in production. Locally they are left out unless asked for: run `npm run build` in `apps/web`, then `./mvnw verify -DskipUTs -Dcucumber.filter.tags="@browser"`. The first run downloads Chromium. The Acceptance stage of CI builds the web app and runs them. To watch them in a Chromium window, add `-Dplaywright.headed=true`: each action then waits 500 ms, or what `-Dplaywright.slowMo=<ms>` says, so the eye can follow it.

---

## Common commands

```bash
./mvnw verify                                        # everything CI runs for the server; needs Docker
./mvnw verify -DskipITs                              # commit stage only: formatting, build and unit tests, no Docker
./mvnw spotless:apply                                # formats the Java code
docker compose -f deploy/compose.yaml up -d --wait   # starts Postgres, Keycloak and the web app
docker compose -f deploy/compose.yaml down           # stops them; add -v to delete their data
```

---

## Quality checks

Every pull request and every push to `main` runs the CI workflow. Each stage starts only when the one before it is green, and a pull request can merge only when every check passes.

| Stage | Check | What it does |
|---|---|---|
| Commit | **Commit (server)** | Checks formatting, builds, and runs the unit tests (`*Test`). Needs no Docker. |
| | **Commit (web)** | Verifies the npm packages' signatures, checks formatting, lint and types, runs the Vitest tests and builds the web app. |
| | **Workflow lint** | Tests the CI scripts and checks every workflow with zizmor. |
| | **Dependency review** | Fails a pull request that adds a dependency with a known vulnerability. |
| Acceptance | **Acceptance** | Runs the integration tests (`*IT`) against PostgreSQL in Testcontainers. |
| Quality | **Quality gate** | Analyses both apps in SonarQube Cloud, with the coverage of the server's two test stages and of the web app's tests. |

Run the same checks before you push:

| App | Same checks as CI | Fix formatting |
|---|---|---|
| Server | `./mvnw verify` | `./mvnw spotless:apply` |
| Web | `npm run check` in `apps/web` | `npm run format` in `apps/web` |

The web app uses the Node major in `apps/web/.nvmrc`. Its `.npmrc` refuses install scripts and packages published less than 7 days ago, which gives the registry time to pull a compromised release. To install a younger package on purpose, say why in the pull request and run `npm install <package>@<version> --min-release-age=0`.

The quality gate has two parts:

- **On new code:** no new issues, at least 80% coverage, at most 3% duplication, and every new security hotspot reviewed.
- **On the overall code:** security rating A.

When a check is red, open the run from the pull request: the log names the failing test, and the test reports are in the run's artifacts.

<details>
<summary><b>How the gate is enforced</b> (click to expand)</summary>

SonarQube Cloud's built-in **Sonar way** gate checks coverage, duplication and hotspots, but it judges new issues by ratings, so a few minor code smells would still pass it. Its free plan can't change the gate, so CI adds the other two conditions through SonarQube's API: a pull request fails if it adds any issue, and the gate fails if `main`'s code doesn't have security rating A. An issue you accept in SonarQube Cloud, with the reason, no longer counts as new.

If `main` loses its A (for example when SonarQube adds a rule that finds an issue in existing code), every pull request fails the gate until it's fixed: to merge the fix, a maintainer marks the listed issues as Accepted in SonarQube Cloud, with the reason, and re-runs the gate.

Pull requests from forks can't read the SonarQube token, so the gate fails for them until a maintainer runs the analysis.

</details>

---

## Editor setup

IntelliJ offers to install **palantir-java-format**, so *Reformat Code* gives the same result as CI, and **SonarQube for IDE**, which then offers to bind to the SonarQube Cloud project and flags the same issues while you code.

VS Code recommends **Prettier**, **EditorConfig** and **SonarQube for IDE** when you open the folder.

> [!TIP]
> The repository stores text files with LF line endings. On Windows, if the formatting check reports files you didn't change, your working copy still has CRLF line endings. Commit or stash your work, then refresh it once:
>
> ```bash
> git rm -rq --cached . && git reset -q --hard
> ```

---

## Dependencies

Dependabot opens pull requests for outdated or vulnerable dependencies. Version updates wait 7 days after a release, so a compromised release is usually caught before it reaches us; security updates come straight away.

---

## License

Defiro is open source under the [European Union Public Licence v. 1.2](LICENSE) (EUPL-1.2).
