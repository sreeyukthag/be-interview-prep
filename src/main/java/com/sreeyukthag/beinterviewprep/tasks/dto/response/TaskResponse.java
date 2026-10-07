package com.sreeyukthag.beinterviewprep.tasks.dto.response;

import com.sreeyukthag.beinterviewprep.tasks.entity.TaskStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record TaskResponse(
        UUID id,
        String title,
        String description,
        TaskStatus status,
        LocalDate dueDate,
        Instant createdAt,
        Instant updatedAt) {}
