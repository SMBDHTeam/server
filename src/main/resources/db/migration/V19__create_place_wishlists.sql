-- 장소 위시리스트.
--
-- 커뮤니티에서 본 장소나 검색으로 찾은 장소를 담아 두었다가, 일정을 만들 때 필수 방문지로
-- 넘긴다. 일정 생성은 이미 mustVisitPlaceIds 를 받고 있어 담아 둔 placeId 를 그대로 쓴다.
--
-- bookmarks 와 구조가 같지만 대상이 다르다. 저장은 게시물, 위시리스트는 장소다. 한 테이블에
-- 합치면 대상 종류 컬럼이 생기고 외래키를 걸 수 없어, 지워진 장소를 가리키는 행이 남는다.
--
-- 대리키 없이 (user_id, place_id) 가 기본키다. 같은 사람이 같은 장소를 두 번 담는 것을 DB 가
-- 막는다. 애플리케이션에서 확인하는 방식은 동시 요청에서 뚫린다.
--
-- 키 순서는 "내 위시리스트" 조회가 주 용도라 user_id 가 앞이다. 이 순서 덕분에 목록 조회에
-- 별도 인덱스가 필요 없다.
CREATE TABLE IF NOT EXISTS place_wishlists (
    user_id bigint NOT NULL REFERENCES users(id),
    place_id bigint NOT NULL REFERENCES places(id),
    created_at timestamp NOT NULL,
    PRIMARY KEY (user_id, place_id)
);
