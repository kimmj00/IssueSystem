import React from 'react';

export default function ExcelUploadModal({
  file,
  onFileChange,
  onUpload,
  onClose,
  isUploading = false,
}) {
  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/40 px-4">
      <div className="w-full max-w-[520px] rounded-2xl bg-white shadow-xl">
        <div className="flex items-center justify-between border-b border-slate-200 px-6 py-4">
          <div>
            <h2 className="text-xl font-semibold text-slate-900">엑셀 업로드</h2>
            <p className="mt-1 text-sm text-slate-500">
              엑셀 파일을 선택한 뒤 업로드합니다.
            </p>
          </div>

          <button
            type="button"
            onClick={onClose}
            disabled={isUploading}
            className="rounded-lg border border-slate-300 bg-white px-3 py-2 text-sm text-slate-700 hover:bg-slate-50 disabled:cursor-not-allowed disabled:opacity-50"
          >
            닫기
          </button>
        </div>

        <div className="space-y-4 px-6 py-5">
          <input
            type="file"
            accept=".xlsx,.xls"
            onChange={onFileChange}
            disabled={isUploading}
            className="w-full rounded-lg border border-slate-300 px-3 py-2 text-sm disabled:cursor-not-allowed disabled:bg-slate-100 disabled:text-slate-500"
          />

          <div className="text-sm text-slate-500">
            선택 파일: {file ? file.name : '없음'}
          </div>

          {isUploading && (
            <div className="rounded-lg border border-blue-200 bg-blue-50 px-3 py-2 text-sm font-medium text-blue-700">
              업로드 중...
            </div>
          )}

          <div className="flex justify-end gap-3 border-t border-slate-200 pt-4">
            <button
              type="button"
              onClick={onClose}
              disabled={isUploading}
              className="rounded-xl border border-slate-300 bg-white px-4 py-2.5 text-sm font-semibold text-slate-700 hover:bg-slate-50 disabled:cursor-not-allowed disabled:opacity-50"
            >
              취소
            </button>

            <button
              type="button"
              onClick={onUpload}
              disabled={isUploading}
              className="rounded-xl bg-slate-900 px-4 py-2.5 text-sm font-semibold text-white shadow-sm hover:bg-slate-800 disabled:cursor-not-allowed disabled:bg-slate-500"
            >
              {isUploading ? '업로드 중...' : '업로드'}
            </button>
          </div>
        </div>
      </div>
    </div>
  );
}
