'use client';
import { useMemo, useState } from 'react';
import { useAuth } from '@/components/AuthProvider';
import { useFinance } from '@/hooks/useFinance';
import { addFinance, deleteFinance } from '@/lib/db';
import { LSyncFinance } from '@/types/models';

const KO_MONTHS = ['1월','2월','3월','4월','5월','6월','7월','8월','9월','10월','11월','12월'];

const CATEGORIES = ['식비','교통','문화','의료','쇼핑','구독','급여','용돈','기타'];

type Filter = 'ALL' | 'INCOME' | 'EXPENSE';

function todayStr() {
  const d = new Date();
  return `${d.getFullYear()}-${String(d.getMonth()+1).padStart(2,'0')}-${String(d.getDate()).padStart(2,'0')}`;
}

export default function FinancePage() {
  const { uid, loading } = useAuth();
  const today = new Date();

  const [year,  setYear]  = useState(today.getFullYear());
  const [month, setMonth] = useState(today.getMonth() + 1);
  const [filter, setFilter] = useState<Filter>('ALL');
  const [showForm, setShowForm] = useState(false);

  const [fAmount,   setFAmount]   = useState('');
  const [fType,     setFType]     = useState<'INCOME' | 'EXPENSE'>('EXPENSE');
  const [fCategory, setFCategory] = useState('기타');
  const [fDate,     setFDate]     = useState(todayStr());
  const [fNote,     setFNote]     = useState('');

  const items = useFinance(uid, year, month);

  const filtered = useMemo(
    () => filter === 'ALL' ? items : items.filter(i => i.type === filter),
    [items, filter],
  );

  const { income, expense } = useMemo(() => {
    let income = 0, expense = 0;
    items.forEach(f => { f.type === 'INCOME' ? income += f.amount : expense += f.amount; });
    return { income, expense };
  }, [items]);
  const balance = income - expense;

  function prevMonth() {
    if (month === 1) { setYear(y => y - 1); setMonth(12); }
    else setMonth(m => m - 1);
  }
  function nextMonth() {
    if (month === 12) { setYear(y => y + 1); setMonth(1); }
    else setMonth(m => m + 1);
  }

  async function handleAdd() {
    if (!fAmount) return;
    await addFinance(uid, {
      type: fType,
      amount: Number(fAmount),
      category: fCategory,
      date: fDate,
      note: fNote || undefined,
    });
    setFAmount(''); setFNote(''); setShowForm(false);
  }

  if (loading) return <LoadingSpinner />;

  return (
    <div className="pt-8">
      {/* 헤더 */}
      <div className="px-5 mb-4">
        <p className="text-[11px] font-medium tracking-[0.12em] text-ls-fg3 uppercase">{year}</p>
        <div className="flex items-center justify-between">
          <h1 className="text-[30px] font-semibold tracking-[-0.035em]">{KO_MONTHS[month - 1]} 가계부</h1>
          <div className="flex gap-2">
            <button onClick={prevMonth} className="w-[38px] h-[38px] bg-ls-card border border-ls-hair rounded-full flex items-center justify-center text-ls-fg">‹</button>
            <button onClick={nextMonth} className="w-[38px] h-[38px] bg-ls-card border border-ls-hair rounded-full flex items-center justify-center text-ls-fg">›</button>
          </div>
        </div>
      </div>

      {/* 요약 카드 */}
      <div className="mx-5 bg-ls-card border border-ls-hair rounded-[14px] px-4 py-4 mb-5">
        <div className="flex items-center justify-between mb-2">
          <span className="text-[11px] font-medium text-ls-fg3 uppercase tracking-[0.12em]">잔액</span>
          <span className="text-[22px] font-semibold">
            <span className={`font-[var(--font-instrument-serif)] italic text-[17px] mr-0.5 ${balance >= 0 ? '' : 'text-ls-fg2'}`}>
              {balance >= 0 ? '+' : '−'}
            </span>
            ₩{Math.abs(balance).toLocaleString()}
          </span>
        </div>
        <div className="flex gap-4">
          <div>
            <p className="text-[10px] text-ls-fg3 uppercase tracking-wide mb-0.5">수입</p>
            <p className="text-[14px] font-medium text-ls-green">+₩{income.toLocaleString()}</p>
          </div>
          <div>
            <p className="text-[10px] text-ls-fg3 uppercase tracking-wide mb-0.5">지출</p>
            <p className="text-[14px] font-medium text-ls-fg2">₩{expense.toLocaleString()}</p>
          </div>
        </div>
      </div>

      {/* 필터 + 추가 버튼 */}
      <div className="px-5 flex items-center justify-between mb-4">
        <div className="flex gap-2">
          {(['ALL','INCOME','EXPENSE'] as Filter[]).map(f => (
            <button key={f}
              onClick={() => setFilter(f)}
              className={`text-[11px] px-3 py-1 rounded-full border font-medium ${
                filter === f
                  ? 'bg-ls-blue border-ls-blue text-white'
                  : 'border-ls-hair text-ls-fg2'
              }`}>
              {f === 'ALL' ? '전체' : f === 'INCOME' ? '수입' : '지출'}
            </button>
          ))}
        </div>
        <button onClick={() => setShowForm(true)}
          className="w-[38px] h-[38px] bg-ls-blue rounded-full flex items-center justify-center text-white text-[20px] leading-none">
          +
        </button>
      </div>

      {/* 거래 리스트 */}
      <div className="px-5 flex flex-col gap-2">
        {filtered.length === 0 && (
          <p className="text-[13px] text-ls-fg3 py-2">내역이 없습니다.</p>
        )}
        {filtered.map(item => (
          <FinanceRow key={item.id} item={item} onDelete={() => deleteFinance(item.id)} />
        ))}
      </div>

      {/* 내역 추가 폼 */}
      {showForm && (
        <Modal title="내역 추가" onClose={() => setShowForm(false)}>
          {/* 수입/지출 선택 */}
          <div className="flex gap-2 mb-3">
            {(['EXPENSE', 'INCOME'] as const).map(t => (
              <button key={t}
                onClick={() => setFType(t)}
                className={`flex-1 py-2 rounded-[10px] text-[13px] font-medium border ${
                  fType === t
                    ? t === 'INCOME' ? 'bg-ls-green border-ls-green text-white' : 'bg-ls-red border-ls-red text-white'
                    : 'border-ls-hair text-ls-fg2'
                }`}>
                {t === 'INCOME' ? '수입' : '지출'}
              </button>
            ))}
          </div>
          <input
            type="number"
            className="w-full bg-ls-raised border border-ls-hair rounded-[10px] px-4 py-3 text-[14px] text-ls-fg outline-none mb-3"
            placeholder="금액"
            value={fAmount}
            onChange={e => setFAmount(e.target.value)}
            autoFocus
          />
          {/* 카테고리 */}
          <div className="flex flex-wrap gap-1.5 mb-3">
            {CATEGORIES.map(c => (
              <button key={c}
                onClick={() => setFCategory(c)}
                className={`text-[11px] px-2.5 py-1 rounded-full border ${
                  fCategory === c ? 'bg-ls-blue border-ls-blue text-white' : 'border-ls-hair text-ls-fg2'
                }`}>
                {c}
              </button>
            ))}
          </div>
          <input type="date" value={fDate} onChange={e => setFDate(e.target.value)}
            className="w-full bg-ls-raised border border-ls-hair rounded-[10px] px-4 py-3 text-[14px] text-ls-fg outline-none mb-3" />
          <input
            className="w-full bg-ls-raised border border-ls-hair rounded-[10px] px-4 py-3 text-[14px] text-ls-fg outline-none mb-3"
            placeholder="메모 (선택)"
            value={fNote}
            onChange={e => setFNote(e.target.value)}
          />
          <button onClick={handleAdd}
            className="w-full bg-ls-blue text-white rounded-[10px] py-3 text-[14px] font-medium">
            추가
          </button>
        </Modal>
      )}
    </div>
  );
}

