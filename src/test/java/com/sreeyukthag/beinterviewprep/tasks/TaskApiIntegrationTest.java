package com.sreeyukthag.beinterviewprep.tasks;

import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.sreeyukthag.beinterviewprep.tasks.dto.request.UpdateTaskRequest;
import com.sreeyukthag.beinterviewprep.tasks.entity.Task;
import com.sreeyukthag.beinterviewprep.tasks.entity.TaskStatus;
import com.sreeyukthag.beinterviewprep.tasks.repository.TaskRepository;
import com.sreeyukthag.beinterviewprep.tasks.service.TaskService;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class TaskApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TaskService taskService;

    @Autowired
    private TaskRepository taskRepository;

    @Test
    void taskLifecycleAgainstTheDatabase() throws Exception {
        String createBody = """
                {"title":"Write spec","description":"First draft","dueDate":"%s"}
                """.formatted(LocalDate.now().plusDays(3));
        String created = mockMvc.perform(post("/api/v1/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("TODO"))
                .andExpect(jsonPath("$.data.createdAt").exists())
                .andReturn()
                .getResponse()
                .getContentAsString();
        String id = JsonPath.read(created, "$.data.id");
        String updateBody = """
                {"title":"Write spec","description":"Final","status":"DONE"}
                """;

        mockMvc.perform(put("/api/v1/tasks/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("DONE"))
                .andExpect(jsonPath("$.data.dueDate").doesNotExist());
        mockMvc.perform(get("/api/v1/tasks").param("status", "DONE").param("size", "100"))
                .andExpect(jsonPath("$.data.content[?(@.id == '%s')]".formatted(id), hasSize(1)));
        mockMvc.perform(get("/api/v1/tasks").param("status", "TODO").param("size", "100"))
                .andExpect(jsonPath("$.data.content[?(@.id == '%s')]".formatted(id), hasSize(0)));
        mockMvc.perform(delete("/api/v1/tasks/{id}", id)).andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/tasks/{id}", id))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("NOT_FOUND"));
        mockMvc.perform(delete("/api/v1/tasks/{id}", id)).andExpect(status().isNotFound());
    }

    @Test
    void sortingByAnUnknownFieldReturns400() throws Exception {
        mockMvc.perform(get("/api/v1/tasks").param("sort", "nope"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("sort"));
    }

    @Test
    void unknownStatusInBodyReturns400NamingTheField() throws Exception {
        String body = """
                {"title":"Write spec","status":"ARCHIVED"}
                """;

        mockMvc.perform(post("/api/v1/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("status"));
    }

    @Test
    void unparseableDueDateReturns400NamingTheField() throws Exception {
        String body = """
                {"title":"Write spec","dueDate":"tomorrow"}
                """;

        mockMvc.perform(post("/api/v1/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("dueDate"));
    }

    @Test
    void writeBasedOnAStaleVersionIsRejected() {
        UUID id = taskRepository
                .save(new Task("Write spec", null, TaskStatus.TODO, null))
                .getId();
        Task staleCopy = taskRepository.findById(id).orElseThrow();
        taskService.update(id, new UpdateTaskRequest("Write spec", null, TaskStatus.IN_PROGRESS, null));
        staleCopy.update("Write spec", null, TaskStatus.DONE, null);

        assertThrows(ObjectOptimisticLockingFailureException.class, () -> taskRepository.save(staleCopy));
    }
}
