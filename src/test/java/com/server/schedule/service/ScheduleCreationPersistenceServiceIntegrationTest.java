package com.server.schedule.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.server.common.error.BusinessException;
import com.server.schedule.domain.SchedulePreview;
import com.server.schedule.repository.ScheduleCreationRequestRepository;
import com.server.schedule.repository.SchedulePreviewRepository;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
@DisplayName("일정 생성 요청 선점")
class ScheduleCreationPersistenceServiceIntegrationTest {

    @Autowired
    private ScheduleCreationPersistenceService persistenceService;

    @Autowired
    private SchedulePreviewRepository previewRepository;

    @Autowired
    private ScheduleCreationRequestRepository requestRepository;

    @Test
    @DisplayName("서로 다른 키가 같은 Preview를 동시에 선점해도 한 요청만 소유한다")
    void reservesPreviewOnlyOnce() throws Exception {
        OffsetDateTime now = OffsetDateTime.parse("2026-07-15T11:00:00+09:00");
        SchedulePreview preview = previewRepository.saveAndFlush(new SchedulePreview(
                "READY", LocalDate.parse("2026-08-10"), LocalDate.parse("2026-08-10"),
                "Asia/Seoul", "UNDECIDED", "ATTRACTION_ROUTES_ONLY", "{}", "[]", null,
                "[]", "{\"preferences\":[],\"unrecognizedTexts\":[]}", "[]", "[]",
                now.plusMinutes(30), now));
        CountDownLatch start = new CountDownLatch(1);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<String> first = executor.submit(() -> reserve(
                    start, "concurrent-key-1", preview, now));
            Future<String> second = executor.submit(() -> reserve(
                    start, "concurrent-key-2", preview, now));
            start.countDown();

            assertThat(List.of(first.get(), second.get()))
                    .containsExactlyInAnyOrder("OWNER", "SCHEDULE_CREATION_IN_PROGRESS");
        } finally {
            executor.shutdownNow();
            requestRepository.deleteAll();
            previewRepository.deleteById(preview.getId());
        }
    }

    private String reserve(
            CountDownLatch start,
            String key,
            SchedulePreview preview,
            OffsetDateTime now
    ) throws InterruptedException {
        start.await();
        try {
            return persistenceService.reserve(
                    key, preview.getId(), "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef", now)
                    ? "OWNER" : "WAIT";
        } catch (BusinessException exception) {
            return exception.getErrorCode().name();
        }
    }
}
