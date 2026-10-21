package com.ccd.studenttaskmanager.task;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
@Transactional
public class TaskService {

    private final TaskRepository repository;

    public TaskService(TaskRepository repository) {
        this.repository = repository;
    }

    public TaskResponse create(TaskRequest request) {
        Task task = new Task(request.title().trim(), normalize(request.description()),
                request.dueDate(), request.priority(),
                request.status() == null ? TaskStatus.TODO : request.status());
        return TaskResponse.from(repository.save(task));
    }

    @Transactional(readOnly = true)
    public List<TaskResponse> list(TaskStatus status) {
        List<Task> tasks = status == null
                ? repository.findAll()
                : repository.findAllByStatusOrderByDueDateAscIdAsc(status);
        return tasks.stream().map(TaskResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public TaskResponse get(long id) {
        return TaskResponse.from(find(id));
    }

    public TaskResponse replace(long id, TaskRequest request) {
        Task task = find(id);
        task.update(request.title().trim(), normalize(request.description()), request.dueDate(),
                request.priority(), request.status() == null ? task.getStatus() : request.status());
        return TaskResponse.from(repository.save(task));
    }

    public TaskResponse updateStatus(long id, TaskStatus status) {
        Task task = find(id);
        task.updateStatus(status);
        return TaskResponse.from(repository.save(task));
    }

    public void delete(long id) {
        repository.delete(find(id));
    }

    private Task find(long id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Task " + id + " was not found"));
    }

    private String normalize(String text) {
        return text == null || text.isBlank() ? null : text.trim();
    }
}
