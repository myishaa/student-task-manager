package com.ccd.studenttaskmanager.task;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TaskServiceTest {

    @Mock
    private TaskRepository repository;

    @InjectMocks
    private TaskService service;

    @Test
    void createTrimsTextAndDefaultsStatusToTodo() {
        when(repository.save(any(Task.class))).thenAnswer(invocation -> invocation.getArgument(0));
        TaskRequest request = new TaskRequest(
                "  Review cloud notes  ",
                "  Prepare for class  ",
                LocalDate.of(2026, 10, 25),
                TaskPriority.HIGH,
                null);

        TaskResponse response = service.create(request);

        assertEquals("Review cloud notes", response.title());
        assertEquals("Prepare for class", response.description());
        assertEquals(LocalDate.of(2026, 10, 25), response.dueDate());
        assertEquals(TaskPriority.HIGH, response.priority());
        assertEquals(TaskStatus.TODO, response.status());
    }

    @Test
    void updateStatusChangesAndSavesTheExistingTask() {
        Task task = new Task("Review cloud notes", null, null, TaskPriority.MEDIUM, TaskStatus.TODO);
        when(repository.findById(7L)).thenReturn(Optional.of(task));
        when(repository.save(task)).thenReturn(task);

        TaskResponse response = service.updateStatus(7L, TaskStatus.DONE);

        assertEquals(TaskStatus.DONE, response.status());
        verify(repository).save(task);
    }

    @Test
    void getMissingTaskReturnsNotFound() {
        when(repository.findById(404L)).thenReturn(Optional.empty());

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.get(404L));

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
        assertEquals("Task 404 was not found", exception.getReason());
    }
}
