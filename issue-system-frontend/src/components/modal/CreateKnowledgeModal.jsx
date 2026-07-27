import React, { useEffect, useMemo, useRef, useState } from 'react';

const infraOptions = [
  'EMS',
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
  'GPM',
  '운영관리',
  '기타',
];

const emptyForm = {
  title: '',
  customerName: '',
  authorName: '',
  content: '',
  infraTypes: [],
};

const COLOR_PALETTE = [
  '#000000', '#444444', '#666666', '#999999', '#b7b7b7', '#cccccc', '#dddddd', '#eeeeee',
  '#ff0000', '#ff9900', '#ffff00', '#00ff00', '#00ffff', '#0000ff', '#9900ff', '#ff00ff',
  '#f4cccc', '#fce5cd', '#fff2cc', '#d9ead3', '#d0e0e3', '#c9daf8', '#cfe2f3', '#d9d2e9',
  '#ea9999', '#f9cb9c', '#ffe599', '#b6d7a8', '#a2c4c9', '#a4c2f4', '#9fc5e8', '#b4a7d6',
  '#e06666', '#f6b26b', '#ffd966', '#93c47d', '#76a5af', '#6d9eeb', '#6fa8dc', '#8e7cc3',
  '#cc0000', '#e69138', '#f1c232', '#6aa84f', '#45818e', '#3c78d8', '#3d85c6', '#674ea7',
  '#990000', '#b45f06', '#bf9000', '#38761d', '#134f5c', '#1155cc', '#0b5394', '#351c75',
];

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
    'SPAN',
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

    if (node.tagName === 'SPAN') {
      const color = node.style.color || '';
      const backgroundColor = node.style.backgroundColor || '';
      const fontSize = node.style.fontSize || '';
      node.getAttributeNames().forEach((name) => node.removeAttribute(name));
      if (/^(#[0-9a-f]{3,8}|rgba?\([\d\s,.%]+\))$/i.test(color)) {
        node.style.color = color;
      }
      if (/^(#[0-9a-f]{3,8}|rgba?\([\d\s,.%]+\))$/i.test(backgroundColor)) {
        node.style.backgroundColor = backgroundColor;
      }
      if (/^(?:[5-9]|[1-3]\d|40)pt$/.test(fontSize)) {
        node.style.fontSize = fontSize;
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
  const selectedRangeRef = useRef(null);
  const selectionStartHtmlRef = useRef('');
  const selectingTextRef = useRef(false);
  const [dragging, setDragging] = useState(false);
  const [showColorPalette, setShowColorPalette] = useState(false);
  const [activeFormats, setActiveFormats] = useState({ bold: false, italic: false, underline: false });

  useEffect(() => {
    if (editorRef.current && editorRef.current.innerHTML !== (value || '')) {
      editorRef.current.innerHTML = value || '';
    }
  }, [value]);

  const syncContent = () => {
    onChange(editorRef.current?.innerHTML || '');
  };

  const updateActiveFormats = () => {
    try {
      setActiveFormats({
        bold: document.queryCommandState('bold'),
        italic: document.queryCommandState('italic'),
        underline: document.queryCommandState('underline'),
      });
    } catch {
      setActiveFormats({ bold: false, italic: false, underline: false });
    }
  };

  const saveSelection = () => {
    const selection = window.getSelection();
    if (!selection?.rangeCount || !editorRef.current?.contains(selection.anchorNode)) {
      return;
    }

    selectedRangeRef.current = selection.getRangeAt(0).cloneRange();
    setActiveFormats({ bold: false, italic: false, underline: false });
  };

  const handleSelectionStart = () => {
    selectingTextRef.current = true;
    selectionStartHtmlRef.current = editorRef.current?.innerHTML || '';
    setActiveFormats({ bold: false, italic: false, underline: false });
  };

  const handleSelectionMove = () => {
    if (selectingTextRef.current) {
      setActiveFormats({ bold: false, italic: false, underline: false });
    }
  };

  const keepDraggedSelectionUnformatted = () => {
    const selection = window.getSelection();
    if (selection && !selection.isCollapsed && editorRef.current?.contains(selection.anchorNode)) {
      setActiveFormats({ bold: false, italic: false, underline: false });
    }
  };

  const handleSelectionEnd = () => {
    const editor = editorRef.current;
    const selection = window.getSelection();
    const draggedText = selection && !selection.isCollapsed;

    if (editor && editor.innerHTML !== selectionStartHtmlRef.current) {
      editor.innerHTML = selectionStartHtmlRef.current;
      onChange(editor.innerHTML);
      selectedRangeRef.current = null;
      setActiveFormats({ bold: false, italic: false, underline: false });
    } else {
      saveSelection();
    }

    if (draggedText) {
      setActiveFormats({ bold: false, italic: false, underline: false });
      window.requestAnimationFrame(keepDraggedSelectionUnformatted);
    }
    selectingTextRef.current = false;
  };

  const preventToolbarFocus = (event) => {
    event.preventDefault();
  };

  const restoreSelection = () => {
    const selection = window.getSelection();
    if (selection && selectedRangeRef.current) {
      selection.removeAllRanges();
      selection.addRange(selectedRangeRef.current);
    }
    return selection;
  };

  const getSelectedEditorRange = () => {
    const selection = restoreSelection();

    if (!selection?.rangeCount) {
      return null;
    }

    const range = selection.getRangeAt(0);
    if (range.collapsed || !editorRef.current?.contains(range.commonAncestorContainer)) {
      return null;
    }

    return { selection, range };
  };

  const runCommand = (command, value = null) => {
    const selected = getSelectedEditorRange();
    if (!selected) {
      return;
    }

    document.execCommand(command, false, value);
    syncContent();
    if (selected.selection.rangeCount) {
      selectedRangeRef.current = selected.selection.getRangeAt(0).cloneRange();
    }
    updateActiveFormats();
  };

  const handleKeyDown = (event) => {
    const key = event.key.toLowerCase();
    const isFormatShortcut = (event.ctrlKey || event.metaKey) && ['b', 'i', 'u'].includes(key);

    if (isFormatShortcut) {
      event.preventDefault();
      const command = { b: 'bold', i: 'italic', u: 'underline' }[key];
      runCommand(command);
    }
  };

  const insertImages = (files) => {
    Array.from(files || [])
        .filter((file) => file.type.startsWith('image/'))
        .forEach((file) => {
          const reader = new FileReader();

          reader.onload = () => {
            editorRef.current?.focus();
            restoreSelection();
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

  const applyColor = (command, color) => {
    const selected = getSelectedEditorRange();
    if (!selected) {
      setShowColorPalette(false);
      return;
    }

    document.execCommand('styleWithCSS', false, true);
    document.execCommand(command, false, color);
    syncContent();
    if (selected.selection.rangeCount) {
      selectedRangeRef.current = selected.selection.getRangeAt(0).cloneRange();
    }
    setShowColorPalette(false);
  };

  const handleDragOver = (e) => {
    e.preventDefault();
    const hasFiles = Array.from(e.dataTransfer?.types || []).includes('Files');
    e.dataTransfer.dropEffect = hasFiles ? 'copy' : 'none';
    setDragging(hasFiles);
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
    const imageFiles = Array.from(e.dataTransfer?.files || []).filter((file) => file.type.startsWith('image/'));
    if (imageFiles.length > 0) {
      insertImages(imageFiles);
    }
  };

  return (
      <div>
        <div className="relative flex flex-wrap items-center gap-0.5 rounded-t-xl border border-b-0 border-slate-300 bg-slate-100 px-2 py-1.5">
          <button
              type="button"
              onMouseDown={preventToolbarFocus}
              onClick={() => runCommand('bold')}
              title="볼드체 (Ctrl+B)"
              className={`h-8 min-w-8 rounded px-2 text-base font-bold ${activeFormats.bold ? 'bg-slate-300 text-slate-900' : 'text-slate-700 hover:bg-slate-200'}`}
          >
            B
          </button>
          <button
              type="button"
              onMouseDown={preventToolbarFocus}
              onClick={() => runCommand('italic')}
              title="기울기 (Ctrl+I)"
              className={`h-8 min-w-8 rounded px-2 text-base font-semibold italic ${activeFormats.italic ? 'bg-slate-300 text-slate-900' : 'text-slate-700 hover:bg-slate-200'}`}
          >
            I
          </button>
          <button
              type="button"
              onMouseDown={preventToolbarFocus}
              onClick={() => runCommand('underline')}
              title="밑줄 (Ctrl+U)"
              className={`h-8 min-w-8 rounded px-2 text-base font-semibold underline underline-offset-2 ${activeFormats.underline ? 'bg-slate-300 text-slate-900' : 'text-slate-700 hover:bg-slate-200'}`}
          >
            U
          </button>
          <button
              type="button"
              onMouseDown={preventToolbarFocus}
              onClick={() => setShowColorPalette((prev) => !prev)}
              title="글씨 색상 선택"
              aria-label="글씨 색상 선택"
              aria-expanded={showColorPalette}
              className={`flex h-8 min-w-8 items-center justify-center gap-1 rounded px-1.5 text-slate-700 hover:bg-slate-200 ${showColorPalette ? 'bg-slate-300 text-slate-900' : ''}`}
          >
            <span className="relative pb-1 text-base font-semibold leading-none">
              A
              <span aria-hidden="true" className="absolute inset-x-0 bottom-0 h-0.5 bg-current" />
            </span>
            <svg aria-hidden="true" viewBox="0 0 10 6" className="h-1.5 w-2.5 fill-current">
              <path d="M0 0h10L5 6z" />
            </svg>
          </button>
          <button
              type="button"
              onMouseDown={preventToolbarFocus}
              onClick={() => imageInputRef.current?.click()}
              title="이미지 (Ctrl+Alt+I)"
              aria-label="이미지 삽입"
              className="flex h-8 min-w-8 items-center justify-center rounded px-2 text-slate-700 hover:bg-slate-200"
          >
            <svg
                aria-hidden="true"
                viewBox="0 0 24 24"
                fill="none"
                className="h-4 w-4"
            >
              <rect x="3.5" y="4.5" width="17" height="15" rx="2" stroke="currentColor" strokeWidth="1.8" />
              <circle cx="9" cy="9.5" r="1.5" fill="currentColor" />
              <path d="m5.5 17 4.25-4.5 2.75 2.75 2.25-2.25 3.75 4" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" />
            </svg>
          </button>
          {showColorPalette && (
            <div className="absolute left-2 top-11 z-30 flex gap-4 rounded-lg border border-slate-300 bg-white p-3 shadow-xl">
              {[
                { label: '글씨 색상', command: 'foreColor' },
              ].map((group) => (
                <div key={group.command}>
                  <div className="mb-2 text-xs font-semibold text-slate-600">{group.label}</div>
                  <div className="grid grid-cols-8 gap-0.5">
                    {COLOR_PALETTE.map((color) => (
                      <button
                        key={`${group.command}-${color}`}
                        type="button"
                        onMouseDown={preventToolbarFocus}
                        onClick={() => applyColor(group.command, color)}
                        title={`${group.label} ${color}`}
                        className="h-4 w-4 border border-black/5"
                        style={{ backgroundColor: color }}
                      />
                    ))}
                  </div>
                </div>
              ))}
            </div>
          )}
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

        <div className="relative bg-white">
          {isContentEmpty(value) && (
              <div className="pointer-events-none absolute left-6 top-5 text-[15px] text-slate-400">
                본문을 입력하거나 이미지를 삽입하세요.
              </div>
          )}
          <div
              ref={editorRef}
              contentEditable
              suppressContentEditableWarning
              onClick={(event) => event.currentTarget.focus({ preventScroll: true })}
              onInput={syncContent}
              onKeyDown={handleKeyDown}
              onMouseDown={handleSelectionStart}
              onMouseMove={handleSelectionMove}
              onMouseUp={handleSelectionEnd}
              onSelect={keepDraggedSelectionUnformatted}
              onKeyUp={saveSelection}
              onPaste={handlePaste}
              onDragStart={(event) => event.preventDefault()}
              onDragOver={handleDragOver}
              onDragLeave={handleDragLeave}
              onDrop={handleDrop}
              spellCheck="true"
              className={`min-h-[420px] max-h-[55vh] w-full cursor-text overflow-y-auto whitespace-pre-wrap break-words rounded-b-xl border px-6 py-5 text-[10pt] leading-7 text-slate-800 outline-none selection:bg-sky-100 [&_img]:my-4 [&_img]:h-auto [&_img]:max-w-full [&_img]:rounded-lg [&_img]:object-contain ${
                  dragging
                      ? 'border-slate-400 bg-sky-50/60'
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
                                               initialValues = null,
                                               mode = 'create',
                                               lockAuthor = false,
                                             }) {
  const [form, setForm] = useState(emptyForm);
  const [files, setFiles] = useState([]);
  const [removedAttachmentIds, setRemovedAttachmentIds] = useState([]);

  const selectedCount = useMemo(() => form.infraTypes.length, [form.infraTypes]);

  useEffect(() => {
    if (!open) {
      return;
    }

    setForm(initialValues ? {
      title: initialValues.title || '',
      customerName: initialValues.customerName || '',
      authorName: initialValues.authorName || '',
      content: initialValues.content || '',
      infraTypes: initialValues.infraTypes || [],
    } : emptyForm);
    setFiles([]);
    setRemovedAttachmentIds([]);
  }, [open, initialValues]);

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

  const selectFiles = (fileList) => {
    const incomingFiles = Array.from(fileList || []);
    const existingNames = new Set(
        (initialValues?.attachments || [])
            .filter((file) => !removedAttachmentIds.includes(file.id))
            .map((file) => file.originalFileName.toLowerCase())
    );
    const nextFiles = [...files];
    const selectedNames = new Set(nextFiles.map((file) => file.name.toLowerCase()));
    const skippedNames = [];

    incomingFiles.forEach((file) => {
      const normalizedName = file.name.toLowerCase();
      if (existingNames.has(normalizedName) || selectedNames.has(normalizedName)) {
        skippedNames.push(file.name);
        return;
      }
      selectedNames.add(normalizedName);
      nextFiles.push(file);
    });

    setFiles(nextFiles);
    if (skippedNames.length > 0) {
      alert(`이미 등록되었거나 선택된 파일은 제외했습니다.\n${skippedNames.join('\n')}`);
    }
  };

  const resetForm = () => {
    setForm(initialValues ? {
      title: initialValues.title || '',
      customerName: initialValues.customerName || '',
      authorName: initialValues.authorName || '',
      content: initialValues.content || '',
      infraTypes: initialValues.infraTypes || [],
    } : emptyForm);
    setFiles([]);
    setRemovedAttachmentIds([]);
  };

  const submit = async (e) => {
    e.preventDefault();

    if (!form.title.trim()) {
      alert('제목을 입력하세요.');
      return;
    }

    if (!form.authorName.trim()) {
      alert('작성자를 입력하세요.');
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
      removedAttachmentIds,
    });

    resetForm();
  };

  return (
      <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/40 px-4">
        <div className="flex max-h-[92vh] w-full max-w-[1180px] flex-col overflow-hidden rounded-2xl bg-white shadow-xl">
          {/* 상단 헤더 */}
          <div className="flex shrink-0 items-center justify-between border-b border-slate-200 px-6 py-4">
            <div>
              <h2 className="text-xl font-semibold text-slate-900">지식공유 {mode === 'edit' ? '수정' : '등록'}</h2>
              <p className="mt-1 text-sm text-slate-500">
                운영 지식, 장애 처리 방법, 점검 절차 등을 {mode === 'edit' ? '수정합니다.' : '등록합니다.'}
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
            <div className="grid grid-cols-1 gap-4 px-6 py-5">
              {/* 왼쪽: 기본 정보 + 내용 */}
              <div className="space-y-4">
                <div className="grid grid-cols-1 gap-4 md:grid-cols-2">
                  <Field label="제목">
                    <input
                        value={form.title}
                        onChange={(e) => changeValue('title', e.target.value)}
                        className="w-full rounded-xl border border-slate-300 px-3 py-2.5 text-sm outline-none focus:border-slate-500"
                        placeholder="Ex) DB 접속 오류 처리 방법"
                    />
                  </Field>

                  <Field label="고객사">
                    <input
                        value={form.customerName}
                        onChange={(e) => changeValue('customerName', e.target.value)}
                        className="w-full rounded-xl border border-slate-300 px-3 py-2.5 text-sm outline-none focus:border-slate-500"
                        placeholder="Ex) A고객사"
                    />
                  </Field>

                  <Field label="작성자">
                    <input
                        value={form.authorName}
                        onChange={(e) => changeValue('authorName', e.target.value)}
                        readOnly={lockAuthor}
                        className={`w-full rounded-xl border px-3 py-2.5 text-sm outline-none ${lockAuthor ? 'cursor-not-allowed border-slate-200 bg-slate-100 text-slate-600' : 'border-slate-300 focus:border-slate-500'}`}
                        placeholder="Ex) 홍길동"
                    />
                  </Field>

                  <Field label="첨부파일">
                    <input
                        type="file"
                        multiple
                        onChange={(e) => {
                          selectFiles(e.target.files);
                          e.target.value = '';
                        }}
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

                {mode === 'edit' && initialValues?.attachments?.length > 0 && (
                    <div className="rounded-xl border border-sky-100 bg-sky-50 px-4 py-3">
                      <div className="mb-2 text-sm font-medium text-slate-700">기존 첨부파일</div>
                      <div className="space-y-1">
                        {initialValues.attachments.filter((file) => !removedAttachmentIds.includes(file.id)).map((file) => (
                            <div key={file.id} className="flex items-center justify-between gap-3 rounded-lg bg-white px-3 py-2 text-sm text-slate-600">
                              <span className="min-w-0 truncate">{file.originalFileName}</span>
                              <button
                                type="button"
                                onClick={() => setRemovedAttachmentIds((prev) => [...prev, file.id])}
                                className="shrink-0 rounded-md border border-red-200 px-2 py-1 text-xs font-semibold text-red-600 hover:bg-red-50"
                              >
                                삭제
                              </button>
                            </div>
                        ))}
                        {initialValues.attachments.every((file) => removedAttachmentIds.includes(file.id)) && (
                          <div className="py-2 text-sm text-slate-500">모든 기존 첨부파일이 삭제 대상으로 선택되었습니다.</div>
                        )}
                      </div>
                      <p className="mt-2 text-xs text-slate-500">삭제 대상으로 선택한 파일은 수정 완료 시 삭제됩니다.</p>
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
              <aside className="rounded-xl border border-slate-200 bg-slate-50 px-3 py-2.5">
                <div className="mb-2 flex items-center justify-between gap-2">
                  <div>
                    <h3 className="text-sm font-semibold text-slate-900">인프라</h3>
                    <p className="mt-0.5 text-[11px] text-slate-500">
                      {mode === 'edit' ? '수정' : '등록'}은 다중 선택입니다.
                    </p>
                  </div>

                  <span className="rounded-full bg-white px-2 py-0.5 text-[11px] font-medium text-slate-600">
                  {selectedCount}개 선택
                </span>
                </div>

                <div className="overflow-x-auto pb-1">
                  <div className="grid min-w-max grid-flow-col grid-rows-2 gap-1">
                    {infraOptions.map((infraType) => {
                      const checked = form.infraTypes.includes(infraType);

                      return (
                          <label
                              key={infraType}
                              className={`flex shrink-0 cursor-pointer items-center gap-1.5 rounded-lg border px-2 py-1.5 text-xs transition ${
                                  checked
                                      ? 'border-slate-900 bg-white text-slate-900'
                                      : 'border-slate-200 bg-white text-slate-600 hover:border-slate-300'
                              }`}
                          >
                            <input
                                type="checkbox"
                                checked={checked}
                                onChange={() => toggleInfra(infraType)}
                                className="h-3.5 w-3.5 shrink-0"
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
                {saving ? `${mode === 'edit' ? '수정' : '등록'} 중...` : mode === 'edit' ? '수정 완료' : '등록'}
              </button>
            </div>
          </form>
        </div>
      </div>
  );
}
