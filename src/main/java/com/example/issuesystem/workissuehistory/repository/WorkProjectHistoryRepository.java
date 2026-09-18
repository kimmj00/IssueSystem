package com.example.issuesystem.workissuehistory.repository;

import com.example.issuesystem.workissuehistory.domain.WorkProjectHistory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface WorkProjectHistoryRepository extends JpaRepository<WorkProjectHistory, Long> {

    // 특정 업로드 파일에 포함된 프로젝트 현황만 조회합니다.
    List<WorkProjectHistory> findByUploadIdOrderByRowNoAscIdAsc(Long uploadId);

    long countByUploadId(Long uploadId);

    @Query(
            value = """
            select p.*
            from work_project_history p
            where p.created_at >= :startDate
              and p.created_at < :endDate
              and (:customerName is null or :customerName = '' or lower(coalesce(p.client_name, '')) like lower(concat('%', :customerName, '%')))
              and (
                  :keyword is null or btrim(:keyword) = ''
                  or lower(concat_ws(' ',
                      p.client_name,
                      p.scope,
                      p.executors,
                      p.progress_logs,
                      p.remaining_issues,
                      p.sales_rep,
                      p.site_code,
                      p.project_scale
                  )) like concat('%', lower(btrim(:keyword)), '%')
                  or replace(lower(concat_ws(' ',
                      p.client_name,
                      p.scope,
                      p.executors,
                      p.progress_logs,
                      p.remaining_issues,
                      p.sales_rep,
                      p.site_code,
                      p.project_scale
                  )), ' ', '') like concat('%', replace(lower(btrim(:keyword)), ' ', ''), '%')
                  or exists (
                      select 1
                      from regexp_split_to_table(lower(btrim(:keyword)), '\\s+') as token(word)
                      where char_length(token.word) >= 2
                        and lower(concat_ws(' ',
                            p.client_name,
                            p.scope,
                            p.executors,
                            p.progress_logs,
                            p.remaining_issues,
                            p.sales_rep,
                            p.site_code,
                            p.project_scale
                        )) like concat('%', token.word, '%')
                  )
                  or greatest(
                      similarity(lower(coalesce(p.client_name, '')), lower(btrim(:keyword))),
                      similarity(lower(coalesce(p.scope, '')), lower(btrim(:keyword))),
                      similarity(left(lower(coalesce(p.progress_logs, '')), 1000), lower(btrim(:keyword))),
                      similarity(left(lower(coalesce(p.remaining_issues, '')), 1000), lower(btrim(:keyword)))
                  ) >= 0.35
                  or (
                      select count(distinct search_char.ch)::int
                      from regexp_split_to_table(regexp_replace(lower(btrim(:keyword)), '\\s+', '', 'g'), '') as search_char(ch)
                      where char_length(search_char.ch) > 0
                        and lower(concat_ws(' ',
                            p.client_name,
                            p.scope,
                            p.executors,
                            p.progress_logs,
                            p.remaining_issues,
                            p.sales_rep,
                            p.site_code,
                            p.project_scale
                        )) like concat('%', search_char.ch, '%')
                  ) >= case
                      when char_length(regexp_replace(lower(btrim(:keyword)), '\\s+', '', 'g')) >= 5 then 3
                      when char_length(regexp_replace(lower(btrim(:keyword)), '\\s+', '', 'g')) >= 3 then 2
                      else 999
                  end
              )
            order by
              case
                  when :keyword is null or btrim(:keyword) = '' then 0
                  else
                      case when lower(coalesce(p.client_name, '')) like concat('%', lower(btrim(:keyword)), '%') then 80 else 0 end
                      + case when replace(lower(coalesce(p.client_name, '')), ' ', '') like concat('%', replace(lower(btrim(:keyword)), ' ', ''), '%') then 70 else 0 end
                      + case when similarity(lower(coalesce(p.client_name, '')), lower(btrim(:keyword))) >= 0.35 then 65 + floor(similarity(lower(coalesce(p.client_name, '')), lower(btrim(:keyword))) * 10)::int else 0 end
                      + case when lower(coalesce(p.scope, '')) like concat('%', lower(btrim(:keyword)), '%') then 60 else 0 end
                      + case when greatest(
                            similarity(lower(coalesce(p.scope, '')), lower(btrim(:keyword))),
                            similarity(left(lower(coalesce(p.progress_logs, '')), 1000), lower(btrim(:keyword))),
                            similarity(left(lower(coalesce(p.remaining_issues, '')), 1000), lower(btrim(:keyword)))
                        ) >= 0.35 then 35 else 0 end
                      + case when lower(coalesce(p.progress_logs, '')) like concat('%', lower(btrim(:keyword)), '%') then 50 else 0 end
                      + case when lower(coalesce(p.remaining_issues, '')) like concat('%', lower(btrim(:keyword)), '%') then 40 else 0 end
                      + coalesce((
                          select least(count(distinct search_char.ch)::int, 3) * 2
                          from regexp_split_to_table(regexp_replace(lower(btrim(:keyword)), '\\s+', '', 'g'), '') as search_char(ch)
                          where char_length(search_char.ch) > 0
                            and lower(coalesce(p.client_name, '')) like concat('%', search_char.ch, '%')
                      ), 0)
                      + coalesce((
                          select least(count(distinct search_char.ch)::int, 3)
                          from regexp_split_to_table(regexp_replace(lower(btrim(:keyword)), '\\s+', '', 'g'), '') as search_char(ch)
                          where char_length(search_char.ch) > 0
                            and lower(concat_ws(' ', p.scope, p.progress_logs, p.remaining_issues))
                                like concat('%', search_char.ch, '%')
                      ), 0)
                      + coalesce((
                          select least(count(distinct search_char.ch)::int, 3)
                          from regexp_split_to_table(regexp_replace(lower(btrim(:keyword)), '\\s+', '', 'g'), '') as search_char(ch)
                          where char_length(search_char.ch) > 0
                            and lower(concat_ws(' ', p.executors, p.sales_rep, p.site_code, p.project_scale))
                                like concat('%', search_char.ch, '%')
                      ), 0)
              end desc,
              p.created_at asc,
              p.id asc
            """,
            countQuery = """
            select count(*)
            from work_project_history p
            where p.created_at >= :startDate
              and p.created_at < :endDate
              and (:customerName is null or :customerName = '' or lower(coalesce(p.client_name, '')) like lower(concat('%', :customerName, '%')))
              and (
                  :keyword is null or btrim(:keyword) = ''
                  or lower(concat_ws(' ',
                      p.client_name,
                      p.scope,
                      p.executors,
                      p.progress_logs,
                      p.remaining_issues,
                      p.sales_rep,
                      p.site_code,
                      p.project_scale
                  )) like concat('%', lower(btrim(:keyword)), '%')
                  or replace(lower(concat_ws(' ',
                      p.client_name,
                      p.scope,
                      p.executors,
                      p.progress_logs,
                      p.remaining_issues,
                      p.sales_rep,
                      p.site_code,
                      p.project_scale
                  )), ' ', '') like concat('%', replace(lower(btrim(:keyword)), ' ', ''), '%')
                  or exists (
                      select 1
                      from regexp_split_to_table(lower(btrim(:keyword)), '\\s+') as token(word)
                      where char_length(token.word) >= 2
                        and lower(concat_ws(' ',
                            p.client_name,
                            p.scope,
                            p.executors,
                            p.progress_logs,
                            p.remaining_issues,
                            p.sales_rep,
                            p.site_code,
                            p.project_scale
                        )) like concat('%', token.word, '%')
                  )
                  or greatest(
                      similarity(lower(coalesce(p.client_name, '')), lower(btrim(:keyword))),
                      similarity(lower(coalesce(p.scope, '')), lower(btrim(:keyword))),
                      similarity(left(lower(coalesce(p.progress_logs, '')), 1000), lower(btrim(:keyword))),
                      similarity(left(lower(coalesce(p.remaining_issues, '')), 1000), lower(btrim(:keyword)))
                  ) >= 0.35
                  or (
                      select count(distinct search_char.ch)::int
                      from regexp_split_to_table(regexp_replace(lower(btrim(:keyword)), '\\s+', '', 'g'), '') as search_char(ch)
                      where char_length(search_char.ch) > 0
                        and lower(concat_ws(' ',
                            p.client_name,
                            p.scope,
                            p.executors,
                            p.progress_logs,
                            p.remaining_issues,
                            p.sales_rep,
                            p.site_code,
                            p.project_scale
                        )) like concat('%', search_char.ch, '%')
                  ) >= case
                      when char_length(regexp_replace(lower(btrim(:keyword)), '\\s+', '', 'g')) >= 5 then 3
                      when char_length(regexp_replace(lower(btrim(:keyword)), '\\s+', '', 'g')) >= 3 then 2
                      else 999
                  end
              )
            """,
            nativeQuery = true
    )
    Page<WorkProjectHistory> searchForGlobal(
            @Param("keyword") String keyword,
            @Param("customerName") String customerName,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate,
            Pageable pageable
    );

    @Query(value = """
            select p.*
            from work_project_history p
            where p.created_at >= :startDate
              and p.created_at < :endDate
              and (:customerName is null or :customerName = '' or lower(coalesce(p.client_name, '')) like lower(concat('%', :customerName, '%')))
              and (
                  :keyword is null or btrim(:keyword) = ''
                  or lower(concat_ws(' ',
                      p.client_name,
                      p.scope,
                      p.executors,
                      p.progress_logs,
                      p.remaining_issues,
                      p.sales_rep,
                      p.site_code,
                      p.project_scale
                  )) like concat('%', lower(btrim(:keyword)), '%')
                  or replace(lower(concat_ws(' ',
                      p.client_name,
                      p.scope,
                      p.executors,
                      p.progress_logs,
                      p.remaining_issues,
                      p.sales_rep,
                      p.site_code,
                      p.project_scale
                  )), ' ', '') like concat('%', replace(lower(btrim(:keyword)), ' ', ''), '%')
                  or exists (
                      select 1
                      from regexp_split_to_table(lower(btrim(:keyword)), '\\s+') as token(word)
                      where char_length(token.word) >= 2
                        and lower(concat_ws(' ',
                            p.client_name,
                            p.scope,
                            p.executors,
                            p.progress_logs,
                            p.remaining_issues,
                            p.sales_rep,
                            p.site_code,
                            p.project_scale
                        )) like concat('%', token.word, '%')
                  )
                  or greatest(
                      similarity(lower(coalesce(p.client_name, '')), lower(btrim(:keyword))),
                      similarity(lower(coalesce(p.scope, '')), lower(btrim(:keyword))),
                      similarity(left(lower(coalesce(p.progress_logs, '')), 1000), lower(btrim(:keyword))),
                      similarity(left(lower(coalesce(p.remaining_issues, '')), 1000), lower(btrim(:keyword)))
                  ) >= 0.35
                  or (
                      select count(distinct search_char.ch)::int
                      from regexp_split_to_table(regexp_replace(lower(btrim(:keyword)), '\\s+', '', 'g'), '') as search_char(ch)
                      where char_length(search_char.ch) > 0
                        and lower(concat_ws(' ',
                            p.client_name,
                            p.scope,
                            p.executors,
                            p.progress_logs,
                            p.remaining_issues,
                            p.sales_rep,
                            p.site_code,
                            p.project_scale
                        )) like concat('%', search_char.ch, '%')
                  ) >= case
                      when char_length(regexp_replace(lower(btrim(:keyword)), '\\s+', '', 'g')) >= 5 then 3
                      when char_length(regexp_replace(lower(btrim(:keyword)), '\\s+', '', 'g')) >= 3 then 2
                      else 999
                  end
              )
            """, nativeQuery = true)
    List<WorkProjectHistory> searchForGlobal(
            @Param("keyword") String keyword,
            @Param("customerName") String customerName,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate
    );
}
