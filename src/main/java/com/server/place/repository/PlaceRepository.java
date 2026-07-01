package com.server.place.repository;

import com.server.place.domain.Place;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlaceRepository extends JpaRepository<Place, Long> {

    List<Place> findByNameContainingIgnoreCaseOrderByNameAsc(String keyword);

    @Override
    @EntityGraph(attributePaths = {"detail", "operatingInfo", "images"})
    Optional<Place> findById(Long id);
}
