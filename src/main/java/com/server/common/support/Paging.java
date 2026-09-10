package com.server.common.support;

import org.springframework.data.domain.PageRequest;

/**
 * 목록 요청의 페이지 번호와 개수를 다듬는다.
 *
 * <p>같은 규칙이 여섯 서비스에 흩어져 있었다. 커뮤니티의 모든 목록은 상한이 같으므로
 * 한 곳에 둔다. 상한을 바꿀 때 빠뜨리는 곳이 생기지 않게 하기 위함이다.
 *
 * <p>잘못된 값은 막지 않고 기본값으로 되돌린다. 목록 조회는 화면이 처음 열릴 때마다
 * 부르는 요청이라, 개수를 잘못 보냈다고 화면을 비우는 것보다 20개를 보여주는 편이 낫다.
 * 개수 검증이 필요한 곳은 컨트롤러에서 {@code @Min}, {@code @Max} 로 따로 막는다.
 */
public final class Paging {

    /** 클라이언트가 개수를 지정하지 않았을 때 쓴다. */
    public static final int DEFAULT_SIZE = 20;

    /** 한 번에 가져갈 수 있는 최대 개수. 이보다 크게 요청해도 여기까지만 준다. */
    public static final int MAX_SIZE = 50;

    private Paging() {
    }

    public static PageRequest of(Integer page, Integer size) {
        return PageRequest.of(page(page), size(size));
    }

    /** 커서 방식이라 페이지 번호가 없는 목록에서 개수만 다듬을 때 쓴다. */
    public static int size(Integer size) {
        if (size == null || size <= 0) {
            return DEFAULT_SIZE;
        }
        return Math.min(size, MAX_SIZE);
    }

    private static int page(Integer page) {
        return page == null || page < 0 ? 0 : page;
    }
}
