import React, { useEffect, useState } from 'react';

const API_BASE =
    process.env.NODE_ENV === 'development'
        ? 'http://localhost:8080'
        : '';

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

export default function KnowledgeDetailWindow() {
    const [knowledge, setKnowledge] = useState(null);
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState('');

    const params = new URLSearchParams(window.location.search);
    const id = params.get('id');

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
        fetchDetail();
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, []);

    const downloadUrl = (attachmentId) => {
        return `${API_BASE}/api/knowledge-shares/attachments/${attachmentId}/download`;
    };

    return (
        <div className="min-h-screen bg-slate-50 text-slate-900">
            <header className="sticky top-0 z-10 border-b border-slate-200 bg-slate-950 px-6 py-4 text-white">
                <div className="flex items-center justify-between">
                    <div>
                        <div className="text-sm text-slate-300">지식공유 DB</div>
                        <h1 className="mt-1 text-xl font-semibold">지식공유 상세보기</h1>
                    </div>

                    <button
                        type="button"
                        onClick={() => window.close()}
                        className="rounded-lg border border-slate-600 px-4 py-2 text-sm text-white hover:bg-slate-800"
                    >
                        창 닫기
                    </button>
                </div>
            </header>

            <main className="px-6 py-5">
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
                                <InfoBox label="담당자" value={knowledge.authorName} />
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
        </div>
    );
}
