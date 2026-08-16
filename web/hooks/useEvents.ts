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
      // 반복 일정은 마스터 1건만 저장된다(읽기-전개) — 여기서는 전개하지 않고 마스터 목록을 그대로 반환한다.
      // 전개는 표시 범위를 아는 화면이 lib/recurrence의 expandEvents로 수행한다.
      // 삭제된 항목 제외, startDate 오름차순(전개 전 기준 정렬)
      const active = all
        .filter(e => !e.deletedAt)
        .sort((a, b) => (a.startDate ?? '').localeCompare(b.startDate ?? ''));
      setEvents(active);
    });
    return unsub;
  }, [uid]);

  return events;
}
