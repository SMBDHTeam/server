package com.server.common.support;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;

/**
 * 목록 요청의 페이지와 개수를 다듬는 규칙.
 *
 * <p>같은 규칙이 여섯 서비스에 흩어져 있어 한 곳으로 모았다. 규칙 자체는 그때와 같다.
 */
@DisplayName("목록 페이징")
class PagingTest {

    @Test
    @DisplayName("보내지 않으면 첫 페이지 20개다")
    void usesDefaults() {
        PageRequest request = Paging.of(null, null);

        assertThat(request.getPageNumber()).isZero();
        assertThat(request.getPageSize()).isEqualTo(20);
    }

    @Test
    @DisplayName("보낸 값을 그대로 쓴다")
    void keepsGivenValues() {
        PageRequest request = Paging.of(2, 30);

        assertThat(request.getPageNumber()).isEqualTo(2);
        assertThat(request.getPageSize()).isEqualTo(30);
    }

    @Test
    @DisplayName("상한을 넘겨 요청해도 50개까지만 준다")
    void capsSize() {
        // 막지 않으면 size=100000 한 번으로 전체를 긁어갈 수 있다.
        assertThat(Paging.size(1000)).isEqualTo(50);
        assertThat(Paging.of(0, 1000).getPageSize()).isEqualTo(50);
    }

    @Test
    @DisplayName("음수나 0 은 기본값으로 되돌린다")
    void fallsBackOnInvalidValues() {
        // 화면을 비우는 것보다 20개를 보여주는 편이 낫다. 목록은 화면이 열릴 때마다 부른다.
        assertThat(Paging.size(0)).isEqualTo(20);
        assertThat(Paging.size(-5)).isEqualTo(20);
        assertThat(Paging.of(-1, null).getPageNumber()).isZero();
    }
}
