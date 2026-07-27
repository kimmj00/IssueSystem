import React, { useEffect, useState } from 'react';
import PageTitle from '../components/common/PageTitle';
import SectionCard from '../components/common/SectionCard';
import { API_BASE } from '../constants/issueOptions';

const PASSWORD_RULE = /^(?=.*[a-z])(?=.*[A-Z])(?=.*\d)(?=.*[^A-Za-z0-9])\S{8,20}$/;

function formatDateTime(value) {
  return value ? String(value).replace('T', ' ').slice(0, 16) : '-';
}

export default function AdminAccountsPage() {
  const [accounts, setAccounts] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [targetAccount, setTargetAccount] = useState(null);
  const [password, setPassword] = useState('');
  const [passwordConfirm, setPasswordConfirm] = useState('');
  const [showPassword, setShowPassword] = useState(false);
  const [showPasswordConfirm, setShowPasswordConfirm] = useState(false);
  const [saving, setSaving] = useState(false);
  const [modalError, setModalError] = useState('');

  useEffect(() => {
    fetch(`${API_BASE}/api/accounts`, { credentials: 'include' })
      .then(async (response) => {
        const result = await response.json();
        if (!response.ok || !result.success) throw new Error(result.message || '계정 조회에 실패했습니다.');
        setAccounts(result.data || []);
      })
      .catch((e) => setError(e.message))
      .finally(() => setLoading(false));
  }, []);

  const resetPasswordModal = () => {
    setTargetAccount(null);
    setPassword('');
    setPasswordConfirm('');
    setShowPassword(false);
    setShowPasswordConfirm(false);
    setModalError('');
  };

  const closePasswordModal = () => {
    if (!saving) resetPasswordModal();
  };

  const changePassword = async (event) => {
    event.preventDefault();
    setModalError('');

    if (!PASSWORD_RULE.test(password)) {
      setModalError('비밀번호 규칙을 확인해주세요.');
      return;
    }
    if (password !== passwordConfirm) {
      setModalError('비밀번호 확인이 일치하지 않습니다.');
      return;
    }

    setSaving(true);
    try {
      const response = await fetch(`${API_BASE}/api/accounts/${targetAccount.id}/password`, {
        method: 'PATCH',
        credentials: 'include',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ password }),
      });
      const result = await response.json();
      if (!response.ok || !result.success) {
        throw new Error(result.message || '비밀번호 변경에 실패했습니다.');
      }
      alert('비밀번호가 변경되었습니다.');
      resetPasswordModal();
    } catch (e) {
      setModalError(e.message);
    } finally {
      setSaving(false);
    }
  };

  return (
    <div className="space-y-5">
      <PageTitle title="계정 관리" description="현재까지 가입된 계정 정보를 확인합니다." />
      <SectionCard title={`가입 계정 / 전체 : ${accounts.length}개`}>
        {error && <div className="mb-4 rounded-xl border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700">{error}</div>}
        <div className="overflow-hidden rounded-2xl border border-slate-200">
          <table className="w-full divide-y divide-slate-200 text-center text-sm">
            <thead className="bg-slate-100">
              <tr>
                <th className="px-4 py-3">번호</th>
                <th className="px-4 py-3">ID</th>
                <th className="px-4 py-3">이름</th>
                <th className="px-4 py-3">권한</th>
                <th className="px-4 py-3">가입일</th>
                <th className="px-4 py-3 text-center">비밀번호 변경</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100 bg-white">
              {loading ? (
                <tr><td colSpan={6} className="px-4 py-8 text-center text-slate-500">불러오는 중...</td></tr>
              ) : accounts.map((account) => (
                <tr key={account.id}>
                  <td className="px-4 py-3">{account.id}</td>
                  <td className="px-4 py-3 font-semibold">{account.userId}</td>
                  <td className="px-4 py-3">{account.name}</td>
                  <td className="px-4 py-3">
                    <span className="rounded-full bg-slate-100 px-2.5 py-1 text-xs font-bold">
                      {account.role === 'ADMIN' ? '관리자' : '사용자'}
                    </span>
                  </td>
                  <td className="px-4 py-3">{formatDateTime(account.createdAt)}</td>
                  <td className="px-4 py-3 text-center">
                    {account.role === 'USER' ? (
                      <button
                        type="button"
                        onClick={() => setTargetAccount(account)}
                        className="rounded-lg border border-slate-300 bg-white px-3 py-1.5 text-xs font-semibold text-slate-700 hover:bg-slate-100"
                      >
                        변경
                      </button>
                    ) : <span className="text-slate-400">-</span>}
                  </td>
                </tr>
              ))}
              {!loading && !error && accounts.length === 0 && (
                <tr><td colSpan={6} className="px-4 py-8 text-center text-slate-500">가입된 계정이 없습니다.</td></tr>
              )}
            </tbody>
          </table>
        </div>
      </SectionCard>

      {targetAccount && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-950/45 px-4">
          <form onSubmit={changePassword} className="w-full max-w-md rounded-2xl bg-white p-6 shadow-2xl">
            <div className="mb-5 flex items-start justify-between gap-4">
              <div>
                <h2 className="text-lg font-bold text-slate-900">비밀번호 변경</h2>
                <p className="mt-1 text-sm text-slate-500">
                  {targetAccount.name} ({targetAccount.userId})
                </p>
              </div>
              <button type="button" onClick={closePasswordModal} className="text-xl text-slate-400 hover:text-slate-700">×</button>
            </div>

            {modalError && <div className="mb-4 rounded-lg bg-red-50 px-3 py-2 text-sm text-red-700">{modalError}</div>}

            <label className="block">
              <span className="mb-2 block text-sm font-semibold text-slate-700">새 비밀번호</span>
              <div className="relative">
                <input
                  type={showPassword ? 'text' : 'password'}
                  value={password}
                  onChange={(e) => setPassword(e.target.value)}
                  autoComplete="new-password"
                  className="w-full rounded-xl border border-slate-300 px-3 py-2.5 pr-11 text-sm outline-none focus:border-slate-500"
                />
                <button
                  type="button"
                  onClick={() => setShowPassword((current) => !current)}
                  aria-label={showPassword ? '비밀번호 숨기기' : '비밀번호 보기'}
                  title={showPassword ? '비밀번호 숨기기' : '비밀번호 보기'}
                  className="absolute inset-y-0 right-0 flex w-11 items-center justify-center text-slate-400 transition hover:text-sky-600"
                >
                  {showPassword ? (
                    <svg viewBox="0 0 24 24" className="h-5 w-5" fill="none" stroke="currentColor" strokeWidth="1.8">
                      <path d="M3 3l18 18" />
                      <path d="M10.6 10.7a2 2 0 002.7 2.7" />
                      <path d="M9.9 4.2A10.8 10.8 0 0112 4c5.5 0 9 5 9 5a15.7 15.7 0 01-2.1 2.5M6.6 6.6C4.3 8.1 3 10 3 10s3.5 5 9 5a10.7 10.7 0 004-.8" />
                    </svg>
                  ) : (
                    <svg viewBox="0 0 24 24" className="h-5 w-5" fill="none" stroke="currentColor" strokeWidth="1.8">
                      <path d="M3 12s3.5-5 9-5 9 5 9 5-3.5 5-9 5-9-5-9-5z" />
                      <circle cx="12" cy="12" r="2.5" />
                    </svg>
                  )}
                </button>
              </div>
              <span className="mt-2 block text-xs text-slate-500">
                ※ 8~20자, 영문 대·소문자, 숫자, 특수문자를 각각 1개 포함해야 합니다.
              </span>
            </label>

            <label className="mt-4 block">
              <span className="mb-2 block text-sm font-semibold text-slate-700">새 비밀번호 확인</span>
              <div className="relative">
                <input
                  type={showPasswordConfirm ? 'text' : 'password'}
                  value={passwordConfirm}
                  onChange={(e) => setPasswordConfirm(e.target.value)}
                  autoComplete="new-password"
                  className="w-full rounded-xl border border-slate-300 px-3 py-2.5 pr-11 text-sm outline-none focus:border-slate-500"
                />
                <button
                  type="button"
                  onClick={() => setShowPasswordConfirm((current) => !current)}
                  aria-label={showPasswordConfirm ? '비밀번호 확인 숨기기' : '비밀번호 확인 보기'}
                  title={showPasswordConfirm ? '비밀번호 확인 숨기기' : '비밀번호 확인 보기'}
                  className="absolute inset-y-0 right-0 flex w-11 items-center justify-center text-slate-400 transition hover:text-sky-600"
                >
                  {showPasswordConfirm ? (
                    <svg viewBox="0 0 24 24" className="h-5 w-5" fill="none" stroke="currentColor" strokeWidth="1.8">
                      <path d="M3 3l18 18" />
                      <path d="M10.6 10.7a2 2 0 002.7 2.7" />
                      <path d="M9.9 4.2A10.8 10.8 0 0112 4c5.5 0 9 5 9 5a15.7 15.7 0 01-2.1 2.5M6.6 6.6C4.3 8.1 3 10 3 10s3.5 5 9 5a10.7 10.7 0 004-.8" />
                    </svg>
                  ) : (
                    <svg viewBox="0 0 24 24" className="h-5 w-5" fill="none" stroke="currentColor" strokeWidth="1.8">
                      <path d="M3 12s3.5-5 9-5 9 5 9 5-3.5 5-9 5-9-5-9-5z" />
                      <circle cx="12" cy="12" r="2.5" />
                    </svg>
                  )}
                </button>
              </div>
            </label>

            <div className="mt-6 flex justify-end gap-2">
              <button type="button" onClick={closePasswordModal} disabled={saving} className="rounded-xl border border-slate-300 px-4 py-2 text-sm font-semibold text-slate-700">
                취소
              </button>
              <button type="submit" disabled={saving} className="rounded-xl bg-slate-900 px-4 py-2 text-sm font-semibold text-white disabled:bg-slate-400">
                {saving ? '확인 중...' : '확인'}
              </button>
            </div>
          </form>
        </div>
      )}
    </div>
  );
}
