package com.server.question.repository;

import com.server.question.entity.Question;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface QuestionRepository extends JpaRepository<Question, String> {

    @Query("""
            select distinct question
            from Question question
            left join fetch question.answers
            where question.active = true
            order by question.displayOrder asc
            """)
    List<Question> findByActiveTrueOrderByDisplayOrderAsc();
}
