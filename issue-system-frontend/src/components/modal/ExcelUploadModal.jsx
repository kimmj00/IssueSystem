import React from 'react';

// 엑셀 업로드 모달
// 메인 화면에서는 버튼만 보이게 하고, 파일 선택은 모달 안에서 처리합니다.
export default function ExcelUploadModal({ file, onFileChange, onUpload, onClose }) {
  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/40 px-4">
      <div className="w-full max-w-[520px] rounded-2xl bg-white shadow-xl">
        <div className="flex items-center justify-between border-b border-slate-200 px-6 py-4">
          <div>
            <h2 className="text-xl font-semibold text-slate-900">엑셀 업로드</h2>
            <p className="mt-1 text-sm text-slate-500">
              패치리스트 엑셀 파일을 업로드합니다.
            </p>
          </div>

          <button
            type="button"
            onClick={onClose}
            className="rounded-lg border border-slate-300 bg-white px-3 py-2 text-sm text-slate-700 hover:bg-slate-50"
          >
            닫기
          </button>
        </div>

        <div className="space-y-4 px-6 py-5">
          <input
            type="file"
            accept=".xlsx,.xls"
            onChange={onFileChange}
            className="w-full rounded-lg border border-slate-300 px-3 py-2 text-sm"
          />

          <div className="text-sm text-slate-500">
            선택 파일: {file ? file.name : '없음'}
          </div>

          <div className="flex justify-end gap-3 border-t border-slate-200 pt-4">
            <button
              type="button"
              onClick={onClose}
              className="rounded-xl border border-slate-300 bg-white px-4 py-2.5 text-sm font-semibold text-slate-700 hover:bg-slate-50"
            >
              취소
            </button>

            <button
              type="button"
              onClick={onUpload}
              className="rounded-xl bg-slate-900 px-4 py-2.5 text-sm font-semibold text-white shadow-sm hover:bg-slate-800"
            >
              업로드
            </button>
          </div>
        </div>
      </div>
    </div>
  );
}
