'use client';
import { useMemo, useState } from 'react';
import { useAuth } from '@/components/AuthProvider';
import { useEvents } from '@/hooks/useEvents';
import { useTodos } from '@/hooks/useTodos';
import { addEvent, addTodo, deleteEvent, deleteTodo, toggleTodo } from '@/lib/db';
import { EventOccurrence, expandEvents } from '@/lib/recurrence';
import { LSyncTodo } from '@/types/models';

const KO_DAYS_SHORT = ['일', '월', '화', '수', '목', '금', '토'];
const KO_MONTHS = ['1월','2월','3월','4월','5월','6월','7월','8월','9월','10월','11월','12월'];

function isoDate(d: Date) {
  return `${d.getFullYear()}-${String(d.getMonth()+1).padStart(2,'0')}-${String(d.getDate()).padStart(2,'0')}`;
}

export default function SchedulePage() {
  const { uid, loading } = useAuth();
  const today = new Date();

  const [year,  setYear]  = useState(today.getFullYear());
  const [month, setMonth] = useState(today.getMonth());
  const [selectedDate, setSelectedDate] = useState(isoDate(today));
  const [showEventForm, setShowEventForm] = useState(false);
  const [showTodoForm,  setShowTodoForm]  = useState(false);
  const [newTitle, setNewTitle] = useState('');
  const [newTime,  setNewTime]  = useState('');
  const [newAllDay, setNewAllDay] = useState(true);

  const events = useEvents(uid);
  const todos  = useTodos(uid);

  // Calendar grid
  const calDays = useMemo(() => {
    const first = new Date(year, month, 1).getDay();
    const total = new Date(year, month + 1, 0).getDate();
    const cells: (number | null)[] = Array(first).fill(null);
    for (let i = 1; i <= total; i++) cells.push(i);
    return cells;
  }, [year, month]);

  // 반복 일정은 마스터 1건이라 그대로 쓰면 첫 발생일에만 찍힌다.
  // 표시 중인 달 범위로 전개해야 매 발생일이 달력에 나타난다(앱과 동일한 읽기-전개).
  const occurrences = useMemo(() => {
    const mm = String(month + 1).padStart(2, '0');
    const from = `${year}-${mm}-01`;
    const to = `${year}-${mm}-${String(new Date(year, month + 1, 0).getDate()).padStart(2, '0')}`;
    return expandEvents(events, from, to)
      .sort((a, b) => a.startDate.localeCompare(b.startDate));
  }, [events, year, month]);

  const eventDates = useMemo(() =>
    new Set(occurrences.map(o => o.date)),
    [occurrences],
  );
  const todoDates = useMemo(() =>
    new Set(todos.map(t => t.dueDate).filter(Boolean)),
    [todos],
  );

  const dayItems = useMemo(() => [
    ...occurrences.filter(o => o.date === selectedDate).map(o => ({ type: 'event' as const, data: o })),
    ...todos.filter(t => t.dueDate === selectedDate).map(t => ({ type: 'todo' as const, data: t })),
  ], [occurrences, todos, selectedDate]);

  function prevMonth() {
    if (month === 0) { setYear(y => y - 1); setMonth(11); }
    else setMonth(m => m - 1);
  }
  function nextMonth() {
    if (month === 11) { setYear(y => y + 1); setMonth(0); }
    else setMonth(m => m + 1);
  }

  async function handleAddEvent() {
    if (!newTitle.trim()) return;
    const startDate = newAllDay
      ? selectedDate
      : `${selectedDate}T${newTime || '09:00'}:00+09:00`;
    await addEvent(uid, { title: newTitle.trim(), isAllDay: newAllDay, startDate, endDate: startDate });
    setNewTitle(''); setNewTime(''); setNewAllDay(true); setShowEventForm(false);
  }

  async function handleAddTodo() {
    if (!newTitle.trim()) return;
    await addTodo(uid, { title: newTitle.trim(), dueDate: selectedDate });
    setNewTitle(''); setShowTodoForm(false);
  }

  if (loading) return <LoadingSpinner />;

  const selDate = new Date(selectedDate + 'T00:00:00');

  return (
    <div className="pt-8">
      {/* 헤더 */}
      <div className="px-5 mb-4">
        <p className="text-[11px] font-medium tracking-[0.12em] text-ls-fg3 uppercase">{year}</p>
        <div className="flex items-center justify-between">
          <h1 className="text-[30px] font-semibold tracking-[-0.035em]">{KO_MONTHS[month]}</h1>
          <div className="flex gap-2">
            <button onClick={prevMonth} className="w-[38px] h-[38px] bg-ls-card border border-ls-hair rounded-full flex items-center justify-center text-ls-fg">‹</button>
            <button onClick={nextMonth} className="w-[38px] h-[38px] bg-ls-card border border-ls-hair rounded-full flex items-center justify-center text-ls-fg">›</button>
          </div>
        </div>
      </div>

      {/* 요일 헤더 */}
      <div className="grid grid-cols-7 px-5 mb-1">
        {KO_DAYS_SHORT.map(d => (
          <div key={d} className="text-center text-[10px] font-medium text-ls-fg3 uppercase tracking-wide py-1">{d}</div>
        ))}
      </div>

      {/* 달력 그리드 */}
      <div className="grid grid-cols-7 px-5 mb-4">
        {calDays.map((day, i) => {
          if (!day) return <div key={`empty-${i}`} />;
          const dateStr = `${year}-${String(month+1).padStart(2,'0')}-${String(day).padStart(2,'0')}`;
          const isToday = dateStr === isoDate(today);
          const isSelected = dateStr === selectedDate;
          const hasEvent = eventDates.has(dateStr);
          const hasTodo  = todoDates.has(dateStr);
          return (
            <button
              key={dateStr}
              onClick={() => setSelectedDate(dateStr)}
              className="flex flex-col items-center py-1.5 rounded-lg"
            >
              <span className={`w-7 h-7 flex items-center justify-center rounded-full text-[13px] font-medium
                ${isSelected ? 'bg-ls-blue text-white' :
                  isToday    ? 'border border-ls-blue text-ls-blue' : 'text-ls-fg'}`}>
                {day}
              </span>
              <div className="flex gap-0.5 mt-0.5 h-1">
                {hasEvent && <div className="w-1 h-1 rounded-full bg-ls-blue" />}
                {hasTodo  && <div className="w-1 h-1 rounded-full bg-ls-green" />}
              </div>
            </button>
          );
        })}
      </div>

      <div className="border-t border-ls-line" />

      {/* 날짜 레이블 */}
      <div className="px-5 pt-4 pb-2 flex items-center justify-between">
        <p className="text-[11px] font-medium tracking-[0.12em] text-ls-fg3 uppercase">
          {KO_MONTHS[selDate.getMonth()]} {selDate.getDate()}일 ({KO_DAYS_SHORT[selDate.getDay()]})
        </p>
        <div className="flex gap-2">
          <button onClick={() => { setShowEventForm(true); setNewTitle(''); }}
            className="text-[11px] px-3 py-1 bg-ls-card border border-ls-hair rounded-full text-ls-fg2">
            + 일정
          </button>
          <button onClick={() => { setShowTodoForm(true); setNewTitle(''); }}
            className="text-[11px] px-3 py-1 bg-ls-card border border-ls-hair rounded-full text-ls-fg2">
            + 할일
          </button>
        </div>
      </div>

      {/* 아이템 리스트 */}
      <div className="px-5 flex flex-col gap-2">
        {dayItems.length === 0 && (
          <p className="text-[13px] text-ls-fg3 py-2">일정이 없습니다.</p>
        )}
        {dayItems.map(item =>
          item.type === 'event'
            ? <EventCard key={`${item.data.masterId}_${item.data.date}`} occurrence={item.data}
                onDelete={() => deleteEvent(item.data.masterId)} />
            : <TodoCard  key={item.data.id} todo={item.data}
                onToggle={v => toggleTodo(item.data.id, v)}
                onDelete={() => deleteTodo(item.data.id)} />,
        )}
      </div>

      {/* 일정 추가 폼 */}
      {showEventForm && (
        <Modal title="일정 추가" onClose={() => setShowEventForm(false)}>
          <input
            className="w-full bg-ls-raised border border-ls-hair rounded-[10px] px-4 py-3 text-[14px] text-ls-fg outline-none mb-3"
            placeholder="제목"
            value={newTitle}
            onChange={e => setNewTitle(e.target.value)}
            autoFocus
          />
          <label className="flex items-center gap-2 mb-3 text-[13px] text-ls-fg2">
            <input type="checkbox" checked={newAllDay} onChange={e => setNewAllDay(e.target.checked)}
              className="accent-ls-blue" />
            종일
          </label>
          {!newAllDay && (
            <input type="time" value={newTime} onChange={e => setNewTime(e.target.value)}
              className="w-full bg-ls-raised border border-ls-hair rounded-[10px] px-4 py-3 text-[14px] text-ls-fg outline-none mb-3" />
          )}
          <button onClick={handleAddEvent}
            className="w-full bg-ls-blue text-white rounded-[10px] py-3 text-[14px] font-medium">
            추가
          </button>
        </Modal>
      )}

      {/* 할일 추가 폼 */}
      {showTodoForm && (
        <Modal title="할 일 추가" onClose={() => setShowTodoForm(false)}>
          <input
            className="w-full bg-ls-raised border border-ls-hair rounded-[10px] px-4 py-3 text-[14px] text-ls-fg outline-none mb-3"
            placeholder="제목"
            value={newTitle}
            onChange={e => setNewTitle(e.target.value)}
            autoFocus
          />
          <button onClick={handleAddTodo}
            className="w-full bg-ls-blue text-white rounded-[10px] py-3 text-[14px] font-medium">
            추가
          </button>
        </Modal>
      )}
    </div>
  );
}

