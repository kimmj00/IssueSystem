import React from 'react';
import Badge from '../common/Badge';
import DetailBlock from '../common/DetailBlock';

// 패치이력 상세보기 모달
// 목록 행을 클릭하면 이 모달에서 상세 내용을 확인합니다.
export default function PatchHistoryDetailModal({ loading, patchHistory, onClose }) {
  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/40 px-4">
      <div className="max-h-[90vh] w-full max-w-[980px] overflow-y-auto rounded-2xl bg-white shadow-xl">
        <div className="sticky top-0 z-10 flex items-center justify-between border-b border-slate-200 bg-white px-6 py-4">
          <div>
            <h2 className="text-xl font-semibold text-slate-900">패치이력 상세</h2>
            <p className="mt-1 text-sm text-slate-500">선택한 패치이력의 원인과 조치 내용을 확인합니다.</p>
          </div>

          <button
            type="button"
            onClick={onClose}
            className="rounded-lg border border-slate-300 bg-white px-3 py-2 text-sm text-slate-700 hover:bg-slate-50"
          >
            닫기
          </button>
        </div>

        <div className="px-6 py-5">
          {loading ? (
            <div className="rounded-xl border border-dashed border-slate-300 px-4 py-10 text-center text-slate-500">
              상세 정보를 불러오는 중입니다.
            </div>
          ) : !patchHistory ? (
            <div className="rounded-xl border border-dashed border-slate-300 px-4 py-10 text-center text-slate-500">
              상세 정보를 찾을 수 없습니다.
            </div>
          ) : (
            <div className="space-y-5">
              <div className="flex flex-wrap items-center gap-2">
                <h3 className="text-2xl font-semibold text-slate-900">{patchHistory.title}</h3>
                <Badge>{patchHistory.infraType}</Badge>
              </div>

              <div className="grid grid-cols-1 gap-3 md:grid-cols-3">
                <div className="rounded-xl bg-slate-50 p-4">
                  <div className="text-xs font-semibold uppercase tracking-wide text-slate-500">INFRA</div>
                  <div className="mt-1 text-sm text-slate-900">{patchHistory.infraType || '-'}</div>
                </div>

                <div className="rounded-xl bg-slate-50 p-4">
                  <div className="text-xs font-semibold uppercase tracking-wide text-slate-500">구분</div>
                  <div className="mt-1 text-sm text-slate-900">{patchHistory.category || '-'}</div>
                </div>

                <div className="rounded-xl bg-slate-50 p-4">
                  <div className="text-xs font-semibold uppercase tracking-wide text-slate-500">배포버전</div>
                  <div className="mt-1 text-sm text-slate-900">{patchHistory.deploymentVersion || '-'}</div>
                </div>

                <div className="rounded-xl bg-slate-50 p-4">
                  <div className="text-xs font-semibold uppercase tracking-wide text-slate-500">완료일</div>
                  <div className="mt-1 text-sm text-slate-900">{patchHistory.completedDate || '-'}</div>
                </div>

                <div className="rounded-xl bg-slate-50 p-4">
                  <div className="text-xs font-semibold uppercase tracking-wide text-slate-500">작성자</div>
                  <div className="mt-1 text-sm text-slate-900">{patchHistory.authorName || '-'}</div>
                </div>
              </div>

              <DetailBlock title="내용" value={patchHistory.content || patchHistory.symptomDetail} />
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
