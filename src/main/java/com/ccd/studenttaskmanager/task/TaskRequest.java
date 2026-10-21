package com.ccd.studenttaskmanager.task;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record TaskRequest(
        @NotBlank @Size(max = 160) String title,
        @Size(max = 2000) String description,
        LocalDate dueDate,
        @NotNull TaskPriority priority,
        TaskStatus status
) {
}
