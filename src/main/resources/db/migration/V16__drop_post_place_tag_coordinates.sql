-- 사진 EXIF 로 촬영 지점을 채우려던 컬럼이다. 장소는 사용자가 일정이나 지도 검색에서
-- 직접 고르는 것으로 정해져 좌표를 받을 경로가 없어졌다. 장소의 대표 좌표는 places 에
-- 있으므로 이 컬럼이 없어도 지도 표시에는 지장이 없다.
ALTER TABLE post_place_tags DROP COLUMN IF EXISTS latitude;
ALTER TABLE post_place_tags DROP COLUMN IF EXISTS longitude;

-- 장소를 게시물이 아니라 사진에 붙인다. 사진마다 다른 곳을 다녀왔을 수 있고, 게시물
-- 단위로 묶으면 어느 사진이 어느 장소인지 알 수 없다.
--
-- nullable 이다. 장소를 붙이지 않은 사진이 있을 수 있고, 이 컬럼이 생기기 전에 저장된
-- 태그는 어느 사진의 것인지 되짚을 근거가 없다.
ALTER TABLE post_place_tags ADD COLUMN IF NOT EXISTS media_id bigint REFERENCES post_media(id);

CREATE INDEX IF NOT EXISTS idx_post_place_tags_media_id ON post_place_tags (media_id);
