import React, { useEffect, useRef, useState } from 'react';
import { API_BASE } from '../constants/patchHistoryOptions';
import CreateKnowledgeModal from '../components/modal/CreateKnowledgeModal';

function formatDateTime(value) {
    if (!value) {
        return '-';
    }

    return String(value).replace('T', ' ').slice(0, 16);
}

function hasHtmlContent(value) {
    return /<\/?[a-z][\s\S]*>/i.test(value || '');
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
            node.getAttributeNames().forEach((name) => node.removeAttribute(name));
            if (/^(#[0-9a-f]{3,8}|rgba?\([\d\s,.%]+\))$/i.test(color)) {
                node.style.color = color;
            }
            if (/^(#[0-9a-f]{3,8}|rgba?\([\d\s,.%]+\))$/i.test(backgroundColor)) {
                node.style.backgroundColor = backgroundColor;
            }
            return;
        }

        node.getAttributeNames().forEach((name) => node.removeAttribute(name));
    });

    return template.innerHTML;
}

function InfoBox({ label, value }) {
    return (
        <div className="rounded-lg bg-slate-50 px-3 py-3">
            <div className="text-[11px] font-semibold uppercase tracking-wide text-slate-500">
                {label}
            </div>
            <div className="mt-1 break-words text-sm text-slate-900">
                {value || '-'}
            </div>
        </div>
    );
}

function DetailBlock({ title, value }) {
    const isHtml = hasHtmlContent(value);
    const sanitizedHtml = isHtml ? sanitizeContentHtml(value) : '';

    return (
        <section className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm">
            <div className="mb-3 text-sm font-semibold text-slate-700">
                {title}
            </div>
            {isHtml ? (
                <div
                    className="min-h-14 rounded-xl border border-slate-200 bg-slate-50 px-4 py-3 text-sm leading-6 text-slate-800 [&_img]:my-3 [&_img]:max-w-full [&_img]:rounded-lg"
                    dangerouslySetInnerHTML={{ __html: sanitizedHtml }}
                />
            ) : (
                <div className="min-h-14 whitespace-pre-wrap rounded-xl border border-slate-200 bg-slate-50 px-4 py-3 text-sm leading-6 text-slate-800">
                    {value || '-'}
                </div>
            )}
        </section>
    );
}

