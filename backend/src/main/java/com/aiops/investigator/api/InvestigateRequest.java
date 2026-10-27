package com.aiops.investigator.api;

import com.aiops.investigator.incident.IncidentType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;

/**
 * The JSON body for {@code POST /api/incidents/investigate}.
 *
 * <p>Validation annotations are checked before the controller method runs. {@code @NotBlank}
 * rejects a missing or empty string. {@code failureTime} is an {@link Instant}, so the JSON value
 * must include a time zone such as {@code Z}.
 */
public record InvestigateRequest(
    @Size(max = 500) String title,
    @NotBlank(message = "workloadId is required") @Size(max = 200) String workloadId,
    @NotBlank(message = "environment is required") @Size(max = 100) String environment,
    @NotNull(message = "failureTime is required") Instant failureTime,
    @NotNull(message = "lookbackMinutes is required")
        @Min(value = 1, message = "lookbackMinutes must be at least 1")
        @Max(value = 10080, message = "lookbackMinutes must be at most 10080")
        Integer lookbackMinutes,
    @Size(max = 200) String autosysJobName,
    @Size(max = 200) String jobRunId,
    IncidentType incidentType) {}
