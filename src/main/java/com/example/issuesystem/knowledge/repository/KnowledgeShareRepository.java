package com.example.issuesystem.knowledge.repository;

import com.example.issuesystem.knowledge.domain.KnowledgeShare;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;

/** 지식공유 Repository */
public interface KnowledgeShareRepository extends JpaRepository<KnowledgeShare, Long> {

    /**
     * 지식공유 검색
     *
     * 검색 정책:
     * 1. 전체 검색어가 그대로 포함되면 후보에 포함한다.
     * 2. 공백만 다른 전체 검색어도 후보에 포함한다.
     * 3. 여러 token 검색에서는 핵심 token이 맞는 경우에만 후보에 포함한다.
     *    예: "클라우드 설치" 검색 시 "설치"만 포함된 글은 제외한다.
     * 4. "설치", "오류", "패치" 같은 약한 token은 후보 포함용이 아니라 점수 보조용으로만 사용한다.
     * 5. 검색어가 단일 token이면 기존처럼 해당 token 포함 결과를 보여준다.
     * 6. pg_trgm similarity는 보조 후보/보조 점수로 사용한다.
     *
     * 구현상 주의:
     * - knowledge_share_infra를 join하면 인프라 개수만큼 row가 중복될 수 있다.
     * - 중복 제거를 위해 distinct를 쓰면 PostgreSQL에서 ORDER BY 계산식 오류가 날 수 있다.
     * - 그래서 infra 조건은 exists로 처리한다.
     * - nativeQuery이므로 infraType은 Enum 객체가 아니라 문자열 name() 값을 받는다.
     */
    @Query(
            value = """
                select ks.*
                from knowledge_share ks
                where
                    (
                        :keyword is null
                        or btrim(:keyword) = ''

                        -- 1) 전체 검색어가 제목/내용/고객사/담당자/첨부파일명에 그대로 포함되는 경우
                        or lower(concat_ws(' ',
                            ks.title,
                            ks.content,
                            ks.customer_name,
                            ks.author_name,
                            ks.attachment_name
                        )) like concat('%', lower(btrim(:keyword)), '%')

                        -- 2) 공백만 다른 경우 보완
                        -- 예: DB 값 = "에이전트설치", 검색어 = "에이전트 설치"
                        or replace(lower(concat_ws(' ',
                            ks.title,
                            ks.content,
                            ks.customer_name,
                            ks.author_name,
                            ks.attachment_name
                        )), ' ', '') like concat('%', replace(lower(btrim(:keyword)), ' ', ''), '%')

                        -- 3) 단일 token 검색은 그대로 허용한다.
                        -- 예: "설치"만 검색하면 설치가 포함된 결과를 보여준다.
                        or (
                            (
                                select count(*)
                                from regexp_split_to_table(lower(btrim(:keyword)), '\\s+') as token(word)
                                where char_length(token.word) >= 2
                            ) = 1
                            and exists (
                                select 1
                                from regexp_split_to_table(lower(btrim(:keyword)), '\\s+') as token(word)
                                where char_length(token.word) >= 2
                                  and lower(concat_ws(' ',
                                        ks.title,
                                        ks.content,
                                        ks.customer_name,
                                        ks.author_name,
                                        ks.attachment_name
                                  )) like concat('%', token.word, '%')
                            )
                        )

                        -- 4) 여러 token 검색에서는 핵심 token이 맞아야 후보에 포함한다.
                        -- 약한 token 예: 설치, 패치, 오류, 장애, 문제, 방법 등
                        -- 예: "클라우드 설치" 검색 시 "클라우드"가 핵심 token이다.
                        or exists (
                            select 1
                            from regexp_split_to_table(lower(btrim(:keyword)), '\\s+') as token(word)
                            where char_length(token.word) >= 2
                              and token.word not in (
                                  '설치', '패치', '신규', '오류', '장애', '문제', '방법', '처리', '조치',
                                  '수정', '변경', '업데이트', '등록', '삭제', '조회', '연동', '적용',
                                  '실패', '성공', '확인', '발생', '개선', '추가', '제거',
                                  'install', 'installation', 'patch', 'update', 'error', 'issue', 'problem',
                                  'failure', 'fail', 'fix', 'new', 'add', 'delete', 'remove', 'check'
                              )
                              and lower(concat_ws(' ',
                                    ks.title,
                                    ks.content,
                                    ks.customer_name,
                                    ks.author_name,
                                    ks.attachment_name
                              )) like concat('%', token.word, '%')
                        )

                        -- 5) 검색어 안에 핵심 token이 아예 없으면 기존 token 검색으로 후퇴한다.
                        -- 예: "신규 설치"처럼 둘 다 약한 token이면 완전 제한하지 않는다.
                        or (
                            not exists (
                                select 1
                                from regexp_split_to_table(lower(btrim(:keyword)), '\\s+') as token(word)
                                where char_length(token.word) >= 2
                                  and token.word not in (
                                      '설치', '패치', '신규', '오류', '장애', '문제', '방법', '처리', '조치',
                                      '수정', '변경', '업데이트', '등록', '삭제', '조회', '연동', '적용',
                                      '실패', '성공', '확인', '발생', '개선', '추가', '제거',
                                      'install', 'installation', 'patch', 'update', 'error', 'issue', 'problem',
                                      'failure', 'fail', 'fix', 'new', 'add', 'delete', 'remove', 'check'
                                  )
                            )
                            and exists (
                                select 1
                                from regexp_split_to_table(lower(btrim(:keyword)), '\\s+') as token(word)
                                where char_length(token.word) >= 2
                                  and lower(concat_ws(' ',
                                        ks.title,
                                        ks.content,
                                        ks.customer_name,
                                        ks.author_name,
                                        ks.attachment_name
                                  )) like concat('%', token.word, '%')
                            )
                        )

                        -- 6-1) pg_trgm 고유사도 후보.
                        -- 오타/붙여쓰기 보완용이라 기준을 높게 둔다.
                        or greatest(
                            similarity(lower(coalesce(ks.title, '')), lower(btrim(:keyword))),
                            similarity(left(lower(coalesce(ks.content, '')), 1000), lower(btrim(:keyword)))
                        ) >= 0.35

                        -- 6-2) 낮은 유사도 후보는 핵심 token이 맞을 때만 허용한다.
                        -- 이렇게 해야 "클라우드 설치" 검색 시 "리눅스 설치" 같은 약한 token 결과가 줄어든다.
                        or (
                            greatest(
                                similarity(lower(coalesce(ks.title, '')), lower(btrim(:keyword))),
                                similarity(left(lower(coalesce(ks.content, '')), 1000), lower(btrim(:keyword)))
                            ) >= 0.18
                            and exists (
                                select 1
                                from regexp_split_to_table(lower(btrim(:keyword)), '\\s+') as token(word)
                                where char_length(token.word) >= 2
                                  and token.word not in (
                                      '설치', '패치', '신규', '오류', '장애', '문제', '방법', '처리', '조치',
                                      '수정', '변경', '업데이트', '등록', '삭제', '조회', '연동', '적용',
                                      '실패', '성공', '확인', '발생', '개선', '추가', '제거',
                                      'install', 'installation', 'patch', 'update', 'error', 'issue', 'problem',
                                      'failure', 'fail', 'fix', 'new', 'add', 'delete', 'remove', 'check'
                                  )
                                  and lower(concat_ws(' ',
                                        ks.title,
                                        ks.content,
                                        ks.customer_name,
                                        ks.author_name,
                                        ks.attachment_name
                                  )) like concat('%', token.word, '%')
                            )
                        )
                    )
                    and (
                        :customerName is null
                        or :customerName = ''
                        or lower(coalesce(ks.customer_name, '')) like lower(concat('%', :customerName, '%'))
                    )
                    and (
                        :infraType is null
                        or exists (
                            select 1
                            from knowledge_share_infra ksi
                            where ksi.knowledge_share_id = ks.id
                              and ksi.infra_type = cast(:infraType as varchar)
                        )
                    )
                    and (
                        :infraTypesCsv is null
                        or exists (
                            select 1
                            from knowledge_share_infra ksi
                            where ksi.knowledge_share_id = ks.id
                              and ksi.infra_type = any(string_to_array(:infraTypesCsv, :filterDelimiter))
                        )
                    )
                    and ks.created_at >= :startDate
                    and ks.created_at < :endDate
                order by
                    case
                        when :keyword is null or btrim(:keyword) = '' then 0
                        else
                            -- 제목 전체 검색어 일치: 최상위권
                            case
                                when lower(coalesce(ks.title, '')) like concat('%', lower(btrim(:keyword)), '%')
                                then 120
                                else 0
                            end

                            -- 제목 공백 제거 일치
                            + case
                                when replace(lower(coalesce(ks.title, '')), ' ', '')
                                     like concat('%', replace(lower(btrim(:keyword)), ' ', ''), '%')
                                then 100
                                else 0
                            end

                            -- 내용 전체 검색어 일치
                            + case
                                when lower(coalesce(ks.content, '')) like concat('%', lower(btrim(:keyword)), '%')
                                then 70
                                else 0
                            end

                            -- 제목 token 일치
                            + coalesce((
                                select count(*)::int * 30
                                from regexp_split_to_table(lower(btrim(:keyword)), '\\s+') as token(word)
                                where char_length(token.word) >= 2
                                  and lower(coalesce(ks.title, '')) like concat('%', token.word, '%')
                            ), 0)

                            -- 내용 token 일치
                            + coalesce((
                                select count(*)::int * 15
                                from regexp_split_to_table(lower(btrim(:keyword)), '\\s+') as token(word)
                                where char_length(token.word) >= 2
                                  and lower(coalesce(ks.content, '')) like concat('%', token.word, '%')
                            ), 0)

                            -- 고객사/담당자/첨부파일명 보조 점수
                            + coalesce((
                                select count(*)::int * 6
                                from regexp_split_to_table(lower(btrim(:keyword)), '\\s+') as token(word)
                                where char_length(token.word) >= 2
                                  and lower(concat_ws(' ', ks.customer_name, ks.author_name, ks.attachment_name))
                                      like concat('%', token.word, '%')
                            ), 0)

                            -- pg_trgm 유사도 보조 점수
                            + floor(greatest(
                                similarity(lower(coalesce(ks.title, '')), lower(btrim(:keyword))) * 20,
                                similarity(left(lower(coalesce(ks.content, '')), 1000), lower(btrim(:keyword))) * 8
                            ))::int
                    end desc,
                    ks.id desc
            """,
            countQuery = """
                select count(*)
                from knowledge_share ks
                where
                    (
                        :keyword is null
                        or btrim(:keyword) = ''
                        or lower(concat_ws(' ',
                            ks.title,
                            ks.content,
                            ks.customer_name,
                            ks.author_name,
                            ks.attachment_name
                        )) like concat('%', lower(btrim(:keyword)), '%')
                        or replace(lower(concat_ws(' ',
                            ks.title,
                            ks.content,
                            ks.customer_name,
                            ks.author_name,
                            ks.attachment_name
                        )), ' ', '') like concat('%', replace(lower(btrim(:keyword)), ' ', ''), '%')
                        or (
                            (
                                select count(*)
                                from regexp_split_to_table(lower(btrim(:keyword)), '\\s+') as token(word)
                                where char_length(token.word) >= 2
                            ) = 1
                            and exists (
                                select 1
                                from regexp_split_to_table(lower(btrim(:keyword)), '\\s+') as token(word)
                                where char_length(token.word) >= 2
                                  and lower(concat_ws(' ',
                                        ks.title,
                                        ks.content,
                                        ks.customer_name,
                                        ks.author_name,
                                        ks.attachment_name
                                  )) like concat('%', token.word, '%')
                            )
                        )
                        or exists (
                            select 1
                            from regexp_split_to_table(lower(btrim(:keyword)), '\\s+') as token(word)
                            where char_length(token.word) >= 2
                              and token.word not in (
                                  '설치', '패치', '신규', '오류', '장애', '문제', '방법', '처리', '조치',
                                  '수정', '변경', '업데이트', '등록', '삭제', '조회', '연동', '적용',
                                  '실패', '성공', '확인', '발생', '개선', '추가', '제거',
                                  'install', 'installation', 'patch', 'update', 'error', 'issue', 'problem',
                                  'failure', 'fail', 'fix', 'new', 'add', 'delete', 'remove', 'check'
                              )
                              and lower(concat_ws(' ',
                                    ks.title,
                                    ks.content,
                                    ks.customer_name,
                                    ks.author_name,
                                    ks.attachment_name
                              )) like concat('%', token.word, '%')
                        )
                        or (
                            not exists (
                                select 1
                                from regexp_split_to_table(lower(btrim(:keyword)), '\\s+') as token(word)
                                where char_length(token.word) >= 2
                                  and token.word not in (
                                      '설치', '패치', '신규', '오류', '장애', '문제', '방법', '처리', '조치',
                                      '수정', '변경', '업데이트', '등록', '삭제', '조회', '연동', '적용',
                                      '실패', '성공', '확인', '발생', '개선', '추가', '제거',
                                      'install', 'installation', 'patch', 'update', 'error', 'issue', 'problem',
                                      'failure', 'fail', 'fix', 'new', 'add', 'delete', 'remove', 'check'
                                  )
                            )
                            and exists (
                                select 1
                                from regexp_split_to_table(lower(btrim(:keyword)), '\\s+') as token(word)
                                where char_length(token.word) >= 2
                                  and lower(concat_ws(' ',
                                        ks.title,
                                        ks.content,
                                        ks.customer_name,
                                        ks.author_name,
                                        ks.attachment_name
                                  )) like concat('%', token.word, '%')
                            )
                        )
                        or greatest(
                            similarity(lower(coalesce(ks.title, '')), lower(btrim(:keyword))),
                            similarity(left(lower(coalesce(ks.content, '')), 1000), lower(btrim(:keyword)))
                        ) >= 0.35
                        or (
                            greatest(
                                similarity(lower(coalesce(ks.title, '')), lower(btrim(:keyword))),
                                similarity(left(lower(coalesce(ks.content, '')), 1000), lower(btrim(:keyword)))
                            ) >= 0.18
                            and exists (
                                select 1
                                from regexp_split_to_table(lower(btrim(:keyword)), '\\s+') as token(word)
                                where char_length(token.word) >= 2
                                  and token.word not in (
                                      '설치', '패치', '신규', '오류', '장애', '문제', '방법', '처리', '조치',
                                      '수정', '변경', '업데이트', '등록', '삭제', '조회', '연동', '적용',
                                      '실패', '성공', '확인', '발생', '개선', '추가', '제거',
                                      'install', 'installation', 'patch', 'update', 'error', 'issue', 'problem',
                                      'failure', 'fail', 'fix', 'new', 'add', 'delete', 'remove', 'check'
                                  )
                                  and lower(concat_ws(' ',
                                        ks.title,
                                        ks.content,
                                        ks.customer_name,
                                        ks.author_name,
                                        ks.attachment_name
                                  )) like concat('%', token.word, '%')
                            )
                        )
                    )
                    and (
                        :customerName is null
                        or :customerName = ''
                        or lower(coalesce(ks.customer_name, '')) like lower(concat('%', :customerName, '%'))
                    )
                    and (
                        :infraType is null
                        or exists (
                            select 1
                            from knowledge_share_infra ksi
                            where ksi.knowledge_share_id = ks.id
                              and ksi.infra_type = cast(:infraType as varchar)
                        )
                    )
                    and (
                        :infraTypesCsv is null
                        or exists (
                            select 1
                            from knowledge_share_infra ksi
                            where ksi.knowledge_share_id = ks.id
                              and ksi.infra_type = any(string_to_array(:infraTypesCsv, :filterDelimiter))
                        )
                    )
                    and ks.created_at >= :startDate
                    and ks.created_at < :endDate
            """,
            nativeQuery = true
    )
    Page<KnowledgeShare> search(
            @Param("keyword") String keyword,
            @Param("customerName") String customerName,
            @Param("infraType") String infraType,
            @Param("infraTypesCsv") String infraTypesCsv,
            @Param("filterDelimiter") String filterDelimiter,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate,
            Pageable pageable
    );
}
