package com.server.place.config;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.server.place.repository.PlaceRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.ApplicationRunner;

@DisplayName("로컬 일정 생성 장소 fixture")
class DummyPlaceDataInitializerTest {

    @Test
    @DisplayName("빈 저장소에 Planner 후보 장소 묶음을 저장한다")
    void seedsPlannerCandidatePlaces() throws Exception {
        PlaceRepository placeRepository = mock(PlaceRepository.class);
        when(placeRepository.count()).thenReturn(0L);
        ApplicationRunner runner = new DummyPlaceDataInitializer().seedDummyPlace(placeRepository);

        runner.run(null);

        verify(placeRepository).saveAll(argThat(places -> places.spliterator().getExactSizeIfKnown() == 8));
    }
}
