package com.sreeyukthag.beinterviewprep.tasks.dto.request;

import com.sreeyukthag.beinterviewprep.tasks.entity.TaskStatus;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record CreateTaskRequest(
        @NotBlank(message = "is required") @Size(max = 100, message = "must be at most 100 characters")
        String title,

        @Size(max = 1000, message = "must be at most 1000 characters")
        String description,

        TaskStatus status,

        @FutureOrPresent(message = "must not be in the past")
        LocalDate dueDate) {}
