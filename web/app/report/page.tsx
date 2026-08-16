'use client';
import { useState } from 'react';
import { useAuth } from '@/components/AuthProvider';
import { useReport } from '@/hooks/useReport';
import { CategorySlice } from '@/lib/finance';

const KO_MONTHS = ['1월','2월','3월','4월','5월','6월','7월','8월','9월','10월','11월','12월'];

export default function ReportPage() {
  const { uid, loading } = useAuth();
  const today = new Date();
  const curYear = today.getFullYear();
  const curMonth = today.getMonth() + 1;

  const [year,  setYear]  = useState(curYear);
  const [month, setMonth] = useState(curMonth);

  const report = useReport(uid, year, month);

  // 미래 월은 데이터가 없으므로 현재 월을 넘어가지 못하게 가드(앱 ReportViewModel.nextMonth와 동일).
  const canGoNext = year < curYear || (year === curYear && month < curMonth);

  function prevMonth() {
    if (month === 1) { setYear(y => y - 1); setMonth(12); }
    else setMonth(m => m - 1);
  }
  function nextMonth() {
    if (!canGoNext) return;
    if (month === 12) { setYear(y => y + 1); setMonth(1); }
    else setMonth(m => m + 1);
  }

  if (loading) return <LoadingSpinner />;

  const rate = report.todoTotal === 0 ? 0 : report.todoCompleted / report.todoTotal;
  const balance = report.income - report.expense;

  return (
    <div className="pt-8">
      {/* 헤더 */}
      <div className="px-5 mb-5">
        <p className="text-[11px] font-medium tracking-[0.12em] text-ls-fg3 uppercase">{year}</p>
        <div className="flex items-center justify-between">
          <h1 className="text-[30px] font-semibold tracking-[-0.035em]">{KO_MONTHS[month - 1]} 리포트</h1>
          <div className="flex gap-2">
            <button onClick={prevMonth}
              className="w-[38px] h-[38px] bg-ls-card border border-ls-hair rounded-full flex items-center justify-center text-ls-fg">‹</button>
            <button onClick={nextMonth} disabled={!canGoNext}
              className={`w-[38px] h-[38px] bg-ls-card border border-ls-hair rounded-full flex items-center justify-center ${
                canGoNext ? 'text-ls-fg' : 'text-ls-muted'
              }`}>›</button>
          </div>
        </div>
      </div>

      <div className="px-5 flex flex-col gap-2">
        {/* 할 일 완료율 */}
        <SectionHeader>할 일</SectionHeader>
        <Card>
          {report.todoTotal === 0 ? (
            <EmptyHint>마감일 있는 할 일 없음</EmptyHint>
          ) : (
            <div className="flex items-center gap-5">
              <CompletionRing rate={rate} />
              <div>
                <p className="flex items-baseline">
                  <span className="text-[30px] font-semibold tracking-[-0.035em] leading-none">{report.todoCompleted}</span>
                  <span className="text-[14px] font-medium text-ls-fg2 ml-1">/ {report.todoTotal} 완료</span>
                </p>
                <p className="text-[11px] font-medium text-ls-fg3 mt-1.5">마감일 있는 할 일 기준</p>
              </div>
            </div>
          )}
        </Card>

        {/* 일정 발생 수 */}
        <SectionHeader>일정</SectionHeader>
        <Card>
          <p className="flex items-baseline">
            <span className="text-[36px] font-semibold tracking-[-0.035em] leading-none">{report.eventCount.toLocaleString()}</span>
            <span className="text-[15px] font-medium text-ls-fg2 ml-1">건</span>
          </p>
          <p className="text-[11px] font-medium text-ls-fg3 mt-1.5">이달 일정 (반복 발생 포함)</p>
        </Card>

        {/* 가계부 */}
        <SectionHeader>가계부</SectionHeader>
        <Card>
          {/* 순지출 — 지출은 중립색(경고색 미사용), 기호만 InstrumentSerif Italic */}
          <p className="flex items-baseline">
            <span className="font-[var(--font-instrument-serif)] italic text-[26px] text-ls-fg2 mr-0.5">−₩</span>
            <span className="text-[32px] font-semibold tracking-[-0.035em] leading-none">{report.expense.toLocaleString()}</span>
          </p>
          <p className="text-[11px] font-medium text-ls-fg3 mt-1.5">이달 순지출</p>

          <div className="h-px bg-ls-line my-4" />

          <div className="flex">
            <div className="flex-1">
              <p className="text-[10px] font-medium tracking-[0.12em] text-ls-fg3 uppercase mb-1">수입</p>
              <p className="text-[15px] font-semibold text-ls-green">+₩{report.income.toLocaleString()}</p>
            </div>
            <div className="w-px bg-ls-line" />
            <div className="flex-1 pl-4">
              <p className="text-[10px] font-medium tracking-[0.12em] text-ls-fg3 uppercase mb-1">잔액</p>
              <p className={`text-[15px] font-semibold ${balance >= 0 ? 'text-ls-fg' : 'text-ls-fg2'}`}>
                {balance >= 0 ? '+' : '−'}₩{Math.abs(balance).toLocaleString()}
              </p>
            </div>
          </div>
        </Card>

        {/* 상위 지출 카테고리 */}
        <SectionHeader>카테고리 상위</SectionHeader>
        <Card>
          {report.topCategories.length === 0 ? (
            <EmptyHint>표시할 지출이 없습니다</EmptyHint>
          ) : (
            <CategoryBars slices={report.topCategories} />
          )}
        </Card>

        {/* 통독 — 웹에는 데이터가 없다(로컬 전용). 수치를 만들지 않고 안내만 한다. */}
        <SectionHeader>성경 통독</SectionHeader>
        <Card>
          <p className="text-[14px] font-medium text-ls-fg mb-1">통독 현황은 앱에서 확인하세요</p>
          <p className="text-[12px] text-ls-fg2 leading-relaxed">
            통독 진행 기록은 기기에만 저장되어 웹 리포트에는 집계되지 않습니다.
          </p>
        </Card>
      </div>
    </div>
  );
}

