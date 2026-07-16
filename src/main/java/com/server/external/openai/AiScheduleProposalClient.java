package com.server.external.openai;

import java.util.List;

public interface AiScheduleProposalClient {

    Proposal propose(Request request);

    record Request(
            List<Day> days,
            List<Candidate> candidates,
            List<Long> mustVisitPlaceIds,
            List<SelectedAnswer> selectedAnswers,
            String customPrompt
    ) {
    }

    record Day(
            int dayNo,
            String date,
            String availableFrom,
            String availableUntil,
            String startLocationName,
            String endLocationName,
            int targetStopCount,
            int requiredMealStopCount,
            List<Long> requiredPlaceIds
    ) {
    }

    record Candidate(
            long placeId,
            String name,
            String category,
            String contentTypeId,
            String longitude,
            String latitude,
            boolean mealPlace
    ) {
    }

    record SelectedAnswer(String questionId, String answerId) {
    }

    record Proposal(List<DayProposal> days, int confidence, String summary) {
    }

    record DayProposal(int dayNo, List<Long> placeIds) {
    }
}
