package com.ccd.studenttaskmanager.task;

import java.time.Instant;
import java.time.LocalDate;

public record TaskResponse(
        Long id,
        String title,
        String description,
        LocalDate dueDate,
        TaskPriority priority,
        TaskStatus status,
        Instant createdAt,
        Instant updatedAt
) {
    static TaskResponse from(Task task) {
        return new TaskResponse(task.getId(), task.getTitle(), task.getDescription(),
                task.getDueDate(), task.getPriority(), task.getStatus(),
                task.getCreatedAt(), task.getUpdatedAt());
    }
}
