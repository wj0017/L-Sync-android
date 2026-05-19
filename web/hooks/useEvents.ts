'use client';
import { useEffect, useState } from 'react';
import { onSnapshot } from 'firebase/firestore';
import { eventsQuery } from '@/lib/db';
import { LSyncEvent } from '@/types/models';

export function useEvents(uid: string) {
  const [events, setEvents] = useState<LSyncEvent[]>([]);

  useEffect(() => {
    const unsub = onSnapshot(eventsQuery(uid), (snap) => {
      const all = snap.docs.map((d) => ({ id: d.id, ...d.data() } as LSyncEvent));
      // 삭제된 항목 제외, startDate 오름차순
      const active = all
        .filter(e => !e.deletedAt)
        .sort((a, b) => (a.startDate ?? '').localeCompare(b.startDate ?? ''));
      setEvents(active);
    });
    return unsub;
  }, [uid]);

  return events;
}
