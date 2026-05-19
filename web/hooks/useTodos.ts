'use client';
import { useEffect, useState } from 'react';
import { onSnapshot } from 'firebase/firestore';
import { todosQuery } from '@/lib/db';
import { LSyncTodo } from '@/types/models';

export function useTodos(uid: string) {
  const [todos, setTodos] = useState<LSyncTodo[]>([]);

  useEffect(() => {
    const unsub = onSnapshot(todosQuery(uid), (snap) => {
      const all = snap.docs.map((d) => ({ id: d.id, ...d.data() } as LSyncTodo));
      // 삭제된 항목 제외, dueDate 오름차순
      const active = all
        .filter(t => !t.deletedAt)
        .sort((a, b) => (a.dueDate ?? '').localeCompare(b.dueDate ?? ''));
      setTodos(active);
    });
    return unsub;
  }, [uid]);

  return todos;
}
