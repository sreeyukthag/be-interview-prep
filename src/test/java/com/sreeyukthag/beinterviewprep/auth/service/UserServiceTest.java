package com.sreeyukthag.beinterviewprep.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

import com.sreeyukthag.beinterviewprep.auth.repository.UserRepository;
import com.sreeyukthag.beinterviewprep.common.exception.ResourceNotFoundException;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private UserService userService;

    @Test
    void profileOfAMissingUserIsNotFound() {
        UUID id = UUID.randomUUID();
        when(userRepository.findById(id)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> userService.getProfile(id));
    }

    @Test
    void listCapsPageSizeAndIgnoresClientSort() {
        ArgumentCaptor<Pageable> requested = ArgumentCaptor.forClass(Pageable.class);
        when(userRepository.findAll(requested.capture())).thenReturn(Page.empty());

        userService.listUsers(PageRequest.of(2, 5_000, Sort.by("passwordHash")));

        assertThat(requested.getValue().getPageNumber()).isEqualTo(2);
        assertThat(requested.getValue().getPageSize()).isEqualTo(UserService.MAX_PAGE_SIZE);
        assertThat(requested.getValue().getSort()).isEqualTo(Sort.by("createdAt", "id"));
    }
}
