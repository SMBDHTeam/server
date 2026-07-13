package com.server.question.config;

import java.util.List;
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
                    List.of(
                            new AnswerSeed("COMPANION_SOLO", "혼자", 1),
                            new AnswerSeed("COMPANION_COUPLE", "연인과", 2),
                            new AnswerSeed("COMPANION_FRIENDS", "친구와", 3),
                            new AnswerSeed("COMPANION_PARENTS", "부모님과", 4),
                            new AnswerSeed("COMPANION_FAMILY_WITH_CHILD", "아이와", 5)
                    )
            ));
            seedQuestion(jdbcTemplate, new QuestionSeed(
                    "PACE",
                    "어떤 속도의 여행을 원하나요?",
                    "SINGLE_CHOICE",
                    true,
                    2,
                    List.of(
                            new AnswerSeed("PACE_RELAXED", "여유롭게", 1),
                            new AnswerSeed("PACE_BALANCED", "적당히", 2),
                            new AnswerSeed("PACE_ACTIVE", "많이 둘러보기", 3)
                    )
            ));
            seedQuestion(jdbcTemplate, new QuestionSeed(
                    "THEME",
                    "어떤 여행을 선호하나요?",
                    "SINGLE_CHOICE",
                    true,
                    3,
                    List.of(
                            new AnswerSeed("THEME_LOCAL", "로컬 동네", 1),
                            new AnswerSeed("THEME_FOOD", "맛집", 2),
                            new AnswerSeed("THEME_HISTORY_CULTURE", "역사·문화", 3),
                            new AnswerSeed("THEME_NATURE", "바다·자연", 4),
                            new AnswerSeed("THEME_NIGHT_VIEW", "야경", 5),
                            new AnswerSeed("THEME_EVENT", "축제·행사", 6)
                    )
            ));
            seedQuestion(jdbcTemplate, new QuestionSeed(
                    "MOBILITY",
                    "이동 부담은 어느 정도까지 괜찮나요?",
                    "SINGLE_CHOICE",
                    true,
                    4,
                    List.of(
                            new AnswerSeed("MOBILITY_AVOID_HILLS_STAIRS", "언덕·계단 피하기", 1),
                            new AnswerSeed("MOBILITY_LOW_WALK", "도보 적게", 2),
                            new AnswerSeed("MOBILITY_NORMAL", "보통", 3),
                            new AnswerSeed("MOBILITY_OK_HILLS", "언덕도 괜찮음", 4)
                    )
            ));
            seedQuestion(jdbcTemplate, new QuestionSeed(
                    "TRANSIT",
                    "대중교통 이동은 어떤 방식을 선호하나요?",
                    "SINGLE_CHOICE",
                    true,
                    5,
                    List.of(
                            new AnswerSeed("TRANSIT_SIMPLE", "환승 적게", 1),
                            new AnswerSeed("TRANSIT_FAST", "빠른 이동", 2),
                            new AnswerSeed("TRANSIT_TRANSFER_OK", "환승 괜찮음", 3)
                    )
            ));
        };
    }

    private void seedQuestion(JdbcTemplate jdbcTemplate, QuestionSeed question) {
        if (exists(jdbcTemplate, "questions", question.id())) {
            jdbcTemplate.update("""
                    update questions
                    set text = ?, type = ?, required = ?, display_order = ?, active = true
                    where id = ?
                    """,
                    question.text(),
                    question.type(),
                    question.required(),
                    question.displayOrder(),
                    question.id()
            );
        } else {
            jdbcTemplate.update("""
                    insert into questions(id, text, type, required, display_order, active)
                    values (?, ?, ?, ?, ?, true)
                    """,
                    question.id(),
                    question.text(),
                    question.type(),
                    question.required(),
                    question.displayOrder()
            );
        }

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
            int displayOrder,
            List<AnswerSeed> answers
    ) {
    }

    private record AnswerSeed(String id, String label, int displayOrder) {
    }
}
