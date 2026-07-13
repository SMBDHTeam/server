package com.server.question.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class QuestionSeedInitializerTest {

    @Test
    void seedsMvpTripQuestionsAndAnswersIdempotently() throws Exception {
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource());
        createSchema(jdbcTemplate);
        QuestionSeedInitializer initializer = new QuestionSeedInitializer();

        initializer.seedTripQuestions(jdbcTemplate).run(null);
        initializer.seedTripQuestions(jdbcTemplate).run(null);

        Integer questionCount = jdbcTemplate.queryForObject("select count(*) from questions", Integer.class);
        Integer answerCount = jdbcTemplate.queryForObject("select count(*) from answers", Integer.class);
        assertThat(questionCount).isEqualTo(5);
        assertThat(answerCount).isEqualTo(21);
        assertThat(jdbcTemplate.queryForObject(
                "select text from questions where id = 'COMPANION'",
                String.class
        )).isEqualTo("누구와 여행하나요?");
        assertThat(jdbcTemplate.queryForList(
                "select id from questions where active = true order by display_order",
                String.class
        )).containsExactly("COMPANION", "PACE", "THEME", "MOBILITY", "TRANSIT");
        assertThat(jdbcTemplate.queryForList(
                "select id from answers where question_id = 'MOBILITY' and active = true order by display_order",
                String.class
        )).containsExactly(
                "MOBILITY_AVOID_HILLS_STAIRS",
                "MOBILITY_LOW_WALK",
                "MOBILITY_NORMAL",
                "MOBILITY_OK_HILLS"
        );
    }

    private DriverManagerDataSource dataSource() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("org.h2.Driver");
        dataSource.setUrl("jdbc:h2:mem:question-seed;MODE=PostgreSQL;DB_CLOSE_DELAY=-1");
        dataSource.setUsername("sa");
        return dataSource;
    }

    private void createSchema(JdbcTemplate jdbcTemplate) {
        jdbcTemplate.execute("""
                create table questions (
                    id varchar(255) primary key,
                    text varchar(255) not null,
                    type varchar(255) not null,
                    required boolean not null,
                    display_order integer not null,
                    active boolean not null
                )
                """);
        jdbcTemplate.execute("""
                create table answers (
                    id varchar(255) primary key,
                    question_id varchar(255) not null,
                    label varchar(255) not null,
                    display_order integer not null,
                    active boolean not null,
                    constraint fk_answers_questions foreign key (question_id) references questions(id)
                )
                """);
    }
}
