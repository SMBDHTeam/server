package com.server.question.entity;

import com.server.answer.entity.Answer;
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

    private String text;
    private String type;
    private boolean required;
    private int displayOrder;
    private boolean active;

    @OneToMany(mappedBy = "question")
    private List<Answer> answers = new ArrayList<>(); // 비어 있을 때 null 대신 빈 리스트가 되게해서 NPE(NullPointerException) 예방한다.






}
