import type { Metadata } from 'next';
import { Instrument_Serif } from 'next/font/google';
import { AuthProvider } from '@/components/AuthProvider';
import { BottomNav } from '@/components/BottomNav';
import './globals.css';

const instrumentSerif = Instrument_Serif({
  weight: '400',
  style: 'italic',
  subsets: ['latin'],
  variable: '--font-instrument-serif',
});

export const metadata: Metadata = {
  title: 'L·SYNC',
  description: '개인 일정·가계부·성경 통독 관리',
};

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="ko" className={instrumentSerif.variable}>
      <body className="bg-ls-bg text-ls-fg font-sans">
        <AuthProvider>
          <div className="mx-auto max-w-[480px] min-h-screen relative">
            <main className="pb-20">{children}</main>
            <BottomNav />
          </div>
        </AuthProvider>
      </body>
    </html>
  );
}
