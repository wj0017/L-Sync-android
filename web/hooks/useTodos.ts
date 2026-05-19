'use client';
import { useEffect, useState } from 'react';
import { onSnapshot } from 'firebase/firestore';
import { todosQuery } from '@/lib/db';
import { LSyncTodo } from '@/types/models';

export function useTodos(uid: string) {
  const [todos, setTodos] = useState<LSyncTodo[]>([]);

  useEffect(() => {
    const unsub = onSnapshot(todosQuery(uid), (snap) => {
      setTodos(snap.docs.map((d) => ({ id: d.id, ...d.data() } as LSyncTodo)));
    });
    return unsub;
  }, [uid]);

  return todos;
}
