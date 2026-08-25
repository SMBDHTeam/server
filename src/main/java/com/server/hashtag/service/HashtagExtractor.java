package com.server.hashtag.service;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * 본문에서 해시태그를 뽑아낸다.
 *
 * <p>{@code #} 뒤의 한글·영문·숫자·밑줄만 태그로 인정하며 공백이나 그 밖의 문자에서 끊는다.
 * 여러 단어를 담고 싶으면 붙여 써야 한다({@code #광안리맛집}). 띄어쓰기를 허용하면
 * 어디까지가 태그인지 정할 수 없기 때문이다.
 */
@Component
public class HashtagExtractor {

    /** 게시물 하나에 담을 수 있는 태그 수. */
    static final int MAX_HASHTAGS_PER_POST = 20;
    /** 태그 하나의 최대 길이. */
    static final int MAX_HASHTAG_LENGTH = 50;

    private static final Pattern HASHTAG = Pattern.compile("#([0-9A-Za-z_가-힣ㄱ-ㅎㅏ-ㅣ]+)");

    /**
     * @return 등장 순서를 유지한 소문자 태그 목록. 중복은 제거하고 최대 개수까지만 남긴다.
     */
    public List<String> extract(String content) {
        if (content == null || content.isBlank()) {
            return List.of();
        }

        LinkedHashSet<String> names = new LinkedHashSet<>();
        Matcher matcher = HASHTAG.matcher(content);
        while (matcher.find() && names.size() < MAX_HASHTAGS_PER_POST) {
            String name = matcher.group(1).toLowerCase();
            if (name.length() <= MAX_HASHTAG_LENGTH) {
                names.add(name);
            }
        }
        return List.copyOf(names);
    }
}
