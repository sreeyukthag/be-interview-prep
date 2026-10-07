package com.sreeyukthag.beinterviewprep.tasks.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.sreeyukthag.beinterviewprep.tasks.entity.Task;
import com.sreeyukthag.beinterviewprep.tasks.entity.TaskStatus;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class TaskRepositoryTest {

    @Autowired
    private TaskRepository taskRepository;

    @Test
    void findByStatusReturnsOnlyTasksWithThatStatus() {
        taskRepository.save(new Task("Write spec", null, TaskStatus.TODO, null));
        taskRepository.save(new Task("Build API", null, TaskStatus.IN_PROGRESS, LocalDate.now()));
        taskRepository.save(new Task("Review PR", null, TaskStatus.IN_PROGRESS, null));

        Page<Task> inProgress = taskRepository.findByStatus(TaskStatus.IN_PROGRESS, PageRequest.of(0, 10));

        assertThat(inProgress.getTotalElements()).isEqualTo(2);
        assertThat(inProgress.getContent()).extracting(Task::getStatus).containsOnly(TaskStatus.IN_PROGRESS);
    }

    @Test
    void findByStatusReturnsEmptyPageWhenNoTaskMatches() {
        taskRepository.save(new Task("Write spec", null, TaskStatus.TODO, null));

        Page<Task> done = taskRepository.findByStatus(TaskStatus.DONE, PageRequest.of(0, 10));

        assertThat(done.getContent()).isEmpty();
        assertThat(done.getTotalElements()).isZero();
    }

    @Test
    void savePopulatesIdAndAuditColumns() {
        Task task = new Task("Write spec", "First draft", TaskStatus.TODO, LocalDate.now());

        Task saved = taskRepository.saveAndFlush(task);

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();
    }
}
