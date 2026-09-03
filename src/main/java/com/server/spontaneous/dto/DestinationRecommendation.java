package com.server.spontaneous.dto;

public record DestinationRecommendation(
        String destinationId,
        String name,
        Double themeScore,
        Integer distanceMeters,
        TransportSummary transport
) {
}