function SectionHeader({ children }: { children: React.ReactNode }) {
  return (
    <p className="text-[11px] font-medium tracking-[0.12em] text-ls-fg3 uppercase mt-3 mb-0.5">{children}</p>
  );
}

function Card({ children }: { children: React.ReactNode }) {
  return (
    <div className="bg-ls-card border border-ls-hair rounded-[14px] px-[18px] py-[18px]">{children}</div>
  );
}

function EmptyHint({ children }: { children: React.ReactNode }) {
  return <p className="text-[13px] text-ls-muted text-center py-4">{children}</p>;
}

// 완료율 링 — 차트 라이브러리 미사용, 인라인 SVG 직접 드로잉(앱 Canvas 링과 동일 톤).
function CompletionRing({ rate }: { rate: number }) {
  const clamped = Math.min(1, Math.max(0, rate));
  const percent = Math.round(clamped * 100);
  const r = 30;
  const circumference = 2 * Math.PI * r;

  return (
    <div className="relative w-[72px] h-[72px] shrink-0">
      <svg width={72} height={72} viewBox="0 0 72 72" className="-rotate-90">
        <circle cx={36} cy={36} r={r} fill="none" stroke="var(--color-ls-line)" strokeWidth={7} />
        {percent > 0 && (
          <circle
            cx={36} cy={36} r={r} fill="none"
            stroke="var(--color-ls-blue)" strokeWidth={7} strokeLinecap="round"
            strokeDasharray={circumference}
            strokeDashoffset={circumference * (1 - clamped)}
          />
        )}
      </svg>
      <span className="absolute inset-0 flex items-center justify-center text-[15px] font-semibold tracking-[-0.02em]">
        {percent}%
      </span>
    </div>
  );
}

// 카테고리 비율 막대 — CSS 폭 비율. 지출이라 경고색(red) 미사용.
function CategoryBars({ slices }: { slices: CategorySlice[] }) {
  const maxAmount = Math.max(...slices.map(s => s.amount));

  return (
    <div className="flex flex-col gap-3.5">
      {slices.map(slice => (
        <div key={slice.category}>
          <div className="flex items-center justify-between mb-1.5">
            <span className="text-[13px] font-medium truncate">{slice.category || '미분류'}</span>
            <span className="text-[13px] font-semibold tracking-[-0.01em] shrink-0 ml-2">
              −₩{slice.amount.toLocaleString()}
            </span>
          </div>
          <div className="h-1.5 rounded-full bg-ls-line overflow-hidden">
            <div
              className="h-full rounded-full bg-ls-fg2"
              style={{ width: `${maxAmount > 0 ? (slice.amount / maxAmount) * 100 : 0}%` }}
            />
          </div>
        </div>
      ))}
    </div>
  );
}

function LoadingSpinner() {
  return (
    <div className="flex items-center justify-center min-h-screen">
      <div className="w-6 h-6 border-2 border-ls-blue border-t-transparent rounded-full animate-spin" />
    </div>
  );
}
