package com.server.spontaneous;

import com.server.auth.web.CurrentUser;
import com.server.common.error.BusinessException;
import com.server.common.error.ErrorCode;
import com.server.external.spontaneous.FastApiSpontaneousClient;
import com.server.schedule.dto.ScheduleResponse;
import com.server.spontaneous.dto.SpontaneousCourseRequest;
import com.server.spontaneous.dto.SpontaneousCourseResponse;
import com.server.spontaneous.dto.SpontaneousDestinationRequest;
import com.server.spontaneous.dto.SpontaneousDestinationResponse;
import com.server.spontaneous.dto.SpontaneousScheduleRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
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
                fastApiSpontaneousClient.recommendCourse(request, CurrentUser.idOrNull());

        return ResponseEntity.ok(response);
    }

    @PostMapping("/schedules")
    @ResponseStatus(HttpStatus.CREATED)
    public ScheduleResponse saveSchedule(
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody SpontaneousScheduleRequest request
    ) {
        Long ownerId = CurrentUser.idOrNull();
        if (ownerId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        if (idempotencyKey == null || idempotencyKey.isBlank() || idempotencyKey.length() > 128) {
            throw new BusinessException(ErrorCode.IDEMPOTENCY_KEY_REQUIRED);
        }
        return fastApiSpontaneousClient.saveSchedule(request, idempotencyKey, ownerId);
    }
}
