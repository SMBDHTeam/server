-- 목록 화면이 쓰는 축소본 주소. 업로드할 때 원본과 함께 만든다.
-- 이 열이 생기기 전에 올라온 사진과 축소본을 만들지 못한 형식(webp·영상)은 비어 있고,
-- 그때 화면은 원본을 쓴다.
alter table post_media
    add column thumbnail_url text;