function EventCard({ occurrence, onDelete }: { occurrence: EventOccurrence; onDelete: () => void }) {
  const time = occurrence.isAllDay ? '종일' : occurrence.startDate.slice(11, 16);
  return (
    <div className="flex items-center gap-3 bg-ls-card border border-ls-hair rounded-[12px] px-4 py-3">
      <div className="w-1 h-10 bg-ls-blue rounded-full flex-shrink-0" />
      <div className="flex-1 min-w-0">
        <p className="text-[14px] font-medium truncate">{occurrence.title}</p>
        <p className="text-[11px] text-ls-fg3">
          {time}{occurrence.isRecurring && ' · 반복'}
        </p>
      </div>
      {/* 반복 발생은 삭제 버튼을 노출하지 않는다 — masterId 삭제는 시리즈 전체를 지운다.
          범위 삭제(단건/이후/전체)는 앱에만 있는 기능이다. */}
      {!occurrence.isRecurring && (
        <button onClick={onDelete} className="text-ls-fg3 text-[18px] leading-none px-1">×</button>
      )}
    </div>
  );
}

function TodoCard({ todo, onToggle, onDelete }: {
  todo: LSyncTodo;
  onToggle: (v: boolean) => void;
  onDelete: () => void;
}) {
  return (
    <div className="flex items-center gap-3 bg-ls-card border border-ls-hair rounded-[12px] px-4 py-3">
      <button
        onClick={() => onToggle(!todo.isCompleted)}
        className={`w-[22px] h-[22px] rounded-full border-[1.5px] flex items-center justify-center flex-shrink-0 ${
          todo.isCompleted ? 'bg-ls-blue border-ls-blue' : 'border-ls-muted'
        }`}
      >
        {todo.isCompleted && <span className="text-[11px] text-white font-bold leading-none">✓</span>}
      </button>
      <p className={`flex-1 text-[14px] font-medium truncate ${todo.isCompleted ? 'line-through text-ls-fg3' : ''}`}>
        {todo.title}
      </p>
      <button onClick={onDelete} className="text-ls-fg3 text-[18px] leading-none px-1">×</button>
    </div>
  );
}

function Modal({ title, children, onClose }: { title: string; children: React.ReactNode; onClose: () => void }) {
  return (
    <div className="fixed inset-0 bg-black/60 flex items-end justify-center z-50 px-4 pb-6"
      onClick={e => { if (e.target === e.currentTarget) onClose(); }}>
      <div className="w-full max-w-[480px] bg-ls-card border border-ls-hair rounded-[16px] p-5">
        <div className="flex items-center justify-between mb-4">
          <h2 className="text-[16px] font-semibold">{title}</h2>
          <button onClick={onClose} className="text-ls-fg3 text-[20px] leading-none">×</button>
        </div>
        {children}
      </div>
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
