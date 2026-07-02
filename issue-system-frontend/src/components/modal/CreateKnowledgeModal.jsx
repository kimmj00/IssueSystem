import React, { useEffect, useMemo, useRef, useState } from 'react';

const infraOptions = [
  'EMS',
  'GPM',
  'ERMS',
  'SMS',
  'NMS',
  'DBMS',
  'FMS',
  'IMS',
  'SYSLOG',
  'TRAP',
  'TMS',
  'APM',
  'BMS',
  'STMS',
  'RTMS',
  'VMS',
  'OAM',
  'WNMS',
  'CMS',
  'K8S',
  'TRMS',
  'NPM',
  'BRMS',
];

const emptyForm = {
  title: '',
  customerName: '',
  authorName: '',
  content: '',
  infraTypes: [],
};

function Field({ label, children }) {
  return (
      <label className="block">
        <div className="mb-2 text-sm font-medium text-slate-700">{label}</div>
        {children}
      </label>
  );
}

function isContentEmpty(value) {
  if (!value) {
    return true;
  }

  if (/<img\b/i.test(value)) {
    return false;
  }

  return value
      .replace(/<[^>]*>/g, '')
      .replace(/&nbsp;/g, ' ')
      .trim() === '';
}

function escapeHtmlAttribute(value) {
  return String(value).replace(/[&<>"']/g, (char) => ({
    '&': '&amp;',
    '<': '&lt;',
    '>': '&gt;',
    '"': '&quot;',
    "'": '&#39;',
  }[char]));
}

function sanitizeContentHtml(value) {
  const template = document.createElement('template');
  const allowedTags = new Set([
    'B',
    'BR',
    'DIV',
    'EM',
    'I',
    'IMG',
    'LI',
    'OL',
    'P',
    'STRONG',
    'U',
    'UL',
  ]);

  template.innerHTML = value || '';

  template.content.querySelectorAll('*').forEach((node) => {
    if (!allowedTags.has(node.tagName)) {
      node.replaceWith(document.createTextNode(node.textContent || ''));
      return;
    }

    if (node.tagName === 'IMG') {
      const src = node.getAttribute('src') || '';
      const alt = node.getAttribute('alt') || '';

      node.getAttributeNames().forEach((name) => node.removeAttribute(name));

      if (src.startsWith('data:image/')) {
        node.setAttribute('src', src);
        node.setAttribute('alt', alt);
      } else {
        node.remove();
      }

      return;
    }

    node.getAttributeNames().forEach((name) => node.removeAttribute(name));
  });

  return template.innerHTML;
}

