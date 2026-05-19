package com.example.issuesystem.issue.repository;

import com.example.issuesystem.issue.domain.IssueCase;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface IssueCaseRepository extends JpaRepository<IssueCase, Long> {

    /**
     * 검색 조건에 맞는 이슈 ID만 먼저 페이징 조회한다.
     *
     * 검색 정책:
     * 1. 전체 검색어가 그대로 포함되면 후보에 포함한다.
     * 2. 공백만 다른 전체 검색어도 후보에 포함한다.
     *    예: DB = "에이전트설치", 검색어 = "에이전트 설치"
     * 3. 검색어가 여러 token이면 "핵심 token"이 맞는 경우에만 후보에 포함한다.
     *    예: "클라우드 설치" 검색 시 "설치"만 맞는 "리눅스 설치"는 제외한다.
     * 4. "설치", "오류", "패치" 같은 약한 token은 후보 포함용이 아니라 점수 보조용으로만 사용한다.
     * 5. 검색어가 단일 token이면 기존처럼 해당 token 포함 결과를 보여준다.
     *    예: "설치"만 검색하면 설치 관련 결과가 나온다.
     * 6. pg_trgm similarity는 보조 후보/보조 점수로 사용한다.
     */
    @Query(value = """
        select i.id
        from issue_case i
        where
            (
                :keyword is null
                or btrim(:keyword) = ''

                -- 1) 전체 검색어가 그대로 포함되는 경우
                or lower(concat_ws(' ',
                    i.title,
                    i.system_name,
                    i.customer_name,
                    i.symptom_summary,
                    i.symptom_detail,
                    i.cause_detail,
                    i.action_detail,
                    i.tags,
                    i.category,
                    i.deployment_version,
                    i.version_info
                )) like concat('%', lower(btrim(:keyword)), '%')

                -- 2) 공백만 다른 경우 보완
                -- 예: DB = "에이전트설치", 검색어 = "에이전트 설치"
                or replace(lower(concat_ws(' ',
                    i.title,
                    i.system_name,
                    i.customer_name,
                    i.symptom_summary,
                    i.symptom_detail,
                    i.cause_detail,
                    i.action_detail,
                    i.tags,
                    i.category,
                    i.deployment_version,
                    i.version_info
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
                                i.title,
                                i.system_name,
                                i.customer_name,
                                i.symptom_summary,
                                i.symptom_detail,
                                i.cause_detail,
                                i.action_detail,
                                i.tags,
                                i.category,
                                i.deployment_version,
                                i.version_info
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
                            i.title,
                            i.system_name,
                            i.customer_name,
                            i.symptom_summary,
                            i.symptom_detail,
                            i.cause_detail,
                            i.action_detail,
                            i.tags,
                            i.category,
                            i.deployment_version,
                            i.version_info
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
                                i.title,
                                i.system_name,
                                i.customer_name,
                                i.symptom_summary,
                                i.symptom_detail,
                                i.cause_detail,
                                i.action_detail,
                                i.tags,
                                i.category,
                                i.deployment_version,
                                i.version_info
                          )) like concat('%', token.word, '%')
                    )
                )

                -- 6-1) pg_trgm 고유사도 후보.
                -- 오타/붙여쓰기 보완용이라 기준을 높게 둔다.
                or greatest(
                    similarity(lower(coalesce(i.title, '')), lower(btrim(:keyword))),
                    similarity(lower(coalesce(i.symptom_summary, '')), lower(btrim(:keyword))),
                    similarity(left(lower(coalesce(i.symptom_detail, '')), 1000), lower(btrim(:keyword))),
                    similarity(lower(coalesce(i.tags, '')), lower(btrim(:keyword)))
                ) >= 0.35

                -- 6-2) 낮은 유사도 후보는 핵심 token이 맞을 때만 허용한다.
                -- 이렇게 해야 "클라우드 설치" 검색 시 "리눅스 설치" 같은 약한 token 결과가 줄어든다.
                or (
                    greatest(
                        similarity(lower(coalesce(i.title, '')), lower(btrim(:keyword))),
                        similarity(lower(coalesce(i.symptom_summary, '')), lower(btrim(:keyword))),
                        similarity(left(lower(coalesce(i.symptom_detail, '')), 1000), lower(btrim(:keyword))),
                        similarity(lower(coalesce(i.tags, '')), lower(btrim(:keyword)))
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
                                i.title,
                                i.system_name,
                                i.customer_name,
                                i.symptom_summary,
                                i.symptom_detail,
                                i.cause_detail,
                                i.action_detail,
                                i.tags,
                                i.category,
                                i.deployment_version,
                                i.version_info
                          )) like concat('%', token.word, '%')
                    )
                )
            )
            and (:infraType is null or i.infra_type = cast(:infraType as varchar))
            and (:status is null or i.status = cast(:status as varchar))
            and (:customerName is null or :customerName = '' or
                coalesce(i.customer_name, '') ilike concat('%', :customerName, '%'))
            and (:category is null or :category = '' or
                coalesce(i.category, '') ilike concat('%', :category, '%'))
            and (:deploymentVersion is null or :deploymentVersion = '' or
                coalesce(i.deployment_version, '') ilike concat('%', :deploymentVersion, '%'))
            and i.created_at >= :startDate
            and i.created_at < :endDate
        order by
            case
                when :keyword is null or btrim(:keyword) = '' then 0
                else
                    -- 제목에서 전체 검색어가 그대로 맞으면 최상위권 점수
                    case when lower(coalesce(i.title, '')) like concat('%', lower(btrim(:keyword)), '%') then 120 else 0 end

                    -- 제목에서 공백만 다른 전체 검색어가 맞으면 높은 점수
                    + case when replace(lower(coalesce(i.title, '')), ' ', '') like concat('%', replace(lower(btrim(:keyword)), ' ', ''), '%') then 100 else 0 end

                    -- 증상 요약/상세에서 전체 검색어가 맞으면 점수 부여
                    + case when lower(coalesce(i.symptom_summary, '')) like concat('%', lower(btrim(:keyword)), '%') then 80 else 0 end
                    + case when lower(coalesce(i.symptom_detail, '')) like concat('%', lower(btrim(:keyword)), '%') then 50 else 0 end
                    + case when lower(coalesce(i.action_detail, '')) like concat('%', lower(btrim(:keyword)), '%') then 35 else 0 end
                    + case when lower(coalesce(i.cause_detail, '')) like concat('%', lower(btrim(:keyword)), '%') then 30 else 0 end

                    -- 제목 token 일치: token이 많이 맞을수록 점수가 올라간다.
                    + coalesce((
                        select count(*)::int * 30
                        from regexp_split_to_table(lower(btrim(:keyword)), '\\s+') as token(word)
                        where char_length(token.word) >= 2
                          and lower(coalesce(i.title, '')) like concat('%', token.word, '%')
                    ), 0)

                    -- 요약 token 일치
                    + coalesce((
                        select count(*)::int * 20
                        from regexp_split_to_table(lower(btrim(:keyword)), '\\s+') as token(word)
                        where char_length(token.word) >= 2
                          and lower(coalesce(i.symptom_summary, '')) like concat('%', token.word, '%')
                    ), 0)

                    -- 상세/원인/조치 token 일치: 제목보다 낮은 점수
                    + coalesce((
                        select count(*)::int * 12
                        from regexp_split_to_table(lower(btrim(:keyword)), '\\s+') as token(word)
                        where char_length(token.word) >= 2
                          and lower(coalesce(i.symptom_detail, '')) like concat('%', token.word, '%')
                    ), 0)
                    + coalesce((
                        select count(*)::int * 8
                        from regexp_split_to_table(lower(btrim(:keyword)), '\\s+') as token(word)
                        where char_length(token.word) >= 2
                          and lower(coalesce(i.action_detail, '')) like concat('%', token.word, '%')
                    ), 0)
                    + coalesce((
                        select count(*)::int * 8
                        from regexp_split_to_table(lower(btrim(:keyword)), '\\s+') as token(word)
                        where char_length(token.word) >= 2
                          and lower(coalesce(i.cause_detail, '')) like concat('%', token.word, '%')
                    ), 0)

                    -- 태그/시스템명/고객사 보조 점수
                    + coalesce((
                        select count(*)::int * 6
                        from regexp_split_to_table(lower(btrim(:keyword)), '\\s+') as token(word)
                        where char_length(token.word) >= 2
                          and lower(concat_ws(' ', i.tags, i.system_name, i.customer_name, i.category, i.deployment_version, i.version_info))
                              like concat('%', token.word, '%')
                    ), 0)

                    -- pg_trgm 유사도 보조 점수
                    + floor(greatest(
                        similarity(lower(coalesce(i.title, '')), lower(btrim(:keyword))) * 20,
                        similarity(lower(coalesce(i.symptom_summary, '')), lower(btrim(:keyword))) * 12,
                        similarity(left(lower(coalesce(i.symptom_detail, '')), 1000), lower(btrim(:keyword))) * 8
                    ))::int
            end desc,
            i.id desc
        """,
            countQuery = """
        select count(*)
        from issue_case i
        where
            (
                :keyword is null
                or btrim(:keyword) = ''
                or lower(concat_ws(' ',
                    i.title,
                    i.system_name,
                    i.customer_name,
                    i.symptom_summary,
                    i.symptom_detail,
                    i.cause_detail,
                    i.action_detail,
                    i.tags,
                    i.category,
                    i.deployment_version,
                    i.version_info
                )) like concat('%', lower(btrim(:keyword)), '%')
                or replace(lower(concat_ws(' ',
                    i.title,
                    i.system_name,
                    i.customer_name,
                    i.symptom_summary,
                    i.symptom_detail,
                    i.cause_detail,
                    i.action_detail,
                    i.tags,
                    i.category,
                    i.deployment_version,
                    i.version_info
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
                                i.title,
                                i.system_name,
                                i.customer_name,
                                i.symptom_summary,
                                i.symptom_detail,
                                i.cause_detail,
                                i.action_detail,
                                i.tags,
                                i.category,
                                i.deployment_version,
                                i.version_info
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
                            i.title,
                            i.system_name,
                            i.customer_name,
                            i.symptom_summary,
                            i.symptom_detail,
                            i.cause_detail,
                            i.action_detail,
                            i.tags,
                            i.category,
                            i.deployment_version,
                            i.version_info
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
                                i.title,
                                i.system_name,
                                i.customer_name,
                                i.symptom_summary,
                                i.symptom_detail,
                                i.cause_detail,
                                i.action_detail,
                                i.tags,
                                i.category,
                                i.deployment_version,
                                i.version_info
                          )) like concat('%', token.word, '%')
                    )
                )
                or greatest(
                    similarity(lower(coalesce(i.title, '')), lower(btrim(:keyword))),
                    similarity(lower(coalesce(i.symptom_summary, '')), lower(btrim(:keyword))),
                    similarity(left(lower(coalesce(i.symptom_detail, '')), 1000), lower(btrim(:keyword))),
                    similarity(lower(coalesce(i.tags, '')), lower(btrim(:keyword)))
                ) >= 0.35
                or (
                    greatest(
                        similarity(lower(coalesce(i.title, '')), lower(btrim(:keyword))),
                        similarity(lower(coalesce(i.symptom_summary, '')), lower(btrim(:keyword))),
                        similarity(left(lower(coalesce(i.symptom_detail, '')), 1000), lower(btrim(:keyword))),
                        similarity(lower(coalesce(i.tags, '')), lower(btrim(:keyword)))
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
                                i.title,
                                i.system_name,
                                i.customer_name,
                                i.symptom_summary,
                                i.symptom_detail,
                                i.cause_detail,
                                i.action_detail,
                                i.tags,
                                i.category,
                                i.deployment_version,
                                i.version_info
                          )) like concat('%', token.word, '%')
                    )
                )
            )
            and (:infraType is null or i.infra_type = cast(:infraType as varchar))
            and (:status is null or i.status = cast(:status as varchar))
            and (:customerName is null or :customerName = '' or
                coalesce(i.customer_name, '') ilike concat('%', :customerName, '%'))
            and (:category is null or :category = '' or
                coalesce(i.category, '') ilike concat('%', :category, '%'))
            and (:deploymentVersion is null or :deploymentVersion = '' or
                coalesce(i.deployment_version, '') ilike concat('%', :deploymentVersion, '%'))
            and i.created_at >= :startDate
            and i.created_at < :endDate
        """,
            nativeQuery = true)
    Page<Long> searchIds(
            @Param("keyword") String keyword,
            @Param("infraType") String infraType,
            @Param("status") String status,
            @Param("customerName") String customerName,
            @Param("category") String category,
            @Param("deploymentVersion") String deploymentVersion,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate,
            Pageable pageable
    );

    /** ID 목록으로 이슈 엔티티 재조회 */
    List<IssueCase> findByIdIn(List<Long> ids);
}
