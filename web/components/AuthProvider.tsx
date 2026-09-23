'use client';
import { createContext, useCallback, useContext, useEffect, useState } from 'react';
import { GoogleAuthProvider, onAuthStateChanged, signInWithPopup, signOut, type User } from 'firebase/auth';
import { auth } from '@/lib/firebase';

interface AuthCtx {
  uid: string;
  loading: false;
  runWrite: (action: () => Promise<unknown>) => Promise<boolean>;
  reportReadError: (source: string, message: string | null) => void;
}

const AuthContext = createContext<AuthCtx | null>(null);

function errorMessage(error: unknown): string {
  const code = typeof error === 'object' && error !== null && 'code' in error
    ? String(error.code)
    : '';
  if (code === 'permission-denied') return '데이터 접근 권한이 없습니다. 다시 로그인해 주세요.';
  if (code === 'auth/popup-blocked') return '로그인 팝업이 차단되었습니다. 팝업을 허용한 뒤 다시 시도해 주세요.';
  if (code === 'auth/unauthorized-domain') return '이 주소는 Firebase 로그인 승인 도메인에 등록되지 않았습니다.';
  if (code === 'auth/operation-not-allowed') return 'Firebase에서 Google 로그인이 활성화되지 않았습니다.';
  return '요청을 완료하지 못했습니다. 연결 상태를 확인하고 다시 시도해 주세요.';
}

export function AuthProvider({ children }: { children: React.ReactNode }) {
  const [user, setUser] = useState<User | null>(null);
  const [checking, setChecking] = useState(true);
  const [busy, setBusy] = useState(false);
  const [authError, setAuthError] = useState<string | null>(null);

  useEffect(() => onAuthStateChanged(auth, nextUser => {
    setUser(nextUser);
    setChecking(false);
  }, error => {
    setAuthError(errorMessage(error));
    setChecking(false);
  }), []);

  async function handleSignIn() {
    setBusy(true);
    setAuthError(null);
    try {
      await signInWithPopup(auth, new GoogleAuthProvider());
    } catch (error) {
      const code = typeof error === 'object' && error !== null && 'code' in error
        ? String(error.code)
        : '';
      if (code !== 'auth/popup-closed-by-user' && code !== 'auth/cancelled-popup-request') {
        setAuthError(errorMessage(error));
      }
    } finally {
      setBusy(false);
    }
  }

  async function handleSignOut() {
    setBusy(true);
    setAuthError(null);
    try {
      await signOut(auth);
    } catch (error) {
      setAuthError(errorMessage(error));
    } finally {
      setBusy(false);
    }
  }

  if (checking) {
    return <div role="status" className="min-h-screen flex items-center justify-center text-ls-fg2">로그인 확인 중…</div>;
  }

  if (!user) {
    return (
      <div className="mx-auto max-w-[480px] min-h-screen flex flex-col items-center justify-center px-8 text-center">
        <h1 className="text-[32px] font-semibold tracking-wide">L·SYNC</h1>
        <p className="text-[14px] text-ls-fg2 mt-3 mb-10">앱에서 사용하는 Google 계정으로 로그인하세요.</p>
        <button type="button" onClick={handleSignIn} disabled={busy}
          className="w-full rounded-xl bg-ls-blue px-5 py-3 text-[14px] font-medium text-white disabled:opacity-50">
          {busy ? '로그인 중…' : 'Google로 로그인'}
        </button>
        {authError && <p role="alert" className="mt-4 text-[13px] text-ls-red">{authError}</p>}
      </div>
    );
  }

  return <AuthenticatedApp key={user.uid} user={user} onSignOut={handleSignOut} busy={busy} authError={authError}>{children}</AuthenticatedApp>;
}

function AuthenticatedApp({ user, children, onSignOut, busy, authError }: {
  user: User;
  children: React.ReactNode;
  onSignOut: () => Promise<void>;
  busy: boolean;
  authError: string | null;
}) {
  const [readErrors, setReadErrors] = useState<Record<string, string>>({});
  const [writeError, setWriteError] = useState<string | null>(null);

  const reportReadError = useCallback((source: string, message: string | null) => {
    setReadErrors(previous => {
      if (message === null && previous[source] === undefined) return previous;
      const next = { ...previous };
      if (message === null) delete next[source];
      else next[source] = message;
      return next;
    });
  }, []);

  const runWrite = useCallback(async (action: () => Promise<unknown>) => {
    if (auth.currentUser?.uid !== user.uid) return false;
    setWriteError(null);
    try {
      await action();
      return auth.currentUser?.uid === user.uid;
    } catch (error) {
      if (auth.currentUser?.uid === user.uid) setWriteError(errorMessage(error));
      return false;
    }
  }, [user.uid]);

  const readError = Object.values(readErrors)[0];
  return (
    <AuthContext.Provider value={{ uid: user.uid, loading: false, runWrite, reportReadError }}>
      <div className="mx-auto max-w-[480px] min-h-screen relative">
        <div className="flex items-center justify-end gap-3 px-5 pt-3 text-[11px] text-ls-fg2">
          <span className="truncate">{user.email}</span>
          <button type="button" onClick={onSignOut} disabled={busy} className="shrink-0 text-ls-fg underline disabled:opacity-50">로그아웃</button>
        </div>
        {(authError || readError || writeError) && (
          <div role="alert" className="mx-5 mt-3 rounded-lg border border-ls-red px-3 py-2 text-[12px] text-ls-red">
            {authError || readError || writeError}
          </div>
        )}
        {children}
      </div>
    </AuthContext.Provider>
  );
}

export function useAuth(): AuthCtx {
  const context = useContext(AuthContext);
  if (!context) throw new Error('useAuth requires an authenticated user');
  return context;
}

export function readErrorMessage(error: unknown): string {
  return errorMessage(error);
}