function RichTextEditor({ value, onChange }) {
  const editorRef = useRef(null);
  const imageInputRef = useRef(null);
  const [dragging, setDragging] = useState(false);

  useEffect(() => {
    if (value === '' && editorRef.current && editorRef.current.innerHTML !== '') {
      editorRef.current.innerHTML = '';
    }
  }, [value]);

  const syncContent = () => {
    onChange(editorRef.current?.innerHTML || '');
  };

  const runCommand = (command) => {
    editorRef.current?.focus();
    document.execCommand(command, false, null);
    syncContent();
  };

  const insertImages = (files) => {
    Array.from(files || [])
        .filter((file) => file.type.startsWith('image/'))
        .forEach((file) => {
          const reader = new FileReader();

          reader.onload = () => {
            editorRef.current?.focus();
            document.execCommand(
                'insertHTML',
                false,
                `<img src="${reader.result}" alt="${escapeHtmlAttribute(file.name)}" />`
            );
            syncContent();
          };

          reader.readAsDataURL(file);
        });
  };

  const placeCaretFromPoint = (x, y) => {
    const selection = window.getSelection();

    if (!selection) {
      return;
    }

    let range = null;

    if (document.caretRangeFromPoint) {
      range = document.caretRangeFromPoint(x, y);
    } else if (document.caretPositionFromPoint) {
      const position = document.caretPositionFromPoint(x, y);

      if (position) {
        range = document.createRange();
        range.setStart(position.offsetNode, position.offset);
      }
    }

    if (range) {
      selection.removeAllRanges();
      selection.addRange(range);
    }
  };

  const handlePaste = (e) => {
    const files = Array.from(e.clipboardData?.files || []);
    const imageFiles = files.filter((file) => file.type.startsWith('image/'));

    if (imageFiles.length > 0) {
      e.preventDefault();
      insertImages(imageFiles);
      return;
    }

    const text = e.clipboardData?.getData('text/plain');
    if (text) {
      e.preventDefault();
      document.execCommand('insertText', false, text);
      syncContent();
    }
  };

  const handleDragOver = (e) => {
    e.preventDefault();
    e.dataTransfer.dropEffect = 'copy';
    setDragging(true);
  };

  const handleDragLeave = (e) => {
    if (!e.currentTarget.contains(e.relatedTarget)) {
      setDragging(false);
    }
  };

  const handleDrop = (e) => {
    e.preventDefault();
    setDragging(false);
    placeCaretFromPoint(e.clientX, e.clientY);
    insertImages(e.dataTransfer?.files);
  };

  return (
      <div>
        <div className="flex flex-wrap items-center gap-1 rounded-t-xl border border-b-0 border-slate-300 bg-slate-50 px-2 py-2">
          <button
              type="button"
              onClick={() => runCommand('bold')}
              className="h-8 min-w-8 rounded-md border border-slate-300 bg-white px-2 text-sm font-bold text-slate-700 hover:bg-slate-100"
          >
            B
          </button>
          <button
              type="button"
              onClick={() => runCommand('italic')}
              className="h-8 min-w-8 rounded-md border border-slate-300 bg-white px-2 text-sm italic text-slate-700 hover:bg-slate-100"
          >
            I
          </button>
          <button
              type="button"
              onClick={() => runCommand('insertUnorderedList')}
              className="h-8 rounded-md border border-slate-300 bg-white px-3 text-sm font-medium text-slate-700 hover:bg-slate-100"
          >
            목록
          </button>
          <button
              type="button"
              onClick={() => imageInputRef.current?.click()}
              className="h-8 rounded-md border border-slate-300 bg-white px-3 text-sm font-medium text-slate-700 hover:bg-slate-100"
          >
            이미지
          </button>
          <input
              ref={imageInputRef}
              type="file"
              accept="image/*"
              multiple
              className="hidden"
              onChange={(e) => {
                insertImages(e.target.files);
                e.target.value = '';
              }}
          />
        </div>

        <div className="relative">
          {isContentEmpty(value) && (
              <div className="pointer-events-none absolute left-4 top-3 text-sm text-slate-400">
                본문을 입력하거나 이미지를 삽입하세요.
              </div>
          )}
          <div
              ref={editorRef}
              contentEditable
              suppressContentEditableWarning
              onInput={syncContent}
              onPaste={handlePaste}
              onDragOver={handleDragOver}
              onDragLeave={handleDragLeave}
              onDrop={handleDrop}
              className={`min-h-[360px] w-full overflow-y-auto rounded-b-xl border px-4 py-3 text-sm leading-6 outline-none focus:border-slate-500 [&_img]:max-w-full [&_img]:rounded-lg ${
                  dragging
                      ? 'border-slate-700 bg-slate-50'
                      : 'border-slate-300 bg-white'
              }`}
          />
        </div>
      </div>
  );
}

