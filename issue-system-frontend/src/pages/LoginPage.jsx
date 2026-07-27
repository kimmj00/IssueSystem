import React, { useState } from 'react';
import { API_BASE } from '../constants/issueOptions';

export const PASSWORD_RULE = /^(?=.*[a-z])(?=.*[A-Z])(?=.*\d)(?=.*[^A-Za-z0-9])\S{8,20}$/;
const SAVED_ID_KEY = 'tc-bank:saved-id';

export default function LoginPage({ onLogin }) {
  const [mode, setMode] = useState('login');
  const [form, setForm] = useState(() => ({ userId: localStorage.getItem(SAVED_ID_KEY) || '', password: '', name: '' }));
  const [rememberId, setRememberId] = useState(() => Boolean(localStorage.getItem(SAVED_ID_KEY)));
  const [showPassword, setShowPassword] = useState(false);
  const [error, setError] = useState('');
  const [message, setMessage] = useState('');
  const [saving, setSaving] = useState(false);

  const changeValue = (key, value) => {
    setForm((prev) => ({ ...prev, [key]: value }));
    setError('');
  };

  const changeMode = (nextMode) => {
    setMode(nextMode);
    setError('');
    setMessage('');
    setShowPassword(false);
    setForm({ userId: nextMode === 'login' ? localStorage.getItem(SAVED_ID_KEY) || '' : '', password: '', name: '' });
  };

  const submit = async (event) => {
    event.preventDefault();
    const userId = form.userId.trim();
    const name = form.name.trim();

    if (!userId || !form.password || (mode === 'signup' && !name)) {
      setError(mode === 'signup' ? 'ID, 비밀번호, 이름을 모두 입력해주세요.' : 'ID와 비밀번호를 입력해주세요.');
      return;
    }

    if (mode === 'signup' && !/^(?=.*[A-Za-z])(?=.*\d)[A-Za-z0-9]{4,20}$/.test(userId)) {
      setError('ID는 영문과 숫자를 포함하여 4~20자로 입력해주세요.');
      return;
    }

    if (mode === 'signup' && !PASSWORD_RULE.test(form.password)) {
      setError('비밀번호 규칙을 확인해주세요.');
      return;
    }

    setSaving(true);
    setError('');
    try {
      const response = await fetch(`${API_BASE}/api/accounts/${mode}`, {
        method: 'POST',
        credentials: 'include',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(mode === 'signup' ? { userId, password: form.password, name } : { userId, password: form.password }),
      });
      const result = await response.json();
      if (!response.ok || !result.success) {
        throw new Error(result.message || `${mode === 'signup' ? '회원가입' : '로그인'}에 실패했습니다.`);
      }

      if (mode === 'signup') {
        changeMode('login');
        setForm((prev) => ({ ...prev, userId }));
        setMessage('회원가입이 완료되었습니다. 로그인해주세요.');
        return;
      }
      if (rememberId) {
        localStorage.setItem(SAVED_ID_KEY, userId);
      } else {
        localStorage.removeItem(SAVED_ID_KEY);
      }
      onLogin(result.data);
    } catch (e) {
      setError(e.message || '요청 처리 중 오류가 발생했습니다.');
    } finally {
      setSaving(false);
    }
  };

  return (
    <main className="relative flex min-h-screen items-center justify-center overflow-hidden bg-slate-100 px-4 py-10 text-slate-900">
      <div className="absolute inset-0 bg-[radial-gradient(circle_at_top_left,_rgba(14,165,233,0.14),_transparent_38%),radial-gradient(circle_at_bottom_right,_rgba(99,102,241,0.10),_transparent_36%)]" />
      <img
        src="/logo.svg"
        alt="Brainz Company"
        className="absolute left-6 top-5 z-10 h-auto w-[126px] sm:left-10 sm:top-8"
      />
      <section className="relative w-full max-w-md overflow-hidden rounded-3xl border border-slate-200 bg-white shadow-xl shadow-slate-300/50">
        <div className="border-b border-sky-100 bg-gradient-to-r from-sky-50 to-indigo-50 px-8 py-7 text-slate-900">
          <div className="flex items-center gap-3">
            <div className="flex h-11 w-11 items-center justify-center rounded-2xl bg-sky-600 text-sm font-black text-white shadow-sm">TC</div>
            <h1 className="text-xl font-bold tracking-wide">TC Bank</h1>
          </div>
        </div>

        <form onSubmit={submit} className="space-y-5 px-8 py-8">
          <h2 className="text-2xl font-bold text-slate-950">{mode === 'signup' ? '회원가입' : '로그인'}</h2>

          {mode === 'signup' && (
            <label className="block">
              <span className="mb-2 block text-sm font-semibold text-slate-700">이름</span>
              <input value={form.name} onChange={(e) => changeValue('name', e.target.value)} autoComplete="name" className="h-11 w-full rounded-xl border border-slate-300 px-3.5 text-sm outline-none focus:border-sky-500 focus:ring-2 focus:ring-sky-100" />
            </label>
          )}

          <label className="block">
            <span className="mb-2 block text-sm font-semibold text-slate-700">ID</span>
            <input value={form.userId} onChange={(e) => changeValue('userId', e.target.value)} autoComplete="username" maxLength={20} className="h-11 w-full rounded-xl border border-slate-300 px-3.5 text-sm outline-none focus:border-sky-500 focus:ring-2 focus:ring-sky-100" />
            {mode === 'signup' && <span className="mt-1.5 block text-xs text-slate-500">※ 영문과 숫자를 포함하여 4~20자로 입력해주세요.</span>}
          </label>
          <label className="block">
            <span className="mb-2 block text-sm font-semibold text-slate-700">비밀번호</span>
            <div className="relative">
              <input type={showPassword ? 'text' : 'password'} value={form.password} onChange={(e) => changeValue('password', e.target.value)} autoComplete={mode === 'signup' ? 'new-password' : 'current-password'} className="h-11 w-full rounded-xl border border-slate-300 px-3.5 pr-11 text-sm outline-none focus:border-sky-500 focus:ring-2 focus:ring-sky-100" />
              <button
                type="button"
                onClick={() => setShowPassword((prev) => !prev)}
                aria-label={showPassword ? '비밀번호 숨기기' : '비밀번호 보기'}
                title={showPassword ? '비밀번호 숨기기' : '비밀번호 보기'}
                className="absolute inset-y-0 right-0 flex w-11 items-center justify-center text-slate-400 transition hover:text-sky-600"
              >
                {showPassword ? (
                  <svg viewBox="0 0 24 24" className="h-5 w-5" fill="none" stroke="currentColor" strokeWidth="1.8"><path d="M3 3l18 18" /><path d="M10.6 10.7a2 2 0 002.7 2.7" /><path d="M9.9 4.2A10.8 10.8 0 0112 4c5.5 0 9 5 9 5a15.7 15.7 0 01-2.1 2.5M6.6 6.6C4.3 8.1 3 10 3 10s3.5 5 9 5a10.7 10.7 0 004-.8" /></svg>
                ) : (
                  <svg viewBox="0 0 24 24" className="h-5 w-5" fill="none" stroke="currentColor" strokeWidth="1.8"><path d="M3 12s3.5-5 9-5 9 5 9 5-3.5 5-9 5-9-5-9-5z" /><circle cx="12" cy="12" r="2.5" /></svg>
                )}
              </button>
            </div>
            {mode === 'signup' && (
              <span className="mt-1.5 block text-xs leading-5 text-slate-500">
                ※ 8~20자, 영문 대·소문자, 숫자, 특수문자를 각각 1개 포함해야합니다.
              </span>
            )}
          </label>

          {mode === 'login' && (
            <label className="flex cursor-pointer items-center gap-2 text-sm text-slate-600">
              <input
                type="checkbox"
                checked={rememberId}
                onChange={(event) => setRememberId(event.target.checked)}
                className="h-4 w-4 rounded border-slate-300 text-sky-600 focus:ring-sky-500"
              />
              아이디 저장
            </label>
          )}

          {message && <div className="rounded-xl border border-emerald-200 bg-emerald-50 px-4 py-3 text-sm text-emerald-700">{message}</div>}
          {error && <div className="rounded-xl border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700">{error}</div>}

          <button disabled={saving} type="submit" className="h-11 w-full rounded-xl bg-sky-600 text-sm font-bold text-white shadow-sm transition hover:bg-sky-700 disabled:bg-slate-400">
            {saving ? '처리 중...' : mode === 'signup' ? '가입하기' : '로그인'}
          </button>
          <button type="button" onClick={() => changeMode(mode === 'signup' ? 'login' : 'signup')} className="w-full text-sm font-semibold text-slate-600 hover:text-slate-950">
            {mode === 'signup' ? '로그인으로 돌아가기' : '회원가입'}
          </button>
        </form>
      </section>
    </main>
  );
}
