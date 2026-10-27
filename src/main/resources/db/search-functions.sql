-- 허용 편집 횟수보다 하나 많은 글자 그룹 중 적어도 한 그룹은 전부 일치해야 한다.
-- 접두사 보너스를 받는 단어는 첫 3글자 그룹으로 별도 포함한다.
CREATE OR REPLACE FUNCTION app_search_char_groups(query_text text)
RETURNS TABLE(required_chars text[]) LANGUAGE sql IMMUTABLE PARALLEL SAFE AS $$
    WITH settings AS (
        SELECT char_length(query_text) AS m,
               CASE WHEN char_length(query_text) <= 3 THEN 0.66 ELSE 0.74 END AS threshold
    ), letters AS (
        SELECT DISTINCT ch FROM regexp_split_to_table(query_text, '') ch
        WHERE ch ~ '[[:alnum:]가-힣]'
    ), numbered AS (
        SELECT ch, row_number() OVER (ORDER BY ch) AS rn, count(*) OVER () AS n FROM letters
    ), groups AS (
        SELECT array_agg(ch ORDER BY ch) AS required_chars
        FROM numbered CROSS JOIN settings
        GROUP BY (rn - 1) % least(n, floor((1 - threshold) * greatest(m, floor(m / threshold)))::bigint + 1)
        UNION
        SELECT app_search_chars(left(query_text, 3)) WHERE char_length(query_text) >= 3
    )
    SELECT required_chars FROM groups
