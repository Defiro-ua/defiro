package io.github.defiro.acceptance.engine;

import static org.assertj.core.api.Assertions.assertThat;

import io.cucumber.datatable.DataTable;
import io.cucumber.java.pt.Dado;
import io.cucumber.java.pt.E;
import io.cucumber.java.pt.Então;
import io.cucumber.java.pt.Quando;
import io.github.defiro.acceptance.AccessSteps;
import io.github.defiro.acceptance.identity.IdentitySteps;
import io.github.defiro.engine.internal.ProcedureSeed;
import io.restassured.response.Response;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;

/**
 * UC23, in Portuguese: the final document of a closed procedure. The Background goes through the
 * same seed, Entra ID and API calls as the review stories (UC17), in the steps of those stories;
 * until UC22 generates the document, a test fixture stands in for it. Each scenario gets a new
 * instance.
 */
public class DocumentoSteps {

    private static final String PROCEDURES = "/api/engine/procedures/";
    private static final String PDF = "application/pdf";
    private static final ZoneId LISBON = ZoneId.of("Europe/Lisbon");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final Map<String, String> ACCOUNT_COLUMNS =
            Map.of("nome", "name", "unidade", "unit", "cargo", "job title", "chefia", "manager", "grupos", "groups");
    private static final Map<String, String> PATH_COLUMNS = Map.of("passo", "step", "tratado por", "handled by");
    // The request form is UC13's; these steps only need a request, so it has one answer.
    private static final List<Map<String, String>> ANSWERS = List.of(Map.of("field", "Motivo", "answer", "Formação"));

    // Who handles the steps of «Ajudas de custo» before the last one, as the Background has them.
    private static final Map<String, String> APPROVERS =
            Map.of("Validação da chefia", "Rui Matos", "Verificação financeira", "Carla Dias");

    private String workflow;
    private byte[] generated;
    private Response response;

    @Autowired
    IdentitySteps identity;

    @Autowired
    RequestSteps requests;

    @Autowired
    ReviewSteps reviews;

    @Autowired
    AccessSteps access;

    @Autowired
    ProcedureSeed seed;

    @Dado("que o Entra ID da organização tem estas contas")
    public void oEntraIdTemEstasContas(DataTable accounts) {
        identity.theOrganizationsEntraIdHasTheseAccounts(withColumns(accounts, ACCOUNT_COLUMNS));
    }

    @E("o workflow {string} tem o percurso")
    public void oWorkflowTemOPercurso(String workflow, DataTable path) {
        requests.theWorkflowHasThePath(workflow, withColumns(path, PATH_COLUMNS));
    }

    @E("o pedido {string} de {person} para {string} foi aprovado por {person} e {person}")
    public void oPedidoFoiAprovado(String reference, String requester, String workflow, String first, String second) {
        this.workflow = workflow;
        requests.submitted(requester, reference, workflow, ANSWERS);
        reviews.approvedTheRequest(first, reference);
        reviews.approvedItAt(second, requests.path(workflow).get(2));
    }

    // The date and time are as the prototype shows them: «17/10/2026 às 15:30», in Lisbon.
    @E("{person} validou-o e fechou-o a {} às {}")
    public void validouEFechou(String person, String date, String time) {
        reviews.approvedItAt(person, requests.path(workflow).getLast());
        seed.closedAt(
                requests.reference(),
                LocalDate.parse(date, DATE)
                        .atTime(LocalTime.parse(time))
                        .atZone(LISBON)
                        .toOffsetDateTime());
    }

    @E("ao fechar foi gerada a ordem de reembolso {string}")
    public void aoFecharFoiGerada(String filename) {
        generated = fixture(filename);
        seed.generated(requests.reference(), filename, PDF, generated);
    }

    // Another request of the same workflow, waiting at that step: whoever handles each step before it
    // approved it. It has no document, as it isn't closed.
    @Dado("que o pedido {string} de {person} está em «{}»")
    public void oPedidoEstaEm(String reference, String requester, String step) {
        List<String> path = requests.path(workflow);
        assertThat(path).as("the path").contains(step);
        requests.submitted(requester, reference, workflow, ANSWERS);
        reviews.followsANewRequest();
        for (int k = 1; k < path.indexOf(step); k++) {
            String approver = APPROVERS.get(path.get(k));
            assertThat(approver).as("who approves " + path.get(k)).isNotNull();
            if (k == 1) {
                reviews.approvedTheRequest(approver, reference);
            } else {
                reviews.approvedItAt(approver, path.get(k));
            }
        }
    }

    @Quando("{person} pede o documento de {string}")
    public void pedeODocumento(String person, String reference) {
        response = reviews.browserOf(person).request().get(PROCEDURES + reference + "/document");
        access.answered(response);
    }

    @Então("recebe {string} como {string}")
    public void recebeComo(String filename, String mediaType) {
        assertThat(response.statusCode()).as("the document is sent").isEqualTo(200);
        assertThat(response.contentType()).isEqualTo(mediaType);
        assertThat(ContentDisposition.parse(response.header(HttpHeaders.CONTENT_DISPOSITION))
                        .getFilename())
                .isEqualTo(filename);
    }

    @Então("não há documento")
    public void naoHaDocumento() {
        assertThat(response.statusCode()).as("there is no document").isEqualTo(404);
    }

    @E("é a ordem de reembolso gerada quando o procedimento foi fechado")
    public void eAOrdemGeradaAoFechar() {
        assertThat(sha256(response.asByteArray())).isEqualTo(sha256(generated));
    }

    // The same table, with the English column names the steps of the review stories read.
    private static DataTable withColumns(DataTable table, Map<String, String> columns) {
        List<List<String>> rows = new ArrayList<>(table.asLists());
        rows.set(
                0,
                rows.getFirst().stream()
                        .map(column -> {
                            String english = columns.get(column);
                            assertThat(english).as("the column " + column).isNotNull();
                            return english;
                        })
                        .toList());
        return DataTable.create(rows, table.getTableConverter());
    }

    private static byte[] fixture(String filename) {
        try (InputStream in = DocumentoSteps.class.getResourceAsStream("/fixtures/" + filename)) {
            assertThat(in).as("the fixture " + filename).isNotNull();
            return in.readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Every Java platform has SHA-256", e);
        }
    }
}