export default function CreateKnowledgeModal({
                                               open,
                                               saving,
                                               onClose,
                                               onSubmit,
                                             }) {
  const [form, setForm] = useState(emptyForm);
  const [files, setFiles] = useState([]);

  const selectedCount = useMemo(() => form.infraTypes.length, [form.infraTypes]);

  if (!open) {
    return null;
  }

  const changeValue = (key, value) => {
    setForm((prev) => ({
      ...prev,
      [key]: value,
    }));
  };

  const toggleInfra = (infraType) => {
    setForm((prev) => {
      const exists = prev.infraTypes.includes(infraType);

      return {
        ...prev,
        infraTypes: exists
            ? prev.infraTypes.filter((item) => item !== infraType)
            : [...prev.infraTypes, infraType],
      };
    });
  };

  const resetForm = () => {
    setForm(emptyForm);
    setFiles([]);
  };

  const submit = async (e) => {
    e.preventDefault();

    if (!form.title.trim()) {
      alert('제목을 입력하세요.');
      return;
    }

    if (!form.authorName.trim()) {
      alert('담당자를 입력하세요.');
      return;
    }

    if (isContentEmpty(form.content)) {
      alert('내용을 입력하세요.');
      return;
    }

    if (form.infraTypes.length === 0) {
      alert('인프라를 하나 이상 선택하세요.');
      return;
    }

    const sanitizedContent = sanitizeContentHtml(form.content).trim();

    await onSubmit({
      ...form,
      title: form.title.trim(),
      customerName: form.customerName.trim(),
      authorName: form.authorName.trim(),
      content: sanitizedContent,
      attachmentName: files.map((file) => file.name).join(', '),
      files,
    });

    resetForm();
  };

  return (
      <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/40 px-4">
        <div className="flex max-h-[92vh] w-full max-w-[1180px] flex-col overflow-hidden rounded-2xl bg-white shadow-xl">
          {/* 상단 헤더 */}
          <div className="flex shrink-0 items-center justify-between border-b border-slate-200 px-6 py-4">
            <div>
              <h2 className="text-xl font-semibold text-slate-900">지식공유 등록</h2>
              <p className="mt-1 text-sm text-slate-500">
                운영 지식, 장애 처리 방법, 점검 절차 등을 등록합니다.
              </p>
            </div>

            <button
                type="button"
                onClick={onClose}
                className="rounded-lg border border-slate-300 bg-white px-4 py-2 text-sm font-medium text-slate-700 hover:bg-slate-50"
            >
              닫기
            </button>
          </div>

          <form onSubmit={submit} className="min-h-0 flex-1 overflow-y-auto">
            <div className="grid grid-cols-1 gap-5 px-6 py-5 xl:grid-cols-[minmax(0,1fr)_360px]">
              {/* 왼쪽: 기본 정보 + 내용 */}
              <div className="space-y-4">
                <div className="grid grid-cols-1 gap-4 md:grid-cols-2">
                  <Field label="제목">
                    <input
                        value={form.title}
                        onChange={(e) => changeValue('title', e.target.value)}
                        className="w-full rounded-xl border border-slate-300 px-3 py-2.5 text-sm outline-none focus:border-slate-500"
                        placeholder="예: DB 접속 오류 처리 방법"
                    />
                  </Field>

                  <Field label="고객사">
                    <input
                        value={form.customerName}
                        onChange={(e) => changeValue('customerName', e.target.value)}
                        className="w-full rounded-xl border border-slate-300 px-3 py-2.5 text-sm outline-none focus:border-slate-500"
                        placeholder="예: A고객사"
                    />
                  </Field>

                  <Field label="담당자">
                    <input
                        value={form.authorName}
                        onChange={(e) => changeValue('authorName', e.target.value)}
                        className="w-full rounded-xl border border-slate-300 px-3 py-2.5 text-sm outline-none focus:border-slate-500"
                        placeholder="예: 홍길동"
                    />
                  </Field>

                  <Field label="첨부파일">
                    <input
                        type="file"
                        multiple
                        onChange={(e) => setFiles(Array.from(e.target.files || []))}
                        className="w-full rounded-xl border border-slate-300 px-3 py-2 text-sm file:mr-3 file:rounded-lg file:border-0 file:bg-slate-900 file:px-3 file:py-1.5 file:text-sm file:font-medium file:text-white"
                    />
                  </Field>
                </div>

                {files.length > 0 && (
                    <div className="rounded-xl border border-slate-200 bg-slate-50 px-4 py-3">
                      <div className="mb-2 text-sm font-medium text-slate-700">
                        선택된 첨부파일
                      </div>

                      <div className="space-y-1">
                        {files.map((file, index) => (
                            <div
                                key={`${file.name}-${index}`}
                                className="text-sm text-slate-600"
                            >
                              {file.name}
                            </div>
                        ))}
                      </div>
                    </div>
                )}

                <Field label="내용">
                  <RichTextEditor
                      value={form.content}
                      onChange={(nextContent) => changeValue('content', nextContent)}
                  />
                </Field>
              </div>

              {/* 오른쪽: 인프라 체크박스 */}
              <aside className="rounded-2xl border border-slate-200 bg-slate-50 p-4">
                <div className="mb-3 flex items-center justify-between">
                  <div>
                    <h3 className="text-sm font-semibold text-slate-900">인프라</h3>
                    <p className="mt-1 text-xs text-slate-500">
                      등록은 다중 선택입니다.
                    </p>
                  </div>

                  <span className="rounded-full bg-white px-2.5 py-1 text-xs font-medium text-slate-600">
                  {selectedCount}개 선택
                </span>
                </div>

                <div className="max-h-[430px] overflow-y-auto pr-1">
                  <div className="grid grid-cols-2 gap-2">
                    {infraOptions.map((infraType) => {
                      const checked = form.infraTypes.includes(infraType);

                      return (
                          <label
                              key={infraType}
                              className={`flex cursor-pointer items-center gap-2 rounded-xl border px-3 py-2 text-sm transition ${
                                  checked
                                      ? 'border-slate-900 bg-white text-slate-900'
                                      : 'border-slate-200 bg-white text-slate-600 hover:border-slate-300'
                              }`}
                          >
                            <input
                                type="checkbox"
                                checked={checked}
                                onChange={() => toggleInfra(infraType)}
                                className="h-4 w-4"
                            />
                            <span>{infraType}</span>
                          </label>
                      );
                    })}
                  </div>
                </div>
              </aside>
            </div>

            {/* 하단 버튼 */}
            <div className="sticky bottom-0 flex justify-end gap-3 border-t border-slate-200 bg-white px-6 py-4">
              <button
                  type="button"
                  onClick={resetForm}
                  className="rounded-xl border border-slate-300 bg-white px-4 py-2.5 text-sm font-semibold text-slate-700 hover:bg-slate-50"
              >
                초기화
              </button>

              <button
                  type="submit"
                  disabled={saving}
                  className="rounded-xl bg-slate-900 px-5 py-2.5 text-sm font-semibold text-white shadow-sm hover:bg-slate-800 disabled:cursor-not-allowed disabled:bg-slate-400"
              >
                {saving ? '등록 중...' : '등록'}
              </button>
            </div>
          </form>
        </div>
      </div>
  );
}
