package com.ccd.studenttaskmanager.task;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TaskRepository extends JpaRepository<Task, Long> {
    List<Task> findAllByStatusOrderByDueDateAscIdAsc(TaskStatus status);
}
