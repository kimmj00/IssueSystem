-- 검색 대상 글의 연결에서 길이로 먼저 제외한다. 큰 단어 원문과 공용 사전을 먼저 읽지 않는다.
-- ASCII 영숫자는 서로 다른 비트, 나머지 글자는 고정된 220개 비트에 배치한다.
-- 충돌은 추가 후보만 만든다. 최종 일치/점수는 기존 문자열 규칙으로 확인한다.
-- 이 계산식을 변경할 때는 기존 시그니처도 다시 채워야 한다.
CREATE OR REPLACE FUNCTION app_search_signature(characters text[]) RETURNS bit(256)
LANGUAGE plpgsql IMMUTABLE STRICT PARALLEL SAFE AS $$
DECLARE
    result bit(256) := B'0'::bit(256);
    ch text;
    code integer;
    position integer;
BEGIN
    FOREACH ch IN ARRAY characters LOOP
        code := ascii(ch);
        position := CASE WHEN code BETWEEN 97 AND 122 THEN code - 97
                         WHEN code BETWEEN 48 AND 57 THEN code - 48 + 26
                         ELSE 36 + ((code::bigint * 65599) % 220)::integer END;
        result := set_bit(result, position, 1);
    END LOOP;
    RETURN result;
END $$;

ALTER TABLE app_search_posting ADD COLUMN IF NOT EXISTS word_length integer;
ALTER TABLE app_search_posting ADD COLUMN IF NOT EXISTS char_signature bit(256);

CREATE OR REPLACE FUNCTION app_search_posting_length() RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
    -- 기존 4열 INSERT도 지원한다. 글/단어가 바뀐 UPDATE는 길이를 다시 맞춘다.
    IF TG_OP = 'INSERT' AND NEW.word_length IS NOT NULL AND NEW.char_signature IS NOT NULL THEN RETURN NEW; END IF;
    SELECT t.word_length, app_search_signature(t.characters) INTO NEW.word_length, NEW.char_signature
    FROM app_search_term t WHERE t.word_hash = NEW.word_hash;
    IF NOT FOUND THEN RAISE foreign_key_violation USING MESSAGE = 'Search term missing'; END IF;
    RETURN NEW;
END $$;
DROP TRIGGER IF EXISTS app_search_posting_length ON app_search_posting;
CREATE TRIGGER app_search_posting_length BEFORE INSERT OR UPDATE OF word_hash ON app_search_posting
    FOR EACH ROW EXECUTE FUNCTION app_search_posting_length();

DO $$
BEGIN
    -- 최초 업그레이드 때만 채운다. 이후 버전 갱신에서는 전체 연결을 다시 갱신하지 않는다.
    IF EXISTS (SELECT 1 FROM pg_attribute
               WHERE attrelid = 'app_search_posting'::regclass
                 AND attname IN ('word_length','char_signature') AND NOT attnotnull) THEN
        WITH missing_words AS MATERIALIZED (
            SELECT DISTINCT word_hash FROM app_search_posting WHERE word_length IS NULL OR char_signature IS NULL
        ), metadata AS MATERIALIZED (
            SELECT w.word_hash,t.word_length,app_search_signature(t.characters) AS signature
            FROM missing_words w CROSS JOIN LATERAL (
                SELECT word_length,characters FROM app_search_term t WHERE t.word_hash=w.word_hash LIMIT 1
            ) t
        )
        UPDATE app_search_posting p SET word_length = m.word_length, char_signature = m.signature
        FROM metadata m WHERE p.word_hash = m.word_hash AND (p.word_length IS NULL OR p.char_signature IS NULL);
        ALTER TABLE app_search_posting ALTER COLUMN word_length SET NOT NULL;
        ALTER TABLE app_search_posting ALTER COLUMN char_signature SET NOT NULL;
    END IF;
END $$;
CREATE INDEX IF NOT EXISTS idx_app_search_posting_scope_terms
    ON app_search_posting (source_kind, document_id, field_kind, word_length, word_hash) INCLUDE (char_signature);
ANALYZE app_search_posting;

CREATE OR REPLACE FUNCTION app_search_store(source_name text, row_id bigint, title_text text, body_text text, aux_text text)
RETURNS void LANGUAGE plpgsql AS $$
DECLARE
    field_name text;
    raw_text text;
    normalized text;
    compact text;
    words text[];
    changed integer;
