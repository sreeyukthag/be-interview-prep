package com.sreeyukthag.beinterviewprep.tasks.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sreeyukthag.beinterviewprep.common.exception.ResourceNotFoundException;
import com.sreeyukthag.beinterviewprep.common.web.PageResponse;
import com.sreeyukthag.beinterviewprep.tasks.dto.request.CreateTaskRequest;
import com.sreeyukthag.beinterviewprep.tasks.dto.request.UpdateTaskRequest;
import com.sreeyukthag.beinterviewprep.tasks.dto.response.TaskResponse;
import com.sreeyukthag.beinterviewprep.tasks.entity.Task;
import com.sreeyukthag.beinterviewprep.tasks.entity.TaskStatus;
import com.sreeyukthag.beinterviewprep.tasks.repository.TaskRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
class TaskServiceTest {

    private static final LocalDate TOMORROW = LocalDate.now().plusDays(1);

    @Mock
    private TaskRepository taskRepository;

    @InjectMocks
    private TaskService taskService;

    @Test
    void createDefaultsStatusToTodo() {
        CreateTaskRequest request = new CreateTaskRequest("Write spec", "First draft", null, TOMORROW);
        when(taskRepository.saveAndFlush(any(Task.class))).thenAnswer(invocation -> invocation.getArgument(0));

        TaskResponse response = taskService.create(request);

        assertThat(response.status()).isEqualTo(TaskStatus.TODO);
        assertThat(response.title()).isEqualTo("Write spec");
        assertThat(response.description()).isEqualTo("First draft");
        assertThat(response.dueDate()).isEqualTo(TOMORROW);
    }

    @Test
    void createKeepsAnExplicitStatus() {
        CreateTaskRequest request = new CreateTaskRequest("Write spec", null, TaskStatus.IN_PROGRESS, null);
        when(taskRepository.saveAndFlush(any(Task.class))).thenAnswer(invocation -> invocation.getArgument(0));

        TaskResponse response = taskService.create(request);

        assertThat(response.status()).isEqualTo(TaskStatus.IN_PROGRESS);
        assertThat(response.dueDate()).isNull();
    }

    @Test
    void listWithoutStatusReturnsAllTasks() {
        Pageable pageable = PageRequest.of(0, 20);
        Task task = new Task("Write spec", null, TaskStatus.DONE, null);
        when(taskRepository.findAll(pageable)).thenReturn(new PageImpl<>(List.of(task), pageable, 1));

        PageResponse<TaskResponse> page = taskService.list(null, pageable);

        assertThat(page.content()).extracting(TaskResponse::title).containsExactly("Write spec");
        assertThat(page.totalElements()).isEqualTo(1);
        verify(taskRepository, never()).findByStatus(any(), any());
    }

    @Test
    void listWithStatusFiltersByThatStatus() {
        Pageable pageable = PageRequest.of(0, 20);
        Task task = new Task("Ship it", null, TaskStatus.DONE, null);
        when(taskRepository.findByStatus(TaskStatus.DONE, pageable))
                .thenReturn(new PageImpl<>(List.of(task), pageable, 1));

        PageResponse<TaskResponse> page = taskService.list(TaskStatus.DONE, pageable);

        assertThat(page.content()).extracting(TaskResponse::status).containsExactly(TaskStatus.DONE);
        verify(taskRepository, never()).findAll(any(Pageable.class));
    }

    @Test
    void getReturnsTheTask() {
        UUID id = UUID.randomUUID();
        when(taskRepository.findById(id)).thenReturn(Optional.of(new Task("Write spec", null, TaskStatus.TODO, null)));

        TaskResponse response = taskService.get(id);

        assertThat(response.title()).isEqualTo("Write spec");
    }

    @Test
    void getUnknownTaskThrowsNotFound() {
        UUID id = UUID.randomUUID();
        when(taskRepository.findById(id)).thenReturn(Optional.empty());

        ResourceNotFoundException ex = assertThrows(ResourceNotFoundException.class, () -> taskService.get(id));

        assertThat(ex.getMessage()).isEqualTo("Task not found: " + id);
    }

    @Test
    void updateReplacesEveryField() {
        UUID id = UUID.randomUUID();
        Task task = new Task("Write spec", "First draft", TaskStatus.TODO, TOMORROW);
        UpdateTaskRequest request = new UpdateTaskRequest("Write final spec", null, TaskStatus.DONE, null);
        when(taskRepository.findById(id)).thenReturn(Optional.of(task));
        when(taskRepository.saveAndFlush(task)).thenReturn(task);

        TaskResponse response = taskService.update(id, request);

        assertThat(response.title()).isEqualTo("Write final spec");
        assertThat(response.description()).isNull();
        assertThat(response.status()).isEqualTo(TaskStatus.DONE);
        assertThat(response.dueDate()).isNull();
    }

    @Test
    void updateUnknownTaskThrowsNotFound() {
        UUID id = UUID.randomUUID();
        UpdateTaskRequest request = new UpdateTaskRequest("Write spec", null, TaskStatus.DONE, null);
        when(taskRepository.findById(id)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> taskService.update(id, request));

        verify(taskRepository, never()).saveAndFlush(any());
    }

    @Test
    void deleteRemovesTheTask() {
        UUID id = UUID.randomUUID();
        Task task = new Task("Write spec", null, TaskStatus.TODO, null);
        when(taskRepository.findById(id)).thenReturn(Optional.of(task));

        taskService.delete(id);

        verify(taskRepository).delete(task);
    }

    @Test
    void deleteUnknownTaskThrowsNotFound() {
        UUID id = UUID.randomUUID();
        when(taskRepository.findById(id)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> taskService.delete(id));

        verify(taskRepository, never()).delete(any());
    }
}