function FinanceRow({ item, onDelete }: { item: LSyncFinance; onDelete: () => void }) {
  return (
    <div className="flex items-center gap-3 bg-ls-card border border-ls-hair rounded-[12px] px-4 py-3">
      <div className="flex-1 min-w-0">
        <div className="flex items-center gap-2">
          <p className="text-[14px] font-medium truncate">{item.note || item.category}</p>
          <span className="text-[10px] text-ls-fg3 shrink-0">{item.category}</span>
        </div>
        <p className="text-[11px] text-ls-fg3">{item.date}</p>
      </div>
      <span className={`text-[14px] font-semibold shrink-0 ${item.type === 'INCOME' ? 'text-ls-green' : 'text-ls-fg2'}`}>
        {item.type === 'INCOME' ? '+' : ''}₩{item.amount.toLocaleString()}
      </span>
      <button onClick={onDelete} className="text-ls-fg3 text-[18px] leading-none px-1">×</button>
    </div>
  );
}

function Modal({ title, children, onClose }: { title: string; children: React.ReactNode; onClose: () => void }) {
  return (
    <div className="fixed inset-0 bg-black/60 flex items-end justify-center z-50 px-4 pb-6"
      onClick={e => { if (e.target === e.currentTarget) onClose(); }}>
      <div className="w-full max-w-[480px] bg-ls-card border border-ls-hair rounded-[16px] p-5 max-h-[80vh] overflow-y-auto">
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
