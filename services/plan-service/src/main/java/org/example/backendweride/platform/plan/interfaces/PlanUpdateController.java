package org.example.backendweride.platform.plan.interfaces;

import org.example.backendweride.platform.plan.domain.model.aggregates.Plan;
import org.example.backendweride.platform.plan.infrastructure.persistence.jpa.PlanRepository;
import org.example.backendweride.platform.plan.interfaces.resources.CreatePlanResource;
import org.example.backendweride.platform.plan.interfaces.transform.CreatePlanCommandFronResourceAssembler;
import org.example.backendweride.platform.shared.security.CurrentUser;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
public class PlanUpdateController {
    private final PlanRepository plans;

    public PlanUpdateController(PlanRepository plans) {
        this.plans = plans;
    }

    @PutMapping("/api/v1/plans/{id}")
    public Plan update(
            @PathVariable Long id,
            @RequestBody CreatePlanResource resource,
            Authentication authentication) {
        CurrentUser.requireAdmin(authentication);
        var plan =
                plans.findById(id)
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        return plans.save(
                plan.update(
                        CreatePlanCommandFronResourceAssembler.toCommandFromResource(resource)));
    }
}
