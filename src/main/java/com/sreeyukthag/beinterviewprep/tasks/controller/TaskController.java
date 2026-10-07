package com.sreeyukthag.beinterviewprep.tasks.controller;

import com.sreeyukthag.beinterviewprep.common.web.ApiResponse;
import com.sreeyukthag.beinterviewprep.common.web.PageResponse;
import com.sreeyukthag.beinterviewprep.tasks.dto.request.CreateTaskRequest;
import com.sreeyukthag.beinterviewprep.tasks.dto.request.UpdateTaskRequest;
import com.sreeyukthag.beinterviewprep.tasks.dto.response.TaskResponse;
import com.sreeyukthag.beinterviewprep.tasks.entity.TaskStatus;
import com.sreeyukthag.beinterviewprep.tasks.service.TaskService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/api/v1/tasks")
@RequiredArgsConstructor
public class TaskController {

    private final TaskService taskService;

    @PostMapping
    public ResponseEntity<ApiResponse<TaskResponse>> create(@Valid @RequestBody CreateTaskRequest request) {
        TaskResponse task = taskService.create(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(task.id())
                .toUri();

        return ResponseEntity.created(location).body(ApiResponse.ok(task));
    }

    @GetMapping
    public ApiResponse<PageResponse<TaskResponse>> list(
            @RequestParam(required = false) TaskStatus status,
            @PageableDefault(sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ApiResponse.ok(taskService.list(status, pageable));
    }

    @GetMapping("/{id}")
    public ApiResponse<TaskResponse> get(@PathVariable UUID id) {
        return ApiResponse.ok(taskService.get(id));
    }

    @PutMapping("/{id}")
    public ApiResponse<TaskResponse> update(@PathVariable UUID id, @Valid @RequestBody UpdateTaskRequest request) {
        return ApiResponse.ok(taskService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        taskService.delete(id);

        return ResponseEntity.noContent().build();
    }
}
