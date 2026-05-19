'use client';
import { useEffect, useState } from 'react';
import { onSnapshot } from 'firebase/firestore';
import { financeMonthQuery } from '@/lib/db';
import { LSyncFinance } from '@/types/models';

export function useFinance(uid: string, year: number, month: number) {
  const [items, setItems] = useState<LSyncFinance[]>([]);

  useEffect(() => {
    const pad = (n: number) => String(n).padStart(2, '0');
    const start = `${year}-${pad(month)}-01`;
    const lastDay = new Date(year, month, 0).getDate();
    const end   = `${year}-${pad(month)}-${lastDay}`;

    const unsub = onSnapshot(financeMonthQuery(uid, start, end), (snap) => {
      setItems(snap.docs.map((d) => ({ id: d.id, ...d.data() } as LSyncFinance)));
    });
    return unsub;
  }, [uid, year, month]);

  return items;
}
