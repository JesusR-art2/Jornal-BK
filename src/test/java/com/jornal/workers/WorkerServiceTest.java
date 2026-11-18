package com.jornal.workers;

import com.jornal.catalog.CatalogApi;
import com.jornal.users.Rol;
import com.jornal.users.UserApi;
import com.jornal.users.UserSummary;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WorkerServiceTest {

    @Mock WorkerProfileRepository repository;
    @Mock WorkerProfileMapper mapper;
    @Mock UserApi userApi;
    @Mock CatalogApi catalogApi;

    @InjectMocks WorkerService service;

    @Test
    void findSummaryByIdReturnsWorkerSummary() {
        UUID id = UUID.randomUUID();
        UUID categoryId = UUID.randomUUID();
        when(userApi.findSummaryById(id)).thenReturn(Optional.of(
                new UserSummary(id, "Ana", Rol.WORKER, true, "CO", "es-CO")));

        WorkerProfile profile = new WorkerProfile();
        profile.setUserId(id);
        profile.setAvailable(true);
        profile.setWorkRadiusKm(15);
        profile.setCategoryIds(Set.of(categoryId));
        profile.setRatingAvg(new BigDecimal("4.50"));
        profile.setRatingCount(3);
        when(repository.findById(id)).thenReturn(Optional.of(profile));

        Optional<WorkerSummary> summary = service.findSummaryById(id);

        assertThat(summary).isPresent();
        assertThat(summary.get().workerId()).isEqualTo(id);
        assertThat(summary.get().name()).isEqualTo("Ana");
        assertThat(summary.get().verified()).isTrue();
        assertThat(summary.get().available()).isTrue();
        assertThat(summary.get().categoryIds()).containsExactly(categoryId);
        assertThat(summary.get().countryCode()).isEqualTo("CO");
    }

    @Test
    void findSummaryByIdIsEmptyForNonWorker() {
        UUID id = UUID.randomUUID();
        when(userApi.findSummaryById(id)).thenReturn(Optional.of(
                new UserSummary(id, "Applicant", Rol.APPLICANT, true, "CO", "es-CO")));

        assertThat(service.findSummaryById(id)).isEmpty();
    }
}
