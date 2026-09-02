-- 해시태그를 카테고리로 바꾸면서 남은 옛 시드를 지운다.
--
-- 예전에는 사용자가 태그를 자유롭게 달았고, 부산·해운대·부산맛집 같은 이름 33개를 미리
-- 넣어 뒀다. 이제는 우리가 정한 여덟 개 중에서만 고르는데, 시드는 없는 것만 채워 넣을 뿐
-- 남는 것을 지우지 않아 옛 이름이 그대로 남았다. 그대로 두면 카테고리 목록 API 가 41개를
-- 돌려주고 화면의 탭도 그만큼 늘어난다.
--
-- 연결부터 지운다. 외래키가 걸려 있어 순서를 지키지 않으면 실패한다. 지금 이 이름이 붙은
-- 게시물은 없지만, 있더라도 카테고리에서 빠지는 것이 맞다. 화면에서 고를 수 없는 값이라
-- 남겨 두면 그 게시물만 사라진 탭에 묶인다.
DELETE FROM post_hashtags
WHERE hashtag_id IN (
    SELECT id FROM hashtags
    WHERE name NOT IN ('맛집', '카페', '힐링', '액티비티', '쇼핑', '야경', '역사', '자연')
);

DELETE FROM hashtags
WHERE name NOT IN ('맛집', '카페', '힐링', '액티비티', '쇼핑', '야경', '역사', '자연');

-- 위에서 연결을 지웠으므로 사용 수를 다시 센다. 그대로 두면 자동완성 정렬이 실제보다
-- 부풀어 있는 값으로 매겨진다.
UPDATE hashtags
SET post_count = (
    SELECT COUNT(*) FROM post_hashtags WHERE post_hashtags.hashtag_id = hashtags.id
);
