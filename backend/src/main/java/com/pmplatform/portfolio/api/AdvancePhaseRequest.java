package com.pmplatform.portfolio.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** The gate decision that lets a project leave its current phase. */
public record AdvancePhaseRequest(@NotBlank @Size(max = 2000) String decisionNote) {
}
