-- 최적화에 의존하지 않는 검색 규칙 비교용. 소규모 테스트 문서의 모든 단어를 평가한다.
CREATE OR REPLACE FUNCTION pg_temp.search_reference(source_name text, requested_field text, keyword text, allowed_ids bigint[])
RETURNS TABLE(document_id bigint, score integer) LANGUAGE sql STABLE AS $$
    WITH q AS MATERIALIZED (
        SELECT lower(btrim(regexp_replace(keyword, '\s+', ' ', 'g'))) AS normalized_text,
               regexp_replace(lower(keyword), '\s+', '', 'g') AS compact,
               app_search_chars(keyword) AS characters
        WHERE keyword IS NOT NULL AND regexp_replace(keyword, '\s+', '', 'g') <> ''
    ), documents AS MATERIALIZED (
        SELECT d.*,
               CASE WHEN strpos(d.normalized_text, q.normalized_text) > 0 THEN 600000
                    WHEN strpos(d.compact_text, q.compact) > 0 THEN 500000 ELSE 0 END AS exact_score
        FROM app_search_document d CROSS JOIN q
        WHERE d.source_kind = source_name AND (requested_field = 'ANY' OR d.field_kind = requested_field)
          AND (allowed_ids IS NULL OR d.document_id = ANY(allowed_ids))
          AND (d.characters && q.characters OR cardinality(q.characters) = 0)
    ), word_scores AS MATERIALIZED (
        SELECT p.document_id, p.field_kind, app_search_similarity(t.word, q.compact) AS similarity
        FROM documents d JOIN app_search_posting p
          ON p.source_kind = source_name AND p.document_id = d.document_id AND p.field_kind = d.field_kind
        JOIN app_search_term t ON t.word_hash = p.word_hash CROSS JOIN q
        WHERE d.exact_score = 0 AND t.characters && q.characters
    ), typos AS (
        SELECT w.document_id, w.field_kind, max(w.similarity) AS similarity
        FROM word_scores w CROSS JOIN q
        WHERE w.similarity >= CASE WHEN char_length(q.compact) <= 3 THEN 0.66 ELSE 0.74 END
        GROUP BY w.document_id, w.field_kind
    ), hits AS (
        SELECT d.document_id,
            CASE WHEN d.exact_score > 0 THEN
                     d.exact_score + CASE d.field_kind WHEN 'TITLE' THEN 2000 WHEN 'CONTENT' THEN 1000 ELSE 0 END
                 WHEN t.similarity IS NOT NULL THEN
                     400000 + CASE d.field_kind WHEN 'TITLE' THEN 2000 WHEN 'CONTENT' THEN 1000 ELSE 0 END
                            + floor(t.similarity * 1000)::integer
                 WHEN overlap.n >= CASE WHEN char_length(q.compact) >= 5 THEN 3 WHEN char_length(q.compact) >= 3 THEN 2 ELSE 2147483647 END THEN
                     1000 + least(overlap.n, 20) + CASE WHEN d.field_kind = 'TITLE' THEN 100 ELSE 0 END
                 ELSE 0 END AS score
        FROM documents d CROSS JOIN q LEFT JOIN typos t
          ON t.document_id = d.document_id AND t.field_kind = d.field_kind
        CROSS JOIN LATERAL (SELECT count(*)::integer AS n FROM unnest(q.characters) ch WHERE ch = ANY(d.characters)) overlap
    ), combined AS (
        SELECT d.document_id, 1000 + least(count(DISTINCT ch)::integer, 20) AS score
        FROM documents d CROSS JOIN q CROSS JOIN LATERAL unnest(q.characters) ch
        WHERE requested_field = 'ANY' AND ch = ANY(d.characters)
        GROUP BY d.document_id, q.compact
        HAVING count(DISTINCT ch) >= CASE WHEN char_length(q.compact) >= 5 THEN 3 WHEN char_length(q.compact) >= 3 THEN 2 ELSE 2147483647 END
    )
    SELECT document_id, max(score)::integer
    FROM (SELECT * FROM hits WHERE score > 0 UNION ALL SELECT * FROM combined) h GROUP BY document_id
$$;
