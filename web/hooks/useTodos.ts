'use client';
import { useEffect, useState } from 'react';
import { onSnapshot } from 'firebase/firestore';
import { readErrorMessage, useAuth } from '@/components/AuthProvider';
import { todosQuery } from '@/lib/db';
import { LSyncTodo } from '@/types/models';

export function useTodos(uid: string) {
  const [todos, setTodos] = useState<LSyncTodo[]>([]);
  const { reportReadError } = useAuth();

  useEffect(() => {
    const unsub = onSnapshot(todosQuery(uid), (snap) => {
      const all = snap.docs.map((d) => ({ id: d.id, ...d.data() } as LSyncTodo));
      // 삭제된 항목 제외, dueDate 오름차순
      const active = all
        .filter(t => !t.deletedAt)
        .sort((a, b) => (a.dueDate ?? '').localeCompare(b.dueDate ?? ''));
      setTodos(active);
      reportReadError('todos', null);
    }, error => {
      setTodos([]);
      reportReadError('todos', readErrorMessage(error));
    });
    return () => { unsub(); reportReadError('todos', null); };
  }, [uid, reportReadError]);

  return todos;
}