BEGIN
    FOR field_name, raw_text IN
        SELECT * FROM (VALUES ('TITLE', title_text), ('CONTENT', body_text), ('AUX', aux_text)) fields
    LOOP
        normalized := lower(btrim(regexp_replace(coalesce(raw_text, ''), '\s+', ' ', 'g')));
        compact := replace(normalized, ' ', '');
        INSERT INTO app_search_document VALUES
            (source_name, row_id, field_name, normalized, compact, app_search_chars(normalized))
        ON CONFLICT (source_kind, document_id, field_kind) DO UPDATE SET
            normalized_text = excluded.normalized_text,
            compact_text = excluded.compact_text,
            characters = excluded.characters
        WHERE app_search_document.normalized_text IS DISTINCT FROM excluded.normalized_text;
        GET DIAGNOSTICS changed = ROW_COUNT;
        IF changed = 0 THEN CONTINUE; END IF;

        SELECT coalesce(array_agg(DISTINCT word ORDER BY word), ARRAY[]::text[]) INTO words
        FROM (SELECT regexp_split_to_table(normalized, '[^[:alnum:]가-힣]+') word
              UNION ALL SELECT compact) terms WHERE word <> '';
        INSERT INTO app_search_term(word_hash, word, characters, word_length)
        SELECT md5(word), word, app_search_chars(word), char_length(word) FROM unnest(words) word
        ON CONFLICT (word_hash) DO NOTHING;
        -- 해시는 긴 본문/단어로 인한 B-tree 크기 제한을 피하기 위한 키다.
        -- 충돌 시 다른 단어로 연결하지 않고 원문 쓰기와 함께 롤백한다.
        IF EXISTS (SELECT 1 FROM unnest(words) AS w(word) JOIN app_search_term t ON t.word_hash = md5(w.word)
                   WHERE t.word <> w.word) THEN
            RAISE EXCEPTION 'Search term hash collision';
        END IF;
        DELETE FROM app_search_posting WHERE source_kind = source_name AND document_id = row_id AND field_kind = field_name;
        INSERT INTO app_search_posting
        SELECT source_name, row_id, field_name, t.word_hash, t.word_length, app_search_signature(t.characters)
        FROM unnest(words) w(word) JOIN app_search_term t ON t.word_hash = md5(w.word);
    END LOOP;
END $$;

-- 이전 버전의 인덱스도 유지하여 기존 SQL 호출과 호환한다.
CREATE INDEX IF NOT EXISTS idx_app_search_term_prefix_length
    ON app_search_term ((left(word, 3)), word_length);

-- 검색에 쓰는 유사도만 계산한다. 통과 가능한 편집 거리의 띠 밖은 읽지 않는다.
-- 기존 app_search_similarity는 기준 점수 검증과 다른 호출을 위해 그대로 둔다.
CREATE OR REPLACE FUNCTION app_search_similarity_match(word text, query text) RETURNS double precision
LANGUAGE plpgsql IMMUTABLE STRICT PARALLEL SAFE AS $$
DECLARE
    n integer := char_length(word);
    m integer := char_length(query);
    threshold double precision := CASE WHEN m <= 3 THEN 0.66 ELSE 0.74 END;
    bonus double precision := CASE WHEN n >= 3 AND m >= 3 AND left(word, 3) = left(query, 3) THEN 0.08 ELSE 0 END;
    distance_limit integer;
    letters text[];
    query_letters text[];
    prev2 integer[];
    prev integer[];
    curr integer[];
    row_min integer;
    previous_min integer := 0;
    i integer;
    j integer;
