package com.server.question.config;

import java.util.List;
import com.server.place.support.TourApiTheme;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

@Configuration
public class QuestionSeedInitializer {

    @Bean
    ApplicationRunner seedTripQuestions(JdbcTemplate jdbcTemplate) {
        return args -> {
            seedQuestion(jdbcTemplate, new QuestionSeed(
                    "COMPANION",
                    "누구와 여행하나요?",
                    "SINGLE_CHOICE",
                    true,
                    1,
                    1,
                    1,
                    1,
                    List.of(
                            new AnswerSeed("COMPANION_SOLO", "혼자", 1),
                            new AnswerSeed("COMPANION_FRIENDS", "친구와", 2),
                            new AnswerSeed("COMPANION_COUPLE", "배우자·연인과", 3),
                            new AnswerSeed("COMPANION_FAMILY_WITH_CHILD", "아이와", 4),
                            new AnswerSeed("COMPANION_PARENTS", "부모님과", 5),
                            new AnswerSeed("COMPANION_OTHER", "기타", 6)
                    )
            ));
            seedQuestion(jdbcTemplate, new QuestionSeed(
                    "MOBILITY",
                    "이동할 때 고려할 점이 있나요?",
                    "SINGLE_CHOICE",
                    true,
                    1,
                    1,
                    1,
                    2,
                    List.of(
                            new AnswerSeed("MOBILITY_NORMAL", "특별히 없어요", 1),
                            new AnswerSeed("MOBILITY_LOW_WALK", "걷는 구간을 줄여주세요", 2)
                    )
            ));
            seedQuestion(jdbcTemplate, new QuestionSeed(
                    "PACE",
                    "하루 일정을 어떻게 구성할까요?",
                    "SINGLE_CHOICE",
                    true,
                    1,
                    1,
                    2,
                    3,
                    List.of(
                            new AnswerSeed("PACE_PACKED", "빼곡하고 알찬 일정", 1),
                            new AnswerSeed("PACE_RELAXED", "널널하고 여유로운 일정", 2)
                    )
            ));
            seedQuestion(jdbcTemplate, new QuestionSeed(
                    "TRANSIT",
                    "대중교통은 어떻게 이용할까요?",
                    "SINGLE_CHOICE",
                    true,
                    1,
                    1,
                    2,
                    4,
                    List.of(
                            new AnswerSeed("TRANSIT_SIMPLE", "환승은 적게", 1),
                            new AnswerSeed("TRANSIT_FAST", "빠른 이동 우선", 2)
                    )
            ));
            seedQuestion(jdbcTemplate, new QuestionSeed(
                    "THEME",
                    "어떤 여행을 선호하나요?",
                    "MULTIPLE_CHOICE",
                    true,
                    1,
                    3,
                    3,
                    5,
                    List.of(
                            answerSeed(TourApiTheme.FOOD, 1),
                            answerSeed(TourApiTheme.NATURE, 2),
                            answerSeed(TourApiTheme.CULTURE, 3),
                            answerSeed(TourApiTheme.ACTIVITY, 4),
                            answerSeed(TourApiTheme.SHOPPING, 5),
                            answerSeed(TourApiTheme.HEALING, 6)
                    )
            ));
        };
    }

    private AnswerSeed answerSeed(TourApiTheme theme, int displayOrder) {
        return new AnswerSeed(theme.answerId(), theme.label(), displayOrder);
    }

    private void seedQuestion(JdbcTemplate jdbcTemplate, QuestionSeed question) {
        if (exists(jdbcTemplate, "questions", question.id())) {
            jdbcTemplate.update("""
                    update questions
                    set text = ?, type = ?, required = ?, min_selections = ?, max_selections = ?, ui_step = ?,
                        display_order = ?, active = true
                    where id = ?
                    """,
                    question.text(),
                    question.type(),
                    question.required(),
                    question.minSelections(),
                    question.maxSelections(),
                    question.uiStep(),
                    question.displayOrder(),
                    question.id()
            );
        } else {
            jdbcTemplate.update("""
                    insert into questions(
                        id, text, type, required, min_selections, max_selections, ui_step, display_order, active
                    ) values (?, ?, ?, ?, ?, ?, ?, ?, true)
                    """,
                    question.id(),
                    question.text(),
                    question.type(),
                    question.required(),
                    question.minSelections(),
                    question.maxSelections(),
                    question.uiStep(),
                    question.displayOrder()
            );
        }

        jdbcTemplate.update("update answers set active = false where question_id = ?", question.id());

        for (AnswerSeed answer : question.answers()) {
            if (exists(jdbcTemplate, "answers", answer.id())) {
                jdbcTemplate.update("""
                        update answers
                        set question_id = ?, label = ?, display_order = ?, active = true
                        where id = ?
                        """,
                        question.id(),
                        answer.label(),
                        answer.displayOrder(),
                        answer.id()
                );
            } else {
                jdbcTemplate.update("""
                        insert into answers(id, question_id, label, display_order, active)
                        values (?, ?, ?, ?, true)
                        """,
                        answer.id(),
                        question.id(),
                        answer.label(),
                        answer.displayOrder()
                );
            }
        }
    }

    private boolean exists(JdbcTemplate jdbcTemplate, String tableName, String id) {
        Integer count = jdbcTemplate.queryForObject(
                "select count(*) from " + tableName + " where id = ?",
                Integer.class,
                id
        );
        return count != null && count > 0;
    }

    private record QuestionSeed(
            String id,
            String text,
            String type,
            boolean required,
            int minSelections,
            int maxSelections,
            int uiStep,
            int displayOrder,
            List<AnswerSeed> answers
    ) {
    }

    private record AnswerSeed(String id, String label, int displayOrder) {
    }
}
