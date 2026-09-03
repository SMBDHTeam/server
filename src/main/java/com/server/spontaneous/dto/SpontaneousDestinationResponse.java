package com.server.spontaneous.dto;

import java.util.List;

public record SpontaneousDestinationResponse(
        List<DestinationRecommendation> destinations
) {
}
