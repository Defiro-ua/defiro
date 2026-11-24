package io.github.defiro.designer.internal;

import io.github.defiro.designer.internal.GroupOptions.Option;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Who can make a request and who can handle a step (UC05), for the designer's cards. */
@PreAuthorize("hasRole('WORKFLOW_MANAGER')")
@RestController
class GroupController {

    private final GroupOptions options;

    GroupController(GroupOptions options) {
        this.options = options;
    }

    record Choices(List<Option> requesters, List<Option> handlers) {}

    @GetMapping("/api/designer/groups")
    Choices groups() {
        return new Choices(options.requesters(), options.handlers());
    }
}
