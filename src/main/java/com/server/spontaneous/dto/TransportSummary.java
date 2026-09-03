package com.server.spontaneous.dto;

public record TransportSummary(
        TransportMode mode,
        Integer outboundMinutes,
        Integer returnMinutes,
        Integer availableStayMinutes
) {
}
