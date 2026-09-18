package com.example.issuesystem.workissuehistory.repository;

import com.example.issuesystem.workissuehistory.domain.WorkMaintenanceHistory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface WorkMaintenanceHistoryRepository extends JpaRepository<WorkMaintenanceHistory, Long> {

    // 특정 업로드 파일에 포함된 유지보수 현황만 조회합니다.
    List<WorkMaintenanceHistory> findByUploadIdOrderByRowNoAscIdAsc(Long uploadId);

    long countByUploadId(Long uploadId);

    @Query(
            value = """
            select m.*
            from work_maintenance_history m
            where m.created_at >= :startDate
              and m.created_at < :endDate
              and (:customerName is null or :customerName = '' or lower(coalesce(m.maintenance_name, '')) like lower(concat('%', :customerName, '%')))
              and (
                  :keyword is null or btrim(:keyword) = ''
                  or lower(concat_ws(' ',
                      m.maintenance_name,
                      m.progress_issues,
                      m.remarks,
                      m.main_dev,
                      m.sub_dev,
                      m.sales_rep,
                      m.site_code,
                      m.region
                  )) like concat('%', lower(btrim(:keyword)), '%')
                  or replace(lower(concat_ws(' ',
                      m.maintenance_name,
                      m.progress_issues,
                      m.remarks,
                      m.main_dev,
                      m.sub_dev,
                      m.sales_rep,
                      m.site_code,
                      m.region
                  )), ' ', '') like concat('%', replace(lower(btrim(:keyword)), ' ', ''), '%')
                  or exists (
                      select 1
                      from regexp_split_to_table(lower(btrim(:keyword)), '\\s+') as token(word)
                      where char_length(token.word) >= 2
                        and lower(concat_ws(' ',
                            m.maintenance_name,
                            m.progress_issues,
                            m.remarks,
                            m.main_dev,
                            m.sub_dev,
                            m.sales_rep,
                            m.site_code,
                            m.region
                        )) like concat('%', token.word, '%')
                  )
                  or greatest(
                      similarity(lower(coalesce(m.maintenance_name, '')), lower(btrim(:keyword))),
                      similarity(lower(coalesce(m.progress_issues, '')), lower(btrim(:keyword))),
                      similarity(left(lower(coalesce(m.remarks, '')), 1000), lower(btrim(:keyword)))
                  ) >= 0.35
                  or (
                      select count(distinct search_char.ch)::int
                      from regexp_split_to_table(regexp_replace(lower(btrim(:keyword)), '\\s+', '', 'g'), '') as search_char(ch)
                      where char_length(search_char.ch) > 0
                        and lower(concat_ws(' ',
                            m.maintenance_name,
                            m.progress_issues,
                            m.remarks,
                            m.main_dev,
                            m.sub_dev,
                            m.sales_rep,
                            m.site_code,
                            m.region
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
                      case when lower(coalesce(m.maintenance_name, '')) like concat('%', lower(btrim(:keyword)), '%') then 80 else 0 end
                      + case when replace(lower(coalesce(m.maintenance_name, '')), ' ', '') like concat('%', replace(lower(btrim(:keyword)), ' ', ''), '%') then 70 else 0 end
                      + case when similarity(lower(coalesce(m.maintenance_name, '')), lower(btrim(:keyword))) >= 0.35 then 65 + floor(similarity(lower(coalesce(m.maintenance_name, '')), lower(btrim(:keyword))) * 10)::int else 0 end
                      + case when lower(coalesce(m.progress_issues, '')) like concat('%', lower(btrim(:keyword)), '%') then 60 else 0 end
                      + case when greatest(
                            similarity(lower(coalesce(m.progress_issues, '')), lower(btrim(:keyword))),
                            similarity(left(lower(coalesce(m.remarks, '')), 1000), lower(btrim(:keyword)))
                        ) >= 0.35 then 35 else 0 end
                      + case when lower(coalesce(m.remarks, '')) like concat('%', lower(btrim(:keyword)), '%') then 40 else 0 end
                      + coalesce((
                          select least(count(distinct search_char.ch)::int, 3) * 2
                          from regexp_split_to_table(regexp_replace(lower(btrim(:keyword)), '\\s+', '', 'g'), '') as search_char(ch)
                          where char_length(search_char.ch) > 0
                            and lower(coalesce(m.maintenance_name, '')) like concat('%', search_char.ch, '%')
                      ), 0)
                      + coalesce((
                          select least(count(distinct search_char.ch)::int, 3)
                          from regexp_split_to_table(regexp_replace(lower(btrim(:keyword)), '\\s+', '', 'g'), '') as search_char(ch)
                          where char_length(search_char.ch) > 0
                            and lower(concat_ws(' ', m.progress_issues, m.remarks))
                                like concat('%', search_char.ch, '%')
                      ), 0)
                      + coalesce((
                          select least(count(distinct search_char.ch)::int, 3)
                          from regexp_split_to_table(regexp_replace(lower(btrim(:keyword)), '\\s+', '', 'g'), '') as search_char(ch)
                          where char_length(search_char.ch) > 0
                            and lower(concat_ws(' ', m.main_dev, m.sub_dev, m.sales_rep, m.site_code, m.region))
                                like concat('%', search_char.ch, '%')
                      ), 0)
              end desc,
              m.created_at asc,
              m.id asc
            """,
            countQuery = """
            select count(*)
            from work_maintenance_history m
            where m.created_at >= :startDate
              and m.created_at < :endDate
              and (:customerName is null or :customerName = '' or lower(coalesce(m.maintenance_name, '')) like lower(concat('%', :customerName, '%')))
              and (
                  :keyword is null or btrim(:keyword) = ''
                  or lower(concat_ws(' ',
                      m.maintenance_name,
                      m.progress_issues,
                      m.remarks,
                      m.main_dev,
                      m.sub_dev,
                      m.sales_rep,
                      m.site_code,
                      m.region
                  )) like concat('%', lower(btrim(:keyword)), '%')
                  or replace(lower(concat_ws(' ',
                      m.maintenance_name,
                      m.progress_issues,
                      m.remarks,
                      m.main_dev,
                      m.sub_dev,
                      m.sales_rep,
                      m.site_code,
                      m.region
                  )), ' ', '') like concat('%', replace(lower(btrim(:keyword)), ' ', ''), '%')
                  or exists (
                      select 1
                      from regexp_split_to_table(lower(btrim(:keyword)), '\\s+') as token(word)
                      where char_length(token.word) >= 2
                        and lower(concat_ws(' ',
                            m.maintenance_name,
                            m.progress_issues,
                            m.remarks,
                            m.main_dev,
                            m.sub_dev,
                            m.sales_rep,
                            m.site_code,
                            m.region
                        )) like concat('%', token.word, '%')
                  )
                  or greatest(
                      similarity(lower(coalesce(m.maintenance_name, '')), lower(btrim(:keyword))),
                      similarity(lower(coalesce(m.progress_issues, '')), lower(btrim(:keyword))),
                      similarity(left(lower(coalesce(m.remarks, '')), 1000), lower(btrim(:keyword)))
                  ) >= 0.35
                  or (
                      select count(distinct search_char.ch)::int
                      from regexp_split_to_table(regexp_replace(lower(btrim(:keyword)), '\\s+', '', 'g'), '') as search_char(ch)
                      where char_length(search_char.ch) > 0
                        and lower(concat_ws(' ',
                            m.maintenance_name,
                            m.progress_issues,
                            m.remarks,
                            m.main_dev,
                            m.sub_dev,
                            m.sales_rep,
                            m.site_code,
                            m.region
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
    Page<WorkMaintenanceHistory> searchForGlobal(
            @Param("keyword") String keyword,
            @Param("customerName") String customerName,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate,
            Pageable pageable
    );

    @Query(value = """
            select m.*
            from work_maintenance_history m
            where m.created_at >= :startDate
              and m.created_at < :endDate
              and (:customerName is null or :customerName = '' or lower(coalesce(m.maintenance_name, '')) like lower(concat('%', :customerName, '%')))
              and (
                  :keyword is null or btrim(:keyword) = ''
                  or lower(concat_ws(' ',
                      m.maintenance_name,
                      m.progress_issues,
                      m.remarks,
                      m.main_dev,
                      m.sub_dev,
                      m.sales_rep,
                      m.site_code,
                      m.region
                  )) like concat('%', lower(btrim(:keyword)), '%')
                  or replace(lower(concat_ws(' ',
                      m.maintenance_name,
                      m.progress_issues,
                      m.remarks,
                      m.main_dev,
                      m.sub_dev,
                      m.sales_rep,
                      m.site_code,
                      m.region
                  )), ' ', '') like concat('%', replace(lower(btrim(:keyword)), ' ', ''), '%')
                  or exists (
                      select 1
                      from regexp_split_to_table(lower(btrim(:keyword)), '\\s+') as token(word)
                      where char_length(token.word) >= 2
                        and lower(concat_ws(' ',
                            m.maintenance_name,
                            m.progress_issues,
                            m.remarks,
                            m.main_dev,
                            m.sub_dev,
                            m.sales_rep,
                            m.site_code,
                            m.region
                        )) like concat('%', token.word, '%')
                  )
                  or greatest(
                      similarity(lower(coalesce(m.maintenance_name, '')), lower(btrim(:keyword))),
                      similarity(lower(coalesce(m.progress_issues, '')), lower(btrim(:keyword))),
                      similarity(left(lower(coalesce(m.remarks, '')), 1000), lower(btrim(:keyword)))
                  ) >= 0.35
                  or (
                      select count(distinct search_char.ch)::int
                      from regexp_split_to_table(regexp_replace(lower(btrim(:keyword)), '\\s+', '', 'g'), '') as search_char(ch)
                      where char_length(search_char.ch) > 0
                        and lower(concat_ws(' ',
                            m.maintenance_name,
                            m.progress_issues,
                            m.remarks,
                            m.main_dev,
                            m.sub_dev,
                            m.sales_rep,
                            m.site_code,
                            m.region
                        )) like concat('%', search_char.ch, '%')
                  ) >= case
                      when char_length(regexp_replace(lower(btrim(:keyword)), '\\s+', '', 'g')) >= 5 then 3
                      when char_length(regexp_replace(lower(btrim(:keyword)), '\\s+', '', 'g')) >= 3 then 2
                      else 999
                  end
              )
            """, nativeQuery = true)
    List<WorkMaintenanceHistory> searchForGlobal(
            @Param("keyword") String keyword,
            @Param("customerName") String customerName,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate
    );
}
