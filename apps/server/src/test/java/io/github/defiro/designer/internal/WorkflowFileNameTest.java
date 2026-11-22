package io.github.defiro.designer.internal;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class WorkflowFileNameTest {

    @ParameterizedTest
    @CsvSource(
            delimiter = '|',
            nullValues = "null",
            value = {
                "Ajudas de custo                 | ajudas-de-custo",
                "Ajudas de custo – Ação Social   | ajudas-de-custo-acao-social",
                "«Férias» 2026!                  | ferias-2026",
                "---                             | workflow",
                "''                              | workflow",
                "null                            | workflow"
            })
    void aFileIsNamedAfterTheWorkflowInPlainLetters(String name, String file) {
        assertThat(WorkflowFileController.slug(name)).isEqualTo(file);
    }
}