$$;
CREATE OR REPLACE FUNCTION app_search_candidates_scope(source_name text, requested_field text, keyword text, allowed_ids bigint[])
RETURNS TABLE(document_id bigint, score integer) LANGUAGE sql STABLE AS $$
    WITH q AS MATERIALIZED (
        SELECT lower(btrim(regexp_replace(keyword, '\s+', ' ', 'g'))) AS normalized_text,
               regexp_replace(lower(keyword), '\s+', '', 'g') AS compact,
               app_search_chars(keyword) AS characters,
               regexp_split_to_array(regexp_replace(lower(keyword), '\s+', '', 'g'), '') AS letters
        WHERE keyword IS NOT NULL AND regexp_replace(keyword, '\s+', '', 'g') <> ''
    ), documents AS MATERIALIZED (
        -- 본문 문자열은 한 번만 읽고 이후에는 ID, 글자 배열, 점수만 사용한다.
        SELECT d.document_id, d.field_kind, d.characters, pg_column_size(d.normalized_text) AS stored_bytes,
               CASE WHEN strpos(d.normalized_text, q.normalized_text) > 0 THEN 600000
                    WHEN strpos(d.compact_text, q.compact) > 0 THEN 500000 ELSE 0 END AS exact_score
        FROM q JOIN app_search_document d
          ON d.characters && q.characters OR cardinality(q.characters) = 0
        WHERE (allowed_ids IS NULL OR d.document_id = ANY(allowed_ids))
          AND d.source_kind = source_name
          AND (requested_field = 'ANY' OR d.field_kind = requested_field)
    ), strategy AS MATERIALIZED (
        SELECT requested_field = 'TITLE' OR coalesce(sum(stored_bytes), 0) <= 65536 AS scope_first
        FROM documents
    ), title_postings AS MATERIALIZED (
        -- 제목은 짧으므로 해당 제목의 단어 연결만 읽는다.
        -- 공용 사전에서 agent 후보 18만 개를 먼저 고르는 비용을 피한다.
        SELECT p.document_id, p.field_kind, p.word_hash
        FROM documents d JOIN app_search_posting p
          ON p.source_kind = source_name AND p.document_id = d.document_id AND p.field_kind = d.field_kind
        WHERE (SELECT scope_first FROM strategy) AND d.exact_score = 0
    ), title_terms AS MATERIALIZED (
        SELECT t.word_hash, t.word
        FROM (SELECT DISTINCT word_hash FROM title_postings) c
        JOIN app_search_term t USING (word_hash)
        CROSS JOIN q
        WHERE t.characters && q.characters
          AND t.word_length BETWEEN ceil(char_length(q.compact) * CASE WHEN char_length(q.compact) <= 3 THEN 0.58 ELSE 0.66 END)::integer
                                AND floor(char_length(q.compact) / CASE WHEN char_length(q.compact) <= 3 THEN 0.58 ELSE 0.66 END)::integer
          -- 편집 거리로도 임계값에 도달할 수 없는 글자 조합은 먼저 제외한다.
          -- 검색어의 중복 글자도 세며, 접두사 보너스는 넉넉하게 허용해 결과 누락을 막는다.
          AND (SELECT count(*) FROM unnest(q.letters) ch
               WHERE ch ~ '[[:alnum:]가-힣]' AND NOT (ch = ANY(t.characters)))
              <= floor((1.0 -
                  CASE WHEN char_length(q.compact) <= 3 THEN 0.66 ELSE 0.74 END +
                  CASE WHEN char_length(q.compact) >= 3 AND t.word_length >= 3 THEN 0.08 ELSE 0 END)
                  * greatest(char_length(q.compact), t.word_length))
    ), filtered_terms AS MATERIALIZED (
        -- 길이/글자 인덱스로 먼저 줄인다. 문서의 모든 단어 해시를 모으지 않는다.
        -- 길이 컬럼을 numeric으로 변환하지 않아 정수 B-tree 인덱스 조건을 유지한다.
        SELECT DISTINCT t.word_hash, t.word
        FROM q CROSS JOIN LATERAL app_search_char_groups(q.compact) g
        JOIN app_search_term t ON t.characters @> g.required_chars
        WHERE NOT (SELECT scope_first FROM strategy) AND t.characters && q.characters
          AND t.word_length BETWEEN ceil(char_length(q.compact) * CASE WHEN char_length(q.compact) <= 3 THEN 0.58 ELSE 0.66 END)::integer
                                AND floor(char_length(q.compact) / CASE WHEN char_length(q.compact) <= 3 THEN 0.58 ELSE 0.66 END)::integer
          -- 편집 거리로도 임계값에 도달할 수 없는 글자 조합은 먼저 제외한다.
          -- 검색어의 중복 글자도 세며, 접두사 보너스는 넉넉하게 허용해 결과 누락을 막는다.
          AND (SELECT count(*) FROM unnest(q.letters) ch
               WHERE ch ~ '[[:alnum:]가-힣]' AND NOT (ch = ANY(t.characters)))
              <= floor((1.0 -
                  CASE WHEN char_length(q.compact) <= 3 THEN 0.66 ELSE 0.74 END +
                  CASE WHEN char_length(q.compact) >= 3 AND t.word_length >= 3 THEN 0.08 ELSE 0 END)
                  * greatest(char_length(q.compact), t.word_length))
    ), posting_strategy AS MATERIALIZED (
        SELECT count(*) > 4096 AS source_first FROM filtered_terms
    ), source_postings AS MATERIALIZED (
        SELECT p.document_id, p.field_kind, p.word_hash
        FROM documents d JOIN app_search_posting p
          ON p.source_kind = source_name AND p.document_id = d.document_id AND p.field_kind = d.field_kind
        WHERE d.exact_score = 0 AND (SELECT source_first FROM posting_strategy)
    ), filtered_postings AS MATERIALIZED (
        -- 길이/글자 조건을 통과한 단어의 연결만 먼저 조회한다.
        -- 문서 수 × 단어 수만큼 기본키를 반복 조회하는 실행 계획을 방지한다.
        SELECT p.document_id, p.field_kind, p.word_hash
        FROM filtered_terms t JOIN app_search_posting p ON p.word_hash = t.word_hash
        WHERE NOT (SELECT source_first FROM posting_strategy) AND p.source_kind = source_name
          AND (requested_field = 'ANY' OR p.field_kind = requested_field)
        UNION ALL
        SELECT document_id, field_kind, word_hash FROM title_postings
        UNION ALL
        SELECT p.document_id, p.field_kind, p.word_hash
        FROM source_postings p JOIN filtered_terms t ON t.word_hash = p.word_hash
    ), relevant_terms AS MATERIALIZED (
        SELECT DISTINCT t.word_hash, t.word
        FROM filtered_terms t JOIN filtered_postings p ON p.word_hash = t.word_hash
        JOIN documents d ON d.document_id = p.document_id AND d.field_kind = p.field_kind
        WHERE NOT (SELECT scope_first FROM strategy) AND d.exact_score = 0
        UNION ALL
        SELECT word_hash, word FROM title_terms
    ), term_scores AS MATERIALIZED (
        SELECT t.word_hash, app_search_similarity(t.word, q.compact) AS similarity
        FROM relevant_terms t CROSS JOIN q
    ), typo_hits AS MATERIALIZED (
        SELECT p.document_id, p.field_kind, max(t.similarity) AS similarity
        FROM term_scores t CROSS JOIN q JOIN filtered_postings p ON p.word_hash = t.word_hash
        JOIN documents d ON d.document_id = p.document_id AND d.field_kind = p.field_kind AND d.exact_score = 0
        WHERE t.similarity >= CASE WHEN char_length(q.compact) <= 3 THEN 0.66 ELSE 0.74 END
        GROUP BY p.document_id, p.field_kind
    ), hits AS (
        SELECT d.document_id,
            CASE
                WHEN d.exact_score > 0 THEN
                    d.exact_score + CASE d.field_kind WHEN 'TITLE' THEN 2000 WHEN 'CONTENT' THEN 1000 ELSE 0 END
                WHEN t.similarity IS NOT NULL THEN
                    400000 + CASE d.field_kind WHEN 'TITLE' THEN 2000 WHEN 'CONTENT' THEN 1000 ELSE 0 END + floor(t.similarity * 1000)::integer
                WHEN overlap.n >= CASE WHEN char_length(q.compact) >= 5 THEN 3 WHEN char_length(q.compact) >= 3 THEN 2 ELSE 2147483647 END
                    THEN 1000 + least(overlap.n, 20) + CASE WHEN d.field_kind = 'TITLE' THEN 100 ELSE 0 END
                ELSE 0
            END AS score
        FROM documents d CROSS JOIN q
        LEFT JOIN typo_hits t ON t.document_id = d.document_id AND t.field_kind = d.field_kind
        CROSS JOIN LATERAL (SELECT count(*)::integer AS n FROM unnest(q.characters) ch WHERE ch = ANY(d.characters)) overlap
    ), combined_hits AS (
        SELECT d.document_id, 1000 + least(count(DISTINCT ch)::integer, 20) AS score
        FROM documents d CROSS JOIN q
        CROSS JOIN LATERAL unnest(q.characters) ch
        WHERE requested_field = 'ANY' AND ch = ANY(d.characters)
        GROUP BY d.document_id, q.characters, q.compact
        HAVING count(DISTINCT ch) >= CASE WHEN char_length(q.compact) >= 5 THEN 3 WHEN char_length(q.compact) >= 3 THEN 2 ELSE 2147483647 END
    )
    SELECT all_hits.document_id, max(all_hits.score)::integer
    FROM (SELECT * FROM hits WHERE hits.score > 0 UNION ALL SELECT * FROM combined_hits) all_hits
    GROUP BY all_hits.document_id
