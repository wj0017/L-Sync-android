'use client';
import { useAuth } from '@/components/AuthProvider';

export default function BiblePage() {
  const { loading } = useAuth();

  if (loading) return (
    <div className="flex items-center justify-center min-h-screen">
      <div className="w-6 h-6 border-2 border-ls-blue border-t-transparent rounded-full animate-spin" />
    </div>
  );

  return (
    <div className="px-5 pt-8">
      <p className="text-[11px] font-medium tracking-[0.12em] text-ls-fg3 uppercase mb-1">성경</p>
      <h1 className="text-[30px] font-semibold tracking-[-0.035em] mb-6">통독</h1>

      <div className="bg-ls-card border border-ls-hair rounded-[14px] px-4 py-6 text-center">
        <p className="text-[40px] mb-3">📖</p>
        <p className="text-[15px] font-medium text-ls-fg mb-2">성경 통독은 앱에서 진행하세요</p>
        <p className="text-[13px] text-ls-fg2 leading-relaxed">
          통독 진행 현황, 챕터 체크, 설정 변경은<br />L·SYNC 앱에서 사용할 수 있습니다.
        </p>
        <p className="text-[11px] text-ls-fg3 mt-4">웹 통독 연동은 추후 업데이트 예정</p>
      </div>
    </div>
  );
}
