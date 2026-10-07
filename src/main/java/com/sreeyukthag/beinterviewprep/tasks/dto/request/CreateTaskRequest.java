package com.sreeyukthag.beinterviewprep.tasks.dto.request;

import com.sreeyukthag.beinterviewprep.tasks.entity.TaskStatus;
import java.time.LocalDate;

public record CreateTaskRequest(String title, String description, TaskStatus status, LocalDate dueDate) {}
