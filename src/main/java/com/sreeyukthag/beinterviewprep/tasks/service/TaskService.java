package com.sreeyukthag.beinterviewprep.tasks.service;

import com.sreeyukthag.beinterviewprep.common.exception.ResourceNotFoundException;
import com.sreeyukthag.beinterviewprep.common.web.PageResponse;
import com.sreeyukthag.beinterviewprep.tasks.dto.request.CreateTaskRequest;
import com.sreeyukthag.beinterviewprep.tasks.dto.request.UpdateTaskRequest;
import com.sreeyukthag.beinterviewprep.tasks.dto.response.TaskResponse;
import com.sreeyukthag.beinterviewprep.tasks.entity.Task;
import com.sreeyukthag.beinterviewprep.tasks.entity.TaskStatus;
import com.sreeyukthag.beinterviewprep.tasks.mapper.TaskMapper;
import com.sreeyukthag.beinterviewprep.tasks.repository.TaskRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
@RequiredArgsConstructor
public class TaskService {

    private final TaskRepository taskRepository;

    public TaskResponse create(CreateTaskRequest request) {
        TaskStatus status = request.status() == null ? TaskStatus.TODO : request.status();
        Task task = new Task(request.title(), request.description(), status, request.dueDate());

        return TaskMapper.toResponse(taskRepository.save(task));
    }

    @Transactional(readOnly = true)
    public PageResponse<TaskResponse> list(TaskStatus status, Pageable pageable) {
        Page<Task> tasks =
                status == null ? taskRepository.findAll(pageable) : taskRepository.findByStatus(status, pageable);

        return PageResponse.of(tasks, TaskMapper::toResponse);
    }

    @Transactional(readOnly = true)
    public TaskResponse get(UUID id) {
        return TaskMapper.toResponse(findTask(id));
    }

    public TaskResponse update(UUID id, UpdateTaskRequest request) {
        Task task = findTask(id);
        task.update(request.title(), request.description(), request.status(), request.dueDate());

        // Flushing here fills updatedAt for the response and surfaces a version conflict before mapping.
        return TaskMapper.toResponse(taskRepository.saveAndFlush(task));
    }

    public void delete(UUID id) {
        taskRepository.delete(findTask(id));
    }

    private Task findTask(UUID id) {
        return taskRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Task", id));
    }
}
