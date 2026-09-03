package com.server.spontaneous;

import com.server.external.spontaneous.FastApiSpontaneousClient;

import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/spontaneous-trips")
public class SpontaneousTripController {

    private final FastApiSpontaneousClient fastApiSpontaneousClient;

    public SpontaneousTripController(
            FastApiSpontaneousClient fastApiSpontaneousClient
    ) {
        this.fastApiSpontaneousClient = fastApiSpontaneousClient;
    }

    @PostMapping("/destinations")
    public ResponseEntity<Map<String, Object>> recommendDestinations(
            @RequestBody Map<String, Object> request
    ) {
        Map<String, Object> response =
                fastApiSpontaneousClient.recommendDestinations(request);

        return ResponseEntity.ok(response);
    }

    @PostMapping("/course")
    public ResponseEntity<Map<String, Object>> recommendCourse(
            @RequestBody Map<String, Object> request
    ) {
        Map<String, Object> response =
                fastApiSpontaneousClient.recommendCourse(request);

        return ResponseEntity.ok(response);
    }



}