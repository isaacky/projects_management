package com.pmplatform.portfolio.api;

import com.pmplatform.portfolio.Project;
import java.time.Instant;
import java.util.UUID;

public record ProjectResponse(
        UUID id,
        String code,
        String name,
        String description,
        String sponsor,
        String phase,
        String nextGate,
        String nextGateName,
        String status,
        long budgetMinor,
        String currency,
        Instant createdAt,
        Instant updatedAt,
        long version) {

    public static ProjectResponse from(Project p) {
        return new ProjectResponse(
                p.getId(),
                p.getCode(),
                p.getName(),
                p.getDescription(),
                p.getSponsor(),
                p.getPhase().name(),
                p.getPhase().exitGate().orElse(null),
                p.getPhase().exitGateName().orElse(null),
                p.getStatus().name(),
                p.getBudgetMinor(),
                p.getCurrency(),
                p.getCreatedAt(),
                p.getUpdatedAt(),
                p.getVersion());
    }
}