$$;
-- 기존 단일 검색/상세 검색 호출도 같은 엔진으로 처리한다.
CREATE OR REPLACE FUNCTION app_search_candidates(source_name text, requested_field text, keyword text)
RETURNS TABLE(document_id bigint, score integer) LANGUAGE sql STABLE AS $$
    SELECT * FROM app_search_candidates_scope(source_name, requested_field, keyword, NULL::bigint[])
$$;

-- 필터를 통과한 글만 검색한다. 제목+내용은 제목 후보 안에서 내용을 평가한다.
CREATE OR REPLACE FUNCTION app_search_rank(source_name text, keyword text, title_keyword text,
                                          content_keyword text, allowed_ids bigint[])
RETURNS TABLE(document_id bigint, score integer, tie_score integer)
LANGUAGE plpgsql STABLE AS $$
DECLARE
    title_ids bigint[];
    title_scores integer[];
    has_title boolean := title_keyword IS NOT NULL AND btrim(title_keyword) <> '';
    has_content boolean := content_keyword IS NOT NULL AND btrim(content_keyword) <> '';
BEGIN
    IF cardinality(allowed_ids) = 0 THEN RETURN; END IF;
    IF has_title AND has_content THEN
        SELECT coalesce(array_agg(t.document_id ORDER BY t.document_id), ARRAY[]::bigint[]), array_agg(t.score ORDER BY t.document_id)
        INTO title_ids, title_scores
        FROM app_search_candidates_scope(source_name, 'TITLE', title_keyword, allowed_ids) t;
        IF cardinality(title_ids) = 0 THEN RETURN; END IF;
        RETURN QUERY
        SELECT t.id, greatest(t.points, c.score), t.points + c.score
        FROM unnest(title_ids, title_scores) t(id, points)
        JOIN app_search_candidates_scope(source_name, 'CONTENT', content_keyword, title_ids) c
          ON c.document_id = t.id;
    ELSIF has_title OR has_content THEN
        RETURN QUERY SELECT c.document_id, c.score, c.score
        FROM app_search_candidates_scope(source_name,
            CASE WHEN has_title THEN 'TITLE' ELSE 'CONTENT' END,
            CASE WHEN has_title THEN title_keyword ELSE content_keyword END, allowed_ids) c;
    ELSIF keyword IS NOT NULL AND btrim(keyword) <> '' THEN
        RETURN QUERY SELECT c.document_id, c.score, c.score
        FROM app_search_candidates_scope(source_name, 'ANY', keyword, allowed_ids) c;
    ELSE
        RETURN QUERY SELECT id, 0, 0 FROM unnest(allowed_ids) id;
    END IF;
END $$;