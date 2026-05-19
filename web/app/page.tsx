'use client';
import { useMemo } from 'react';
import { useAuth } from '@/components/AuthProvider';
import { useEvents } from '@/hooks/useEvents';
import { useTodos } from '@/hooks/useTodos';
import { useFinance } from '@/hooks/useFinance';
import { LSyncEvent, LSyncTodo } from '@/types/models';

const KO_DAYS = ['일', '월', '화', '수', '목', '금', '토'];
const KO_MONTHS = ['1월','2월','3월','4월','5월','6월','7월','8월','9월','10월','11월','12월'];

function fmtDate(d: Date) {
  return `${KO_MONTHS[d.getMonth()]} ${d.getDate()}일 ${KO_DAYS[d.getDay()]}요일`;
}

function todayStr() {
  const d = new Date();
  return `${d.getFullYear()}-${String(d.getMonth()+1).padStart(2,'0')}-${String(d.getDate()).padStart(2,'0')}`;
}

export default function HomePage() {
  const { uid, loading } = useAuth();
  const today = new Date();
  const todayIso = todayStr();

  const events  = useEvents(uid);
  const todos   = useTodos(uid);
  const finance = useFinance(uid, today.getFullYear(), today.getMonth() + 1);

  const todayEvents = useMemo(
    () => events.filter(e => e.startDate?.startsWith(todayIso)),
    [events, todayIso],
  );
  const todayTodos = useMemo(
    () => todos.filter(t => t.dueDate === todayIso),
    [todos, todayIso],
  );

  const { income, expense } = useMemo(() => {
    let income = 0, expense = 0;
    finance.forEach(f => { f.type === 'INCOME' ? income += f.amount : expense += f.amount; });
    return { income, expense };
  }, [finance]);

  const balance = income - expense;
  const allItems = [
    ...todayEvents.map(e => ({ type: 'event' as const, data: e })),
    ...todayTodos.map(t => ({ type: 'todo'  as const, data: t })),
  ];
  const previewItems = allItems.slice(0, 5);

  if (loading) {
    return (
      <div className="flex items-center justify-center min-h-screen">
        <div className="w-6 h-6 border-2 border-ls-blue border-t-transparent rounded-full animate-spin" />
      </div>
    );
  }

  return (
    <div className="px-5 pt-8">
      {/* 헤더 */}
      <div className="flex items-end justify-between mb-6">
        <span className="text-[20px] font-semibold tracking-[0.06em]">L·SYNC</span>
        <div className="text-right">
          <p className="text-[10px] font-medium tracking-[0.12em] text-ls-fg3 uppercase">{today.getFullYear()}</p>
          <p className="text-[14px] font-medium tracking-[-0.01em] text-ls-fg2">{fmtDate(today)}</p>
        </div>
      </div>

      {/* 통독 */}
      <p className="text-[11px] font-medium tracking-[0.12em] text-ls-fg3 uppercase mb-3">성경 통독</p>
      <div className="bg-ls-card border border-ls-hair rounded-[14px] px-4 py-4">
        <p className="text-[13px] text-ls-fg2">앱에서 통독 진행 현황을 확인하세요.</p>
        <p className="text-[11px] text-ls-fg3 mt-1">웹 통독 연동은 추후 업데이트 예정입니다.</p>
      </div>

      <div className="my-6 border-t border-ls-line" />

      {/* 일정 */}
      <p className="text-[11px] font-medium tracking-[0.12em] text-ls-fg3 uppercase mb-3">
        오늘 · 일정 &amp; 할 일 · {allItems.length}
      </p>
      {previewItems.length === 0 ? (
        <p className="text-[13px] text-ls-fg3 py-1">오늘 일정이 없습니다.</p>
      ) : (
        <div className="flex flex-col gap-2">
          {previewItems.map(item =>
            item.type === 'event'
              ? <EventRow key={item.data.id} event={item.data as LSyncEvent} />
              : <TodoRow  key={item.data.id} todo={item.data as LSyncTodo} />,
          )}
          {allItems.length > 5 && (
            <a href="/schedule" className="text-[13px] text-ls-fg3 pt-1 block">더 보기 →</a>
          )}
        </div>
      )}

      <div className="my-6 border-t border-ls-line" />

      {/* 가계부 */}
      <p className="text-[11px] font-medium tracking-[0.12em] text-ls-fg3 uppercase mb-3">
        {KO_MONTHS[today.getMonth()]} 가계부
      </p>
      <div className="bg-ls-card border border-ls-hair rounded-[14px] px-4 py-4 mb-2">
        <div className="flex items-center justify-between">
          <span className="text-[11px] font-medium text-ls-fg3 uppercase tracking-[0.12em]">잔액</span>
          <span className="text-[22px] font-semibold">
            <span className={`font-[var(--font-instrument-serif)] italic text-[17px] mr-0.5 ${balance >= 0 ? '' : 'text-ls-fg2'}`}>
              {balance >= 0 ? '+' : '−'}
            </span>
            ₩{Math.abs(balance).toLocaleString()}
          </span>
        </div>
        <div className="flex items-center gap-4 mt-2">
          <span className="text-[13px] text-ls-green">+₩{income.toLocaleString()}</span>
          <span className="text-[13px] text-ls-fg2">₩{expense.toLocaleString()}</span>
        </div>
      </div>
    </div>
  );
}

function EventRow({ event }: { event: LSyncEvent }) {
  const time = event.isAllDay ? '종일' : (event.startDate?.slice(11, 16) ?? '');
  return (
    <div className="flex items-center gap-3 bg-ls-card border border-ls-hair rounded-[12px] px-4 py-3">
      <div className="w-1 h-8 bg-ls-blue rounded-full flex-shrink-0" />
      <div className="flex-1 min-w-0">
        <p className="text-[14px] font-medium truncate">{event.title}</p>
        <p className="text-[11px] text-ls-fg3">{time}</p>
      </div>
    </div>
  );
}

function TodoRow({ todo }: { todo: LSyncTodo }) {
  return (
    <div className="flex items-center gap-3 bg-ls-card border border-ls-hair rounded-[12px] px-4 py-3">
      <div className={`w-[22px] h-[22px] rounded-full border-[1.5px] flex items-center justify-center flex-shrink-0 ${
        todo.isCompleted ? 'bg-ls-blue border-ls-blue' : 'border-ls-muted'
      }`}>
        {todo.isCompleted && <span className="text-[11px] text-white font-bold leading-none">✓</span>}
      </div>
      <p className={`flex-1 text-[14px] font-medium truncate ${todo.isCompleted ? 'line-through text-ls-fg3' : ''}`}>
        {todo.title}
      </p>
    </div>
  );
}
