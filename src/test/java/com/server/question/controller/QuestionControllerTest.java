package com.server.question.controller;

import com.server.question.dto.QuestionResponse;
import com.server.question.dto.TripQuestionsResponse;
import com.server.question.service.QuestionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import com.server.answer.dto.AnswerResponse;

import java.util.List;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

// 슬라이스(@WebMvcTest) 대신 전체 컨텍스트를 쓴다. 보안 필터가 늘 때마다 필터가 의존하는
// 빈을 하나씩 목으로 채워야 해서, 이 저장소의 다른 컨트롤러 테스트와 방식을 맞춘다.
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class QuestionControllerTest {

    @MockitoBean
    private QuestionService questionService;

    /**
     * 슬라이스 테스트는 서비스 계층을 싣지 않는다. SecurityConfig 가 인증 필터를 거치면서
     * 필터가 의존하는 토큰 제공자가 필요해진다. addFilters = false 여도 빈은 만들어진다.
     */
    @Autowired
    private MockMvc mockMvc;

    @Test
    void Question_search_success() throws Exception{
        AnswerResponse answer = new AnswerResponse("COMPANION_PARENTS", "부모님", 1);
        QuestionResponse question = new QuestionResponse("COMPANION", "누구와 여행하나요?", "SINGLE_CHOICE", true, 1, List.of(answer));
        TripQuestionsResponse response = new TripQuestionsResponse(List.of(question));

        given(questionService.getTripQuestions()).willReturn(response);

        mockMvc.perform(get("/api/v1/trip-questions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value("COMPANION"))
                .andExpect(jsonPath("$.items[0].minSelections").value(1))
                .andExpect(jsonPath("$.items[0].maxSelections").value(1))
                .andExpect(jsonPath("$.items[0].uiStep").value(1))
                .andExpect(jsonPath("$.items[0].answers[0].label").value("부모님"));
    }
}
