'use client';
import { createContext, useContext } from 'react';

// Auth 연동 전: local_user 고정
// Google Sign-In 연동 시 여기를 교체
const USER_ID = 'local_user';

interface AuthCtx {
  uid: string;
  loading: boolean;
}

const AuthContext = createContext<AuthCtx>({ uid: USER_ID, loading: false });

export function AuthProvider({ children }: { children: React.ReactNode }) {
  return (
    <AuthContext.Provider value={{ uid: USER_ID, loading: false }}>
      {children}
    </AuthContext.Provider>
  );
}

export const useAuth = () => useContext(AuthContext);
