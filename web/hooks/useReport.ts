'use client';
import { useMemo } from 'react';
import { useEvents } from './useEvents';
import { useTodos } from './useTodos';
import { useFinance } from './useFinance';
import { CategorySlice, financeTotals, topExpenseCategories } from '@/lib/finance';
import { expandEvents } from '@/lib/recurrence';

// 월간 리포트 집계 — 앱 ReportViewModel과 1:1 대응해야 한다.
// 집계식(정산 규칙·반복 전개)은 여기서 다시 쓰지 않고 lib/finance·lib/recurrence를 호출한다.
// 통독(reading_plan)은 Firestore 미동기화라 웹에서 얻을 수 없으므로 지표에 포함하지 않는다.

export interface ReportData {
  yearMonth: string;      // 표시용 라벨 "2026년 8월"
  todoTotal: number;
  todoCompleted: number;
  eventCount: number;     // 반복 전개 후 "발생" 수 (마스터 row 수 아님)
  expense: number;        // 순지출
  income: number;
  topCategories: CategorySlice[];
}

export function useReport(uid: string, year: number, month: number): ReportData {
  const events = useEvents(uid);
  const todos = useTodos(uid);
  // useFinance가 이미 해당 월·tombstone·isExcluded를 걸러 반환한다.
  const finances = useFinance(uid, year, month);

  // Floating Date(YYYY-MM-DD) 범위 — 타임존 왕복 없이 문자열로 조립한다.
  // 말일 일수만 Date로 구하고(로컬 시각 무관) 포맷은 직접 한다.
  const { from, to } = useMemo(() => {
    const pad = (n: number) => String(n).padStart(2, '0');
    const lastDay = new Date(year, month, 0).getDate();
    return { from: `${year}-${pad(month)}-01`, to: `${year}-${pad(month)}-${pad(lastDay)}` };
  }, [year, month]);

  // 마감일 있는 할일만 완료율 분모(앱 TodoDao.observeByDueDateRange가 dueDate NOT NULL을 건다).
  const monthTodos = useMemo(
    () => todos.filter(t => t.dueDate && t.dueDate >= from && t.dueDate <= to),
    [todos, from, to],
  );

  // 반복 마스터는 1건이므로 발생 단위로 전개해서 센다(PRD 2.5).
  const eventCount = useMemo(
    () => expandEvents(events, from, to).length,
    [events, from, to],
  );

  const totals = useMemo(() => financeTotals(finances), [finances]);
  const topCategories = useMemo(() => topExpenseCategories(finances), [finances]);

  return {
    yearMonth: `${year}년 ${month}월`,
    todoTotal: monthTodos.length,
    todoCompleted: monthTodos.filter(t => t.isCompleted).length,
    eventCount,
    expense: totals.expense,
    income: totals.income,
    topCategories,
  };
}
