'use client';
import { useEffect, useState } from 'react';
import { onSnapshot } from 'firebase/firestore';
import { eventsQuery } from '@/lib/db';
import { LSyncEvent } from '@/types/models';

export function useEvents(uid: string) {
  const [events, setEvents] = useState<LSyncEvent[]>([]);

  useEffect(() => {
    const unsub = onSnapshot(eventsQuery(uid), (snap) => {
      setEvents(snap.docs.map((d) => ({ id: d.id, ...d.data() } as LSyncEvent)));
    });
    return unsub;
  }, [uid]);

  return events;
}
