import React from 'react';
import LabeledInput from '../common/LabeledInput';
import { categoryOptions, infraOptions, statusOptions } from '../../constants/issueOptions';

// 신규 이슈 등록 모달
// 기존 화면에 노출하던 등록 폼을 모달로 분리했습니다.
export default function CreateIssueModal({
  form,
  saving,
  handleChange,
  handleSubmit,
  onReset,
  onClose,
}) {
  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/40 px-4">
      <div className="max-h-[90vh] w-full max-w-[900px] overflow-y-auto rounded-2xl bg-white shadow-xl">
        <div className="sticky top-0 z-10 flex items-center justify-between border-b border-slate-200 bg-white px-6 py-4">
          <div>
            <h2 className="text-xl font-semibold text-slate-900">이슈 등록</h2>
            <p className="mt-1 text-sm text-slate-500">신규 이슈 사례를 등록합니다.</p>
          </div>

          <button
            type="button"
            onClick={onClose}
            className="rounded-lg border border-slate-300 bg-white px-3 py-2 text-sm text-slate-700 hover:bg-slate-50"
          >
            닫기
          </button>
        </div>

        <form onSubmit={handleSubmit} className="space-y-4 px-6 py-5">
          <div className="grid grid-cols-1 gap-4 md:grid-cols-2">
            <LabeledInput label="제목">
              <input
                className="w-full rounded-xl border border-slate-300 px-3 py-2 outline-none focus:border-slate-500"
                value={form.title}
                onChange={(e) => handleChange('title', e.target.value)}
                placeholder="예: DB 연결 끊김 사례"
              />
            </LabeledInput>

            <LabeledInput label="시스템명">
              <input
                className="w-full rounded-xl border border-slate-300 px-3 py-2 outline-none focus:border-slate-500"
                value={form.systemName}
                onChange={(e) => handleChange('systemName', e.target.value)}
                placeholder="예: EMS / 고객 시스템명"
              />
            </LabeledInput>

            <LabeledInput label="인프라 유형">
              <select
                className="w-full rounded-xl border border-slate-300 px-3 py-2 outline-none focus:border-slate-500"
                value={form.infraType}
                onChange={(e) => handleChange('infraType', e.target.value)}
              >
                {infraOptions.map((option) => (
                  <option key={option} value={option}>{option}</option>
                ))}
              </select>
            </LabeledInput>

            <LabeledInput label="고객사명">
              <input
                className="w-full rounded-xl border border-slate-300 px-3 py-2 outline-none focus:border-slate-500"
                value={form.customerName}
                onChange={(e) => handleChange('customerName', e.target.value)}
                placeholder="예: A고객사"
              />
            </LabeledInput>

            <LabeledInput label="DB버전">
              <input
                className="w-full rounded-xl border border-slate-300 px-3 py-2 outline-none focus:border-slate-500"
                value={form.versionInfo}
                onChange={(e) => handleChange('versionInfo', e.target.value)}
                placeholder="예: PostgreSQL 14"
              />
            </LabeledInput>

            <LabeledInput label="배포 버전">
              <input
                className="w-full rounded-xl border border-slate-300 px-3 py-2 outline-none focus:border-slate-500"
                value={form.deploymentVersion}
                onChange={(e) => handleChange('deploymentVersion', e.target.value)}
                placeholder="예: 20251114.X"
              />
            </LabeledInput>

            <LabeledInput label="상태">
              <select
                className="w-full rounded-xl border border-slate-300 px-3 py-2 outline-none focus:border-slate-500"
                value={form.status}
                onChange={(e) => handleChange('status', e.target.value)}
              >
                {statusOptions.map((option) => (
                  <option key={option} value={option}>{option}</option>
                ))}
              </select>
            </LabeledInput>

            <LabeledInput label="구분">
              <div className="flex gap-2">
                <select
                  className="w-[140px] shrink-0 rounded-xl border border-slate-300 px-2 py-2 outline-none focus:border-slate-500"
                  value={categoryOptions.includes(form.category) ? form.category : 'DIRECT'}
                  onChange={(e) => {
                    const value = e.target.value;
                    handleChange('category', value === 'DIRECT' ? '' : value);
                  }}
                >
                  <option value="DIRECT">직접입력</option>
                  {categoryOptions.map((option) => (
                    <option key={option} value={option}>{option}</option>
                  ))}
                </select>

                <input
                  className="min-w-0 flex-1 rounded-xl border border-slate-300 px-3 py-2 outline-none focus:border-slate-500"
                  value={form.category}
                  onChange={(e) => handleChange('category', e.target.value)}
                  placeholder="직접입력 또는 선택값"
                />
              </div>
            </LabeledInput>
          </div>

          <LabeledInput label="증상 요약">
            <input
              className="w-full rounded-xl border border-slate-300 px-3 py-2 outline-none focus:border-slate-500"
              value={form.symptomSummary}
              onChange={(e) => handleChange('symptomSummary', e.target.value)}
              placeholder="예: DB 연결이 간헐적으로 끊김"
            />
          </LabeledInput>

          <LabeledInput label="증상 상세">
            <textarea
              className="min-h-28 w-full rounded-xl border border-slate-300 px-3 py-2 outline-none focus:border-slate-500"
              value={form.symptomDetail}
              onChange={(e) => handleChange('symptomDetail', e.target.value)}
              placeholder="상세 증상을 입력하세요"
            />
          </LabeledInput>

          <LabeledInput label="원인">
            <textarea
              className="min-h-28 w-full rounded-xl border border-slate-300 px-3 py-2 outline-none focus:border-slate-500"
              value={form.causeDetail}
              onChange={(e) => handleChange('causeDetail', e.target.value)}
              placeholder="원인을 입력하세요"
            />
          </LabeledInput>

          <LabeledInput label="조치 내용">
            <textarea
              className="min-h-28 w-full rounded-xl border border-slate-300 px-3 py-2 outline-none focus:border-slate-500"
              value={form.actionDetail}
              onChange={(e) => handleChange('actionDetail', e.target.value)}
              placeholder="조치 내용을 입력하세요"
            />
          </LabeledInput>

          <div className="grid grid-cols-1 gap-4 md:grid-cols-2">
            <LabeledInput label="태그">
              <input
                className="w-full rounded-xl border border-slate-300 px-3 py-2 outline-none focus:border-slate-500"
                value={form.tags}
                onChange={(e) => handleChange('tags', e.target.value)}
                placeholder="예: DB, timeout, connection"
              />
            </LabeledInput>

            <LabeledInput label="작성자">
              <input
                className="w-full rounded-xl border border-slate-300 px-3 py-2 outline-none focus:border-slate-500"
                value={form.authorName}
                onChange={(e) => handleChange('authorName', e.target.value)}
                placeholder="예: 홍길동"
              />
            </LabeledInput>
          </div>

          <div className="flex justify-end gap-3 border-t border-slate-200 pt-4">
            <button
              type="button"
              onClick={onReset}
              className="rounded-xl border border-slate-300 bg-white px-4 py-2.5 text-sm font-semibold text-slate-700 hover:bg-slate-50"
            >
              초기화
            </button>

            <button
              type="submit"
              disabled={saving}
              className="rounded-xl bg-slate-900 px-4 py-2.5 text-sm font-semibold text-white shadow-sm hover:bg-slate-800 disabled:cursor-not-allowed disabled:bg-slate-400"
            >
              {saving ? '등록 중...' : '등록'}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}
