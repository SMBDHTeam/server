package com.server.spontaneous;

import com.server.external.spontaneous.FastApiSpontaneousClient;
import com.server.spontaneous.dto.SpontaneousCourseRequest;
import com.server.spontaneous.dto.SpontaneousCourseResponse;
import com.server.spontaneous.dto.SpontaneousDestinationRequest;
import com.server.spontaneous.dto.SpontaneousDestinationResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/spontaneous-trips")
public class SpontaneousTripController {

    private final FastApiSpontaneousClient fastApiSpontaneousClient;
    private final SpontaneousStartLocationValidator startLocationValidator;

    public SpontaneousTripController(
            FastApiSpontaneousClient fastApiSpontaneousClient,
            SpontaneousStartLocationValidator startLocationValidator
    ) {
        this.fastApiSpontaneousClient = fastApiSpontaneousClient;
        this.startLocationValidator = startLocationValidator;
    }

    @PostMapping("/destinations")
    public ResponseEntity<SpontaneousDestinationResponse> recommendDestinations(
            @Valid @RequestBody SpontaneousDestinationRequest request
    ) {
        startLocationValidator.validateBusan(request.startLocation());

        SpontaneousDestinationResponse response =
                fastApiSpontaneousClient.recommendDestinations(request);

        return ResponseEntity.ok(response);
    }

    @PostMapping("/course")
    public ResponseEntity<SpontaneousCourseResponse> recommendCourse(
            @Valid @RequestBody SpontaneousCourseRequest request
    ) {
        startLocationValidator.validateBusan(request.startLocation());

        SpontaneousCourseResponse response =
                fastApiSpontaneousClient.recommendCourse(request);

        return ResponseEntity.ok(response);
    }
}
