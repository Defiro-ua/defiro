package io.github.defiro.plugin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class AssigneeRuleTest {

    @Test
    void aGroupRuleNamesTheGroup() {
        assertThat(new AssigneeRule.Group("divisao-financeira").groupId()).isEqualTo("divisao-financeira");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = " ")
    void aGroupRuleNeedsTheGroup(String groupId) {
        assertThatThrownBy(() -> new AssigneeRule.Group(groupId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("group");
    }

    @Test
    void theRulesThatDependOnTheRequesterNeedNothingElse() {
        assertThat(new AssigneeRule.Requester()).isEqualTo(new AssigneeRule.Requester());
        assertThat(new AssigneeRule.ManagersOfRequesterUnit()).isEqualTo(new AssigneeRule.ManagersOfRequesterUnit());
    }
}