export default function KnowledgeDetailWindow({ headerAction = null }) {
    const detailLoadedRef = useRef(false);
    const [knowledge, setKnowledge] = useState(null);
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState('');
    const [isEditOpen, setIsEditOpen] = useState(false);
    const [saving, setSaving] = useState(false);
    const [deleting, setDeleting] = useState(false);
    const [message, setMessage] = useState('');

    const params = new URLSearchParams(window.location.search);
    const id = params.get('id');
    let currentAccountId = null;
    let currentAccountRole = null;
    try {
        const currentUser = JSON.parse(sessionStorage.getItem('tc-bank:user'));
        currentAccountId = currentUser?.id ?? null;
        currentAccountRole = currentUser?.role ?? null;
    } catch {
        currentAccountId = null;
    }
    const handleClose = () => {
        if (params.get('embedded') === '1' && window.parent && window.parent !== window) {
            window.parent.postMessage(
                {
                    type: 'issue-system:close-split-screen',
                    index: Number(params.get('splitIndex')),
                },
                window.location.origin
            );
            return;
        }

        window.close();
    };

    const fetchDetail = async () => {
        if (!id) {
            setError('지식공유 ID가 없습니다.');
            return;
        }

        setLoading(true);
        setError('');

        try {
            const res = await fetch(`${API_BASE}/api/knowledge-shares/${id}`);
            const result = await res.json();

            if (!res.ok || !result.success) {
                throw new Error(result.message || '지식공유 상세 조회에 실패했습니다.');
            }

            setKnowledge(result.data);
        } catch (e) {
            setError(e.message || '지식공유 상세 조회 중 오류가 발생했습니다.');
        } finally {
            setLoading(false);
        }
    };

    useEffect(() => {
        if (detailLoadedRef.current) {
            return;
        }
        detailLoadedRef.current = true;
        fetchDetail();
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, []);

    const downloadUrl = (attachmentId) => {
        return `${API_BASE}/api/knowledge-shares/attachments/${attachmentId}/download`;
    };

    const handleUpdate = async (payload) => {
        setSaving(true);
        setError('');
        setMessage('');

        try {
            const formData = new FormData();
            const request = {
                title: payload.title,
                customerName: payload.customerName,
                authorName: payload.authorName,
                attachmentName: '',
                content: payload.content,
                infraTypes: payload.infraTypes,
            };

            formData.append('request', new Blob([JSON.stringify(request)], { type: 'application/json' }));
            payload.files.forEach((file) => formData.append('files', file));
            payload.removedAttachmentIds.forEach((attachmentId) => formData.append('deleteAttachmentIds', String(attachmentId)));

            const response = await fetch(`${API_BASE}/api/knowledge-shares/${id}`, {
                method: 'PUT',
                credentials: 'include',
                body: formData,
            });
            const result = await response.json();

            if (!response.ok || !result.success) {
                throw new Error(result.message || '지식공유 수정에 실패했습니다.');
            }

            setIsEditOpen(false);
            setMessage('지식공유가 수정되었습니다.');
            await fetchDetail();
        } catch (e) {
            setError(e.message || '지식공유 수정 중 오류가 발생했습니다.');
        } finally {
            setSaving(false);
        }
    };

    const handleDelete = async () => {
        if (!window.confirm('이 지식을 삭제하시겠습니까? 삭제된 내용은 복구할 수 없습니다.')) {
            return;
        }

        setDeleting(true);
        setError('');
        try {
            const response = await fetch(`${API_BASE}/api/knowledge-shares/${id}`, {
                method: 'DELETE',
                credentials: 'include',
            });
            const result = await response.json();
            if (!response.ok || !result.success) {
                throw new Error(result.message || '지식공유 삭제에 실패했습니다.');
            }
            handleClose();
        } catch (e) {
            setError(e.message || '지식공유 삭제 중 오류가 발생했습니다.');
        } finally {
            setDeleting(false);
        }
    };

    return (
        <div className="min-h-screen bg-slate-50 text-slate-900">
            <header className="sticky top-0 z-10 border-b border-sky-100 bg-sky-50 px-6 py-4 text-slate-900 shadow-sm">
                <div className="flex items-center justify-between">
                    <div>
                        <div className="text-sm text-sky-600">지식공유 DB</div>
                        <div className="mt-1 flex items-center gap-2">
                            <h1 className="text-xl font-semibold">지식공유 상세보기</h1>
                            {headerAction}
                        </div>
                    </div>

                    <div className="flex items-center gap-2">
                        {knowledge && (knowledge.createdByAccountId === currentAccountId || currentAccountRole === 'ADMIN') && (
                            <>
                                <button
                                    type="button"
                                    onClick={() => setIsEditOpen(true)}
                                    className="rounded-lg bg-sky-600 px-4 py-2 text-sm font-semibold text-white hover:bg-sky-700"
                                >
                                    수정
                                </button>
                                <button
                                    type="button"
                                    onClick={handleDelete}
                                    disabled={deleting}
                                    className="rounded-lg border border-red-200 bg-white px-4 py-2 text-sm font-semibold text-red-600 hover:bg-red-50 disabled:opacity-50"
                                >
                                    {deleting ? '삭제 중...' : '삭제'}
                                </button>
                            </>
                        )}
                        <button
                            type="button"
                            onClick={handleClose}
                            className="rounded-lg border border-sky-200 bg-white px-4 py-2 text-sm text-slate-600 hover:bg-sky-100 hover:text-sky-700"
                        >
                        창 닫기
                        </button>
                    </div>
                </div>
            </header>

            <main className="px-6 py-5">
                {message && (
                    <div className="mb-4 rounded-xl border border-emerald-200 bg-emerald-50 px-4 py-3 text-sm text-emerald-700">
                        {message}
                    </div>
                )}
                {loading ? (
                    <div className="rounded-2xl border border-dashed border-slate-300 bg-white px-4 py-10 text-center text-slate-500">
                        상세 정보를 불러오는 중입니다.
                    </div>
                ) : error ? (
                    <div className="rounded-2xl border border-red-200 bg-red-50 px-4 py-5 text-sm text-red-700">
                        {error}
                    </div>
                ) : !knowledge ? (
                    <div className="rounded-2xl border border-dashed border-slate-300 bg-white px-4 py-10 text-center text-slate-500">
                        상세 정보를 찾을 수 없습니다.
                    </div>
                ) : (
                    <div className="space-y-5">
                        <section className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm">
                            <div className="flex flex-wrap items-center gap-2">
                                <h2 className="text-2xl font-semibold text-slate-900">
                                    {knowledge.title}
                                </h2>

                                {(knowledge.infraTypes || []).map((infraType) => (
                                    <span
                                        key={infraType}
                                        className="inline-flex items-center rounded-full bg-slate-100 px-2.5 py-1 text-xs font-medium text-slate-700"
                                    >
                    {infraType}
                  </span>
                                ))}
                            </div>

                            <div className="mt-4 grid grid-cols-1 gap-2 sm:grid-cols-2 lg:grid-cols-4">
                                <InfoBox label="ID" value={knowledge.id} />
                                <InfoBox label="고객사" value={knowledge.customerName} />
                                <InfoBox label="작성자" value={knowledge.authorName} />
                                <InfoBox label="등록일" value={formatDateTime(knowledge.createdAt)} />
                            </div>
                        </section>

                        <DetailBlock title="내용" value={knowledge.content} />

                        <section className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm">
                            <div className="mb-3 text-sm font-semibold text-slate-700">
                                첨부파일
                            </div>

                            {knowledge.attachments && knowledge.attachments.length > 0 ? (
                                <div className="space-y-2">
                                    {knowledge.attachments.map((file) => (
                                        <a
                                            key={file.id}
                                            href={downloadUrl(file.id)}
                                            className="block rounded-xl border border-slate-200 bg-slate-50 px-4 py-3 text-sm font-medium text-slate-700 underline underline-offset-2 hover:bg-slate-100 hover:text-slate-900"
                                        >
                                            {file.originalFileName}
                                        </a>
                                    ))}
                                </div>
                            ) : (
                                <div className="rounded-xl border border-dashed border-slate-300 px-4 py-6 text-center text-sm text-slate-500">
                                    첨부파일이 없습니다.
                                </div>
                            )}
                        </section>
                    </div>
                )}
            </main>

            {isEditOpen && knowledge && (
                <CreateKnowledgeModal
                    open={isEditOpen}
                    saving={saving}
                    onClose={() => setIsEditOpen(false)}
                    onSubmit={handleUpdate}
                    initialValues={knowledge}
                    mode="edit"
                    lockAuthor
                />
            )}
        </div>
    );
}
