package com.server.post.web;

import com.server.post.domain.FeedScope;
import java.util.Locale;
import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;

/**
 * {@code ?feed=following} 처럼 소문자로 보낸 값을 받아들인다.
 *
 * <p>기본 변환기는 열거형 이름과 대소문자까지 같아야 해서 {@code FOLLOWING} 만 통과한다.
 * 문자열로 받던 시절부터 클라이언트가 소문자로 보내 왔으므로 그 계약을 유지한다.
 * 모르는 값은 여기서 예외가 되어 {@code 400} 으로 나간다.
 */
@Component
public class FeedScopeConverter implements Converter<String, FeedScope> {

    @Override
    public FeedScope convert(String source) {
        // 빈 문자열(?feed=)은 보내지 않은 것과 같게 본다. 값을 지운 채로 요청이 나가는
        // 경우가 있어 이것까지 400 으로 막으면 화면이 비어 버린다.
        if (source.isBlank()) {
            return null;
        }
        return FeedScope.valueOf(source.trim().toUpperCase(Locale.ROOT));
    }
}
