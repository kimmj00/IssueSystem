package com.example.issuesystem.patchhistory.service;

import com.example.issuesystem.common.PageResponse;
import com.example.issuesystem.common.domain.InfraType;
import com.example.issuesystem.patchhistory.domain.PatchHistory;
import com.example.issuesystem.patchhistory.domain.PatchStatus;
import com.example.issuesystem.patchhistory.dto.PatchHistoryCreateRequest;
import com.example.issuesystem.patchhistory.dto.PatchHistoryFilterOptionsResponse;
import com.example.issuesystem.patchhistory.dto.PatchHistoryResponse;
import com.example.issuesystem.patchhistory.dto.PatchHistoryUpdateRequest;
import com.example.issuesystem.patchhistory.repository.PatchHistoryRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PatchHistoryService {

    private static final String FILTER_DELIMITER = "\u001F";

    private final PatchHistoryRepository patchHistoryRepository;

    /**
     * 패치이력 등록
     *
     * issue_attachment 테이블을 사용하지 않으므로 첨부파일 저장 로직은 제거했다.
     */
    @Transactional
    public Long create(PatchHistoryCreateRequest request) {
        String content = chooseText(request.getContent(), request.getSymptomDetail());
        String summary = chooseText(request.getSymptomSummary(), firstLine(content), request.getTitle());

        PatchHistory patchHistory = PatchHistory.builder()
                .title(request.getTitle())
                .infraType(request.getInfraType())
                .systemName(request.getSystemName())
                .customerName(request.getCustomerName())
                .versionInfo(request.getVersionInfo())
                .status(request.getStatus())
                .symptomSummary(summary)
                .symptomDetail(content)
                .causeDetail(request.getCauseDetail())
                .actionDetail(request.getActionDetail())
                .tags(request.getTags())
                .authorName(request.getAuthorName())
                .category(request.getCategory())
                .deploymentVersion(request.getDeploymentVersion())
                .completedDate(request.getCompletedDate())
                .build();

        return patchHistoryRepository.save(patchHistory).getId();
    }

    /** 패치이력 수정 */
    @Transactional
    public void update(Long id, PatchHistoryUpdateRequest request) {
        PatchHistory patchHistory = patchHistoryRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("패치이력을 찾을 수 없습니다."));

        String content = chooseText(request.getContent(), request.getSymptomDetail());
        String summary = chooseText(request.getSymptomSummary(), firstLine(content), request.getTitle());

        patchHistory.update(
                request.getTitle(),
                request.getInfraType(),
                request.getSystemName(),
                request.getCustomerName(),
                request.getVersionInfo(),
                request.getStatus(),
                summary,
                content,
                request.getCauseDetail(),
                request.getActionDetail(),
                request.getTags(),
                request.getCategory(),
                request.getDeploymentVersion(),
                request.getCompletedDate()
        );
    }

    /** 단건 상세 조회 */
    @Transactional
    public PatchHistoryResponse get(Long id) {
        PatchHistory patchHistory = patchHistoryRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("패치이력을 찾을 수 없습니다."));

        return PatchHistoryResponse.from(patchHistory);
    }

    /** 전체 목록 조회 */
    @Transactional
    public List<PatchHistoryResponse> getAll() {
        return patchHistoryRepository.findAll().stream()
                .map(PatchHistoryResponse::from)
                .toList();
    }

    /**
     * 검색 + 페이징 조회
     *
     * 기간 검색:
     * - startDate: 해당 날짜 00:00:00 이상
     * - endDate: 해당 날짜 다음날 00:00:00 미만
     */
    @Transactional
    public PageResponse<PatchHistoryResponse> search(
            String keyword,
            InfraType infraType,
            List<InfraType> infraTypes,
            PatchStatus status,
            String customerName,
            String category,
            List<String> categories,
            String deploymentVersion,
            List<String> deploymentVersions,
            String detailType,
            String detailDeploymentVersion,
            String detailVersionRelation,
            LocalDate startDate,
            LocalDate endDate,
            int page,
            int size
    ) {
        int safePage = Math.max(page, 0);
        int safeSize = size <= 0 ? 5 : size;

        // PostgreSQL native query의 날짜 파라미터 타입 추론 오류 방지를 위해 null 대신 기본 범위를 넣는다.
        LocalDateTime startDateTime = startDate != null
                ? startDate.atStartOfDay()
                : LocalDateTime.of(1970, 1, 1, 0, 0);

        LocalDateTime endDateTime = endDate != null
                ? endDate.plusDays(1).atStartOfDay()
                : LocalDateTime.of(9999, 12, 31, 0, 0);

        String normalizedDetailType = normalizeDetailType(detailType);
        String infraTypesCsv = joinInfraTypes(infraTypes);
        String categoriesCsv = joinValues(categories);
        String deploymentVersionsCsv = joinValues(deploymentVersions);
        String normalizedRelation = normalizeVersionRelation(detailVersionRelation);
        String normalizedDetailDeploymentVersion = blankToNull(detailDeploymentVersion);
        if ("ALL".equals(normalizedRelation)) {
            normalizedDetailDeploymentVersion = null;
        }
        String selectedVersionNumber = extractVersionNumber(normalizedDetailDeploymentVersion);
        boolean useCompletedDateComparison = "SECURITY".equals(normalizedDetailType)
                && isDateAlphaVersion(normalizedDetailDeploymentVersion);
        LocalDate referenceCompletedDate = useCompletedDateComparison && normalizedDetailDeploymentVersion != null
                ? patchHistoryRepository.findReferenceCompletedDate(normalizedDetailDeploymentVersion)
                : null;

        Page<Long> idPage = normalizedDetailType == null
                ? patchHistoryRepository.searchIds(
                        keyword,
                        infraType != null ? infraType.name() : null,
                        status != null ? status.name() : null,
                        customerName,
                        category,
                        deploymentVersion,
                        infraTypesCsv,
                        categoriesCsv,
                        deploymentVersionsCsv,
                        FILTER_DELIMITER,
                        startDateTime,
                        endDateTime,
                        PageRequest.of(safePage, safeSize)
                )
                : patchHistoryRepository.detailSearchIds(
                        keyword,
                        infraType != null ? infraType.name() : null,
                        status != null ? status.name() : null,
                        customerName,
                        category,
                        deploymentVersion,
                        infraTypesCsv,
                        categoriesCsv,
                        deploymentVersionsCsv,
                        FILTER_DELIMITER,
                        normalizedDetailType,
                        normalizedDetailDeploymentVersion,
                        normalizedRelation,
                        selectedVersionNumber,
                        useCompletedDateComparison,
                        referenceCompletedDate,
                        startDateTime,
                        endDateTime,
                        PageRequest.of(safePage, safeSize)
                );

        List<Long> ids = idPage.getContent();

        if (ids.isEmpty()) {
            Page<PatchHistoryResponse> emptyPage = new PageImpl<>(
                    List.of(),
                    idPage.getPageable(),
                    idPage.getTotalElements()
            );
            return PageResponse.from(emptyPage);
        }

        List<PatchHistory> patchHistories = patchHistoryRepository.findByIdIn(ids);

        Map<Long, PatchHistory> patchHistoryMap = patchHistories.stream()
                .collect(Collectors.toMap(PatchHistory::getId, Function.identity()));

        // searchIds()에서 가져온 ID 순서를 유지한다.
        // findByIdIn()은 DB가 반환 순서를 보장하지 않으므로 idPage의 ids 순서대로 다시 정렬한다.
        List<PatchHistoryResponse> content = ids.stream()
                .map(patchHistoryMap::get)
                .filter(patchHistory -> patchHistory != null)
                .map(PatchHistoryResponse::from)
                .toList();

        Page<PatchHistoryResponse> responsePage = new PageImpl<>(
                content,
                idPage.getPageable(),
                idPage.getTotalElements()
        );

        return PageResponse.from(responsePage);
    }

    @Transactional
    public PatchHistoryFilterOptionsResponse getFilterOptions(List<String> categories) {
        String categoriesCsv = joinValues(categories);

        return PatchHistoryFilterOptionsResponse.builder()
                .categories(patchHistoryRepository.findCategories())
                .deploymentVersions(patchHistoryRepository.findDeploymentVersionsByCategories(
                        categoriesCsv,
                        FILTER_DELIMITER
                ))
                .build();
    }

    @Transactional
    public List<String> getDeploymentVersions(String detailType) {
        String normalizedDetailType = normalizeDetailType(detailType);

        return patchHistoryRepository.findDeploymentVersions(normalizedDetailType);
    }

    private String normalizeDetailType(String detailType) {
        if (detailType == null || detailType.trim().isEmpty()) {
            return null;
        }

        String normalized = detailType.trim().toUpperCase();

        return switch (normalized) {
            case "WEB", "CORE", "SECURITY" -> normalized;
            default -> null;
        };
    }

    private String normalizeVersionRelation(String relation) {
        if (relation == null || relation.trim().isEmpty()) {
            return "SAME";
        }

        String normalized = relation.trim().toUpperCase();

        return switch (normalized) {
            case "ALL", "SAME", "BEFORE", "AFTER" -> normalized;
            default -> "SAME";
        };
    }

    private String blankToNull(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }

        return value.trim();
    }

    private String joinInfraTypes(List<InfraType> values) {
        if (values == null || values.isEmpty()) {
            return null;
        }

        String joined = values.stream()
                .filter(value -> value != null)
                .map(Enum::name)
                .distinct()
                .collect(Collectors.joining(FILTER_DELIMITER));

        return joined.isEmpty() ? null : joined;
    }

    private String joinValues(List<String> values) {
        if (values == null || values.isEmpty()) {
            return null;
        }

        String joined = values.stream()
                .map(this::blankToNull)
                .filter(value -> value != null)
                .distinct()
                .collect(Collectors.joining(FILTER_DELIMITER));

        return joined.isEmpty() ? null : joined;
    }

    private boolean isDateAlphaVersion(String value) {
        return value != null && value.matches("\\d{8}\\.[A-Za-z]+");
    }

    private String extractVersionNumber(String value) {
        if (value == null) {
            return null;
        }

        String digits = value.replaceAll("\\D", "");

        return digits.isEmpty() ? null : digits;
    }

    private String chooseText(String... values) {
        for (String value : values) {
            if (value != null && !value.trim().isEmpty()) {
                return value;
            }
        }

        return "";
    }

    private String firstLine(String value) {
        if (value == null || value.trim().isEmpty()) {
            return "";
        }

        return value.split("\\R", 2)[0].trim();
    }
}
