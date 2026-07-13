package com.server.question.entity;

import com.server.answer.entity.Answer;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@NoArgsConstructor
@Getter
@Entity
@Table(name = "questions")
public class Question {
    @Id
    private String id;

    @Column(nullable = false)
    private String text;

    @Column(nullable = false)
    private String type;

    @Column(nullable = false)
    private boolean required;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    @Column(nullable = false)
    private boolean active;

    @OneToMany(mappedBy = "question")
    private List<Answer> answers = new ArrayList<>();
}
