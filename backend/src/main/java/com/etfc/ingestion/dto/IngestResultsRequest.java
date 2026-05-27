package com.etfc.ingestion.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

public record IngestResultsRequest(
    @NotNull @Size(min = 1) List<@Valid IngestResultRequest> results) {}