BEGIN
    IF n = 0 OR m = 0 THEN RETURN 0; END IF;
    IF 1.0 - abs(n - m)::double precision / greatest(n, m) + bonus < threshold THEN RETURN 0; END IF;
    -- ceil로 경계에서 부동소수점 반올림 때문에 일치 후보가 빠지는 것을 방지한다.
    distance_limit := ceil((1.0 - threshold + bonus) * greatest(n, m))::integer;
    letters := string_to_array(word, NULL);
    query_letters := string_to_array(query, NULL);
    prev := ARRAY(SELECT generate_series(0, m));
    prev2 := prev;
    FOR i IN 1..n LOOP
        curr := array_fill(distance_limit + 1, ARRAY[m + 1]);
        curr[1] := i;
        row_min := i;
        FOR j IN greatest(1, i - distance_limit)..least(m, i + distance_limit) LOOP
            curr[j + 1] := least(prev[j + 1] + 1, curr[j] + 1,
                prev[j] + CASE WHEN letters[i] = query_letters[j] THEN 0 ELSE 1 END);
            IF i > 1 AND j > 1 AND letters[i] = query_letters[j - 1]
                AND letters[i - 1] = query_letters[j] THEN
                curr[j + 1] := least(curr[j + 1], prev2[j - 1] + 1);
            END IF;
            row_min := least(row_min, curr[j + 1]);
        END LOOP;
        -- 인접 교환이 두 행 전을 참조하므로 두 행 모두 한도를 넘었을 때만 종료한다.
        IF row_min > distance_limit AND previous_min > distance_limit THEN RETURN 0; END IF;
        previous_min := row_min;
        prev2 := prev; prev := curr;
    END LOOP;
    IF prev[m + 1] > distance_limit THEN RETURN 0; END IF;
    RETURN least(1.0, 1.0 - prev[m + 1]::double precision / greatest(n, m) + bonus);
