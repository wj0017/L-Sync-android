'use client';
import { useEffect, useState } from 'react';
import { onSnapshot } from 'firebase/firestore';
import { readErrorMessage, useAuth } from '@/components/AuthProvider';
import { financeQuery } from '@/lib/db';
import { LSyncFinance } from '@/types/models';

export function useFinance(uid: string, year: number, month: number) {
  const [items, setItems] = useState<LSyncFinance[]>([]);
  const { reportReadError } = useAuth();

  useEffect(() => {
    const pad = (n: number) => String(n).padStart(2, '0');
    const prefix = `${year}-${pad(month)}`;

    const unsub = onSnapshot(financeQuery(uid), (snap) => {
      const filtered = snap.docs
        .map(d => ({ id: d.id, ...d.data() } as LSyncFinance))
        .filter(f => !f.deletedAt && !f.isExcluded && f.date?.startsWith(prefix))
        .sort((a, b) => b.date.localeCompare(a.date));
      setItems(filtered);
      reportReadError('finance', null);
    }, error => {
      setItems([]);
      reportReadError('finance', readErrorMessage(error));
    });
    return () => { unsub(); reportReadError('finance', null); };
  }, [uid, year, month, reportReadError]);

  return items;
}
