package com.sreeyukthag.beinterviewprep.tasks.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.endsWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sreeyukthag.beinterviewprep.common.exception.ResourceNotFoundException;
import com.sreeyukthag.beinterviewprep.common.web.PageResponse;
import com.sreeyukthag.beinterviewprep.tasks.dto.request.CreateTaskRequest;
import com.sreeyukthag.beinterviewprep.tasks.dto.request.UpdateTaskRequest;
import com.sreeyukthag.beinterviewprep.tasks.dto.response.TaskResponse;
import com.sreeyukthag.beinterviewprep.tasks.entity.TaskStatus;
import com.sreeyukthag.beinterviewprep.tasks.service.TaskService;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(TaskController.class)
@AutoConfigureMockMvc(addFilters = false)
class TaskControllerTest {

    private static final UUID ID = UUID.fromString("7f6c2a5e-3d1b-4c8e-9a0f-1b2c3d4e5f60");
    private static final LocalDate TOMORROW = LocalDate.now().plusDays(1);

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TaskService taskService;

    @Test
    void createReturns201WithLocationAndTask() throws Exception {
        when(taskService.create(any(CreateTaskRequest.class))).thenReturn(task(TaskStatus.TODO));
        String body = """
                {"title":"Write spec","description":"First draft","dueDate":"%s"}
                """.formatted(TOMORROW);

        mockMvc.perform(post("/api/v1/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", endsWith("/api/v1/tasks/" + ID)))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(ID.toString()))
                .andExpect(jsonPath("$.data.status").value("TODO"))
                .andExpect(jsonPath("$.data.dueDate").value(TOMORROW.toString()));
    }

    @Test
    void listWithStatusPassesTheFilterToTheService() throws Exception {
        when(taskService.list(eq(TaskStatus.DONE), any(Pageable.class)))
                .thenReturn(new PageResponse<>(List.of(task(TaskStatus.DONE)), 0, 20, 1, 1, true, true));

        mockMvc.perform(get("/api/v1/tasks").param("status", "DONE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].status").value("DONE"))
                .andExpect(jsonPath("$.data.totalElements").value(1));
    }

    @Test
    void listWithoutParamsUsesNoFilterAndNewestFirst() throws Exception {
        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        when(taskService.list(eq(null), pageable.capture()))
                .thenReturn(new PageResponse<>(List.of(), 0, 20, 0, 0, true, true));

        mockMvc.perform(get("/api/v1/tasks")).andExpect(status().isOk());

        Sort.Order order = pageable.getValue().getSort().getOrderFor("createdAt");
        assertThat(order).isNotNull();
        assertThat(order.isDescending()).isTrue();
    }

    @Test
    void listWithUnknownStatusReturns400() throws Exception {
        mockMvc.perform(get("/api/v1/tasks").param("status", "ARCHIVED"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[0].field").value("status"));
    }

    @Test
    void getReturnsTheTask() throws Exception {
        when(taskService.get(ID)).thenReturn(task(TaskStatus.IN_PROGRESS));

        mockMvc.perform(get("/api/v1/tasks/{id}", ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("Write spec"))
                .andExpect(jsonPath("$.data.status").value("IN_PROGRESS"));
    }

    @Test
    void getUnknownTaskReturns404() throws Exception {
        when(taskService.get(ID)).thenThrow(new ResourceNotFoundException("Task", ID));

        mockMvc.perform(get("/api/v1/tasks/{id}", ID))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("Task not found: " + ID));
    }

    @Test
    void getWithMalformedIdReturns400() throws Exception {
        mockMvc.perform(get("/api/v1/tasks/{id}", "not-a-uuid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("id"));
    }

    @Test
    void updateReturnsTheUpdatedTask() throws Exception {
        when(taskService.update(eq(ID), any(UpdateTaskRequest.class))).thenReturn(task(TaskStatus.DONE));
        String body = """
                {"title":"Write spec","status":"DONE"}
                """;

        mockMvc.perform(put("/api/v1/tasks/{id}", ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("DONE"));
    }

    @Test
    void updateUnknownTaskReturns404() throws Exception {
        when(taskService.update(eq(ID), any(UpdateTaskRequest.class)))
                .thenThrow(new ResourceNotFoundException("Task", ID));
        String body = """
                {"title":"Write spec","status":"DONE"}
                """;

        mockMvc.perform(put("/api/v1/tasks/{id}", ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("NOT_FOUND"));
    }

    @Test
    void deleteReturns204() throws Exception {
        mockMvc.perform(delete("/api/v1/tasks/{id}", ID)).andExpect(status().isNoContent());

        verify(taskService).delete(ID);
    }

    @Test
    void deleteUnknownTaskReturns404() throws Exception {
        doThrow(new ResourceNotFoundException("Task", ID)).when(taskService).delete(ID);

        mockMvc.perform(delete("/api/v1/tasks/{id}", ID))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("NOT_FOUND"));
    }

    @Test
    void malformedJsonReturns400WithoutCallingTheService() throws Exception {
        mockMvc.perform(post("/api/v1/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("MALFORMED_REQUEST"));

        verify(taskService, never()).create(any());
    }

    @Test
    void createWithoutTitleReturns400WithFieldError() throws Exception {
        String body = """
                {"description":"No title"}
                """;

        mockMvc.perform(post("/api/v1/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[0].field").value("title"))
                .andExpect(jsonPath("$.errors[0].message").value("is required"));

        verify(taskService, never()).create(any());
    }

    @Test
    void createWithBlankTitleReturns400() throws Exception {
        String body = """
                {"title":"   "}
                """;

        mockMvc.perform(post("/api/v1/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("title"));
    }

    @Test
    void createWithTitleOver100CharactersReturns400() throws Exception {
        String body = """
                {"title":"%s"}
                """.formatted("a".repeat(101));

        mockMvc.perform(post("/api/v1/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("title"))
                .andExpect(jsonPath("$.errors[0].message").value("must be at most 100 characters"));
    }

    @Test
    void createWithTitleOfExactly100CharactersIsAccepted() throws Exception {
        when(taskService.create(any(CreateTaskRequest.class))).thenReturn(task(TaskStatus.TODO));
        String body = """
                {"title":"%s"}
                """.formatted("a".repeat(100));

        mockMvc.perform(post("/api/v1/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated());
    }

    @Test
    void createWithPastDueDateReturns400() throws Exception {
        String body = """
                {"title":"Write spec","dueDate":"%s"}
                """.formatted(LocalDate.now().minusDays(1));

        mockMvc.perform(post("/api/v1/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("dueDate"))
                .andExpect(jsonPath("$.errors[0].message").value("must not be in the past"));

        verify(taskService, never()).create(any());
    }

    @Test
    void createWithTodayAsDueDateIsAccepted() throws Exception {
        when(taskService.create(any(CreateTaskRequest.class))).thenReturn(task(TaskStatus.TODO));
        String body = """
                {"title":"Write spec","dueDate":"%s"}
                """.formatted(LocalDate.now());

        mockMvc.perform(post("/api/v1/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated());
    }

    @Test
    void createWithDescriptionOver1000CharactersReturns400() throws Exception {
        String body = """
                {"title":"Write spec","description":"%s"}
                """.formatted("d".repeat(1001));

        mockMvc.perform(post("/api/v1/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("description"));
    }

    @Test
    void createReportsEveryInvalidField() throws Exception {
        String body = """
                {"title":"%s","dueDate":"%s"}
                """.formatted("a".repeat(101), LocalDate.now().minusDays(1));

        mockMvc.perform(post("/api/v1/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.length()").value(2))
                .andExpect(jsonPath("$.errors[*].field", containsInAnyOrder("title", "dueDate")));
    }

    @Test
    void updateWithoutStatusReturns400() throws Exception {
        String body = """
                {"title":"Write spec"}
                """;

        mockMvc.perform(put("/api/v1/tasks/{id}", ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("status"))
                .andExpect(jsonPath("$.errors[0].message").value("is required"));

        verify(taskService, never()).update(any(), any());
    }

    @Test
    void updateWithPastDueDateReturns400() throws Exception {
        String body = """
                {"title":"Write spec","status":"DONE","dueDate":"%s"}
                """.formatted(LocalDate.now().minusDays(1));

        mockMvc.perform(put("/api/v1/tasks/{id}", ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("dueDate"));
    }

    private static TaskResponse task(TaskStatus status) {
        Instant now = Instant.parse("2026-10-07T10:00:00Z");
        return new TaskResponse(ID, "Write spec", "First draft", status, TOMORROW, now, now);
    }
}