END $$;

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
        SELECT input.*, char_length(compact) AS query_length, app_search_signature(characters) AS signature,
               CASE WHEN char_length(compact) <= 3 THEN 66 ELSE 74 END AS threshold_percent,
               ceil(char_length(compact) * CASE WHEN char_length(compact) <= 3 THEN 0.66 ELSE 0.74 END)::integer AS min_length,
               floor(char_length(compact) / CASE WHEN char_length(compact) <= 3 THEN 0.66 ELSE 0.74 END)::integer AS max_length,
               ceil(char_length(compact) * CASE WHEN char_length(compact) <= 3 THEN 0.58 ELSE 0.66 END)::integer AS bonus_min_length,
               floor(char_length(compact) / CASE WHEN char_length(compact) <= 3 THEN 0.58 ELSE 0.66 END)::integer AS bonus_max_length
        FROM (
            SELECT lower(btrim(regexp_replace(keyword, '\s+', ' ', 'g'))) AS normalized_text,
                   regexp_replace(lower(keyword), '\s+', '', 'g') AS compact,
                   app_search_chars(keyword) AS characters
            WHERE keyword IS NOT NULL AND regexp_replace(keyword, '\s+', '', 'g') <> ''
        ) input
    ), documents AS MATERIALIZED (
        -- 본문 문자열은 한 번만 읽고 이후에는 ID, 글자 배열, 점수만 사용한다.
        SELECT d.document_id, d.field_kind, d.characters,
               CASE WHEN strpos(d.normalized_text, q.normalized_text) > 0 THEN 600000
                    WHEN strpos(d.compact_text, q.compact) > 0 THEN 500000 ELSE 0 END AS exact_score
        FROM q JOIN app_search_document d
          ON d.characters && q.characters OR cardinality(q.characters) = 0
        -- PostgreSQL 13의 ANY(배열)는 행마다 ID 목록을 선형 탐색한다.
        -- 독립된 IN 하위 쿼리로 ID 집합을 한 번 해시하여 넓은 기간 검색도 일정하게 조회한다.
        WHERE (allowed_ids IS NULL OR d.document_id IN (SELECT unnest(allowed_ids)))
          AND d.source_kind = source_name
          AND (requested_field = 'ANY' OR d.field_kind = requested_field)
    ), query_signatures AS MATERIALIZED (
        SELECT g.required_chars,app_search_signature(g.required_chars) AS signature
        FROM q CROSS JOIN LATERAL app_search_char_groups(q.compact) g
    ), word_probe AS MATERIALIZED (
        -- 4글자 이상에서 선택성이 좋은 경우에만 작은 단어 해시 집합을 사용한다.
        -- 긴 원문을 가져오지 않고, 길이를 좁힌 메타 정보만 최대 129행 확인한다.
        SELECT t.word_hash
        FROM q CROSS JOIN query_signatures g
        CROSS JOIN LATERAL (
            SELECT t.word_hash FROM app_search_term t
            WHERE t.word_length BETWEEN q.bonus_min_length AND q.bonus_max_length
              AND t.characters @> g.required_chars
            LIMIT 129
        ) t
        WHERE requested_field <> 'TITLE' AND q.query_length > 3 AND cardinality(q.characters) > 0
          AND EXISTS (SELECT 1 FROM documents WHERE exact_score=0)
        LIMIT 129
    ), word_strategy AS MATERIALIZED (
        -- 조사 상한에 닿으면 대상의 모든 연결을 확인하므로 결과 후보를 자르지 않는다.
        SELECT requested_field <> 'TITLE' AND q.query_length > 3
               AND (SELECT count(*) FROM word_probe) <= 128 AS word_first FROM q
    ), scoped_scan AS MATERIALIZED (
        -- ID/필드/길이 인덱스를 먼저 조회한다. LIMIT으로 실제 후보를 자르지 않는다.
        -- OFFSET 0은 조인 순서를 유지하여 공용 사전에서 시작하는 계획을 방지한다.
        SELECT d.document_id, d.field_kind, p.word_hash
        FROM documents d CROSS JOIN q
        CROSS JOIN LATERAL (
            SELECT p.word_hash FROM app_search_posting p
            WHERE p.source_kind = source_name AND p.document_id = d.document_id
              AND p.field_kind = d.field_kind
              AND p.word_length BETWEEN q.bonus_min_length AND q.bonus_max_length
              -- 쿼리에 없는 글자의 비트 수는 편집 거리 하한이다. 충돌은 하한만 낮춘다.
              AND length(replace((p.char_signature & ~q.signature)::text,'0',''))
                  <= (100 - q.threshold_percent + 8) * greatest(q.query_length,p.word_length)::bigint / 100
              AND EXISTS (SELECT 1 FROM query_signatures g WHERE (p.char_signature & g.signature) = g.signature)
            OFFSET 0
        ) p
        WHERE d.exact_score = 0 AND cardinality(q.characters) > 0 AND NOT (SELECT word_first FROM word_strategy)
    ), scoped_postings AS MATERIALIZED (
        SELECT p.document_id,p.field_kind,p.word_hash
        FROM (SELECT DISTINCT word_hash FROM word_probe) h
        CROSS JOIN LATERAL (
            SELECT p.document_id,p.field_kind,p.word_hash FROM app_search_posting p
            WHERE p.word_hash=h.word_hash AND p.source_kind=source_name
              AND (requested_field='ANY' OR p.field_kind=requested_field)
              AND ((p.document_id,p.field_kind) IN (
                  SELECT document_id,field_kind FROM documents WHERE exact_score=0
              )) IS TRUE
            OFFSET 0
        ) p
        WHERE (SELECT word_first FROM word_strategy)
        UNION ALL
        SELECT document_id,field_kind,word_hash FROM scoped_scan
    ), relevant_terms AS MATERIALIZED (
        -- 중복 단어는 한 번만 평가하며, 길이 검사를 통과한 단어만 기본키로 가져온다.
        SELECT h.word_hash, t.word
        FROM (SELECT DISTINCT word_hash FROM scoped_postings) h CROSS JOIN q
        CROSS JOIN LATERAL (
            SELECT t.word, t.characters, t.word_length FROM app_search_term t
            WHERE t.word_hash = h.word_hash LIMIT 1
        ) t
        WHERE CASE
            WHEN t.word_length NOT BETWEEN q.bonus_min_length AND q.bonus_max_length THEN false
            WHEN NOT (t.characters && q.characters) THEN false
            WHEN t.word_length BETWEEN q.min_length AND q.max_length
              OR (q.query_length >= 3 AND left(t.word, 3) = left(q.compact, 3)
                  AND t.word_length BETWEEN q.bonus_min_length AND q.bonus_max_length)
            THEN greatest(abs(t.word_length - q.query_length),
                    char_length(translate(q.compact, t.word, '')),
                    char_length(translate(t.word, q.compact, '')))
                <= (100 - q.threshold_percent +
                    CASE WHEN q.query_length >= 3 AND t.word_length >= 3
                         AND left(t.word, 3) = left(q.compact, 3) THEN 8 ELSE 0 END)
                    * greatest(q.query_length, t.word_length)::bigint / 100
            ELSE false END
    ), term_scores AS MATERIALIZED (
        SELECT t.word_hash, app_search_similarity_match(t.word, q.compact) AS similarity
        FROM relevant_terms t CROSS JOIN q
    ), typo_hits AS MATERIALIZED (
        SELECT p.document_id, p.field_kind, max(t.similarity) AS similarity
        FROM term_scores t CROSS JOIN q JOIN scoped_postings p ON p.word_hash = t.word_hash
        -- scoped_postings의 모든 경로에서 대상 문서와 exact_score = 0을 이미 보장한다.
        -- 여기서 documents를 다시 조인하면 일반 실행계획에서 수백만 번 재비교할 수 있다.
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
-- 검색어·ID 목록마다 큰 계획을 새로 만들지 않는다. 함수 종료 시 호출자의 설정으로 복원된다.
LANGUAGE plpgsql STABLE SET plan_cache_mode = force_generic_plan SET jit = off SET client_min_messages = notice AS $$
DECLARE
    title_ids bigint[];
    title_scores integer[];
    has_title boolean := title_keyword IS NOT NULL AND btrim(title_keyword) <> '';
    has_content boolean := content_keyword IS NOT NULL AND btrim(content_keyword) <> '';
    started timestamptz := clock_timestamp();
    elapsed_ms numeric;
    result_count bigint;
    searched_field text;
    searched_keyword text;
    searched_ids bigint[] := allowed_ids;
BEGIN
    IF cardinality(allowed_ids) = 0 THEN RETURN; END IF;
    IF has_title AND has_content THEN
        SELECT coalesce(array_agg(t.document_id ORDER BY t.document_id), ARRAY[]::bigint[]), array_agg(t.score ORDER BY t.document_id)
        INTO title_ids, title_scores
        FROM app_search_candidates_scope(source_name, 'TITLE', title_keyword, allowed_ids) t;
        elapsed_ms := extract(epoch FROM clock_timestamp() - started) * 1000;
        IF elapsed_ms >= 1000 THEN
            RAISE NOTICE 'issue_search_metrics %', json_build_object(
                'source', source_name, 'field', 'TITLE', 'keywordLength', char_length(title_keyword),
                'allowedCount', cardinality(allowed_ids), 'resultCount', cardinality(title_ids),
                'elapsedMs', round(elapsed_ms, 2), 'backendPid', pg_backend_pid());
        END IF;
        IF cardinality(title_ids) = 0 THEN RETURN; END IF;
        searched_field := 'CONTENT'; searched_keyword := content_keyword; searched_ids := title_ids;
        started := clock_timestamp();
        RETURN QUERY
        SELECT t.id, greatest(t.points, c.score), t.points + c.score
        FROM unnest(title_ids, title_scores) t(id, points)
        JOIN app_search_candidates_scope(source_name, 'CONTENT', content_keyword, title_ids) c
          ON c.document_id = t.id;
    ELSIF has_title THEN
        searched_field := 'TITLE'; searched_keyword := title_keyword;
        RETURN QUERY SELECT c.document_id, c.score, c.score
        FROM app_search_candidates_scope(source_name, 'TITLE', title_keyword, allowed_ids) c;
    ELSIF has_content THEN
        searched_field := 'CONTENT'; searched_keyword := content_keyword;
        RETURN QUERY SELECT c.document_id, c.score, c.score
        FROM app_search_candidates_scope(source_name, 'CONTENT', content_keyword, allowed_ids) c;
    ELSIF keyword IS NOT NULL AND btrim(keyword) <> '' THEN
        searched_field := 'ANY'; searched_keyword := keyword;
        RETURN QUERY SELECT c.document_id, c.score, c.score
        FROM app_search_candidates_scope(source_name, 'ANY', keyword, allowed_ids) c;
    ELSE
        RETURN QUERY SELECT id, 0, 0 FROM unnest(allowed_ids) id;
    END IF;
    GET DIAGNOSTICS result_count = ROW_COUNT;
    elapsed_ms := extract(epoch FROM clock_timestamp() - started) * 1000;
    -- 별도 SQL 래퍼를 두지 않아 제목/내용 상수에 따른 실행계획 최적화를 유지한다.
    -- 원문 검색어 없이 느린 단계의 범위와 결과 건수만 JDBC 경고로 전달한다.
    IF searched_field IS NOT NULL AND elapsed_ms >= 1000 THEN
        RAISE NOTICE 'issue_search_metrics %', json_build_object(
            'source', source_name, 'field', searched_field, 'keywordLength', char_length(searched_keyword),
            'allowedCount', cardinality(searched_ids), 'resultCount', result_count,
            'elapsedMs', round(elapsed_ms, 2), 'backendPid', pg_backend_pid());
    END IF;
END $$;
