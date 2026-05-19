import {
  collection,
  query,
  where,
  addDoc,
  updateDoc,
  deleteDoc,
  doc,
  serverTimestamp,
} from 'firebase/firestore';
import { db } from './firebase';
import { LSyncEvent, LSyncFinance, LSyncTodo } from '@/types/models';

// ── Queries (복합 인덱스 불필요 — 필터링은 클라이언트에서) ───────────────────────

export function eventsQuery(uid: string) {
  return query(collection(db, 'events'), where('userId', '==', uid));
}

export function todosQuery(uid: string) {
  return query(collection(db, 'todos'), where('userId', '==', uid));
}

export function financeQuery(uid: string) {
  return query(collection(db, 'finance'), where('userId', '==', uid));
}

// ── Mutations ─────────────────────────────────────────────────────────────────

export async function addEvent(uid: string, data: Omit<LSyncEvent, 'id' | 'userId' | 'createdAt' | 'updatedAt'>) {
  return addDoc(collection(db, 'events'), {
    ...data,
    userId: uid,
    deletedAt: null,
    createdAt: serverTimestamp(),
    updatedAt: serverTimestamp(),
  });
}

export async function deleteEvent(id: string) {
  return updateDoc(doc(db, 'events', id), {
    deletedAt: new Date().toISOString(),
    updatedAt: serverTimestamp(),
  });
}

export async function addTodo(uid: string, data: { title: string; dueDate?: string }) {
  return addDoc(collection(db, 'todos'), {
    ...data,
    userId: uid,
    isCompleted: false,
    financeIsLinked: false,
    deletedAt: null,
    createdAt: serverTimestamp(),
    updatedAt: serverTimestamp(),
  });
}

export async function toggleTodo(id: string, isCompleted: boolean) {
  return updateDoc(doc(db, 'todos', id), {
    isCompleted,
    completedAt: isCompleted ? new Date().toISOString() : null,
    updatedAt: serverTimestamp(),
  });
}

export async function deleteTodo(id: string) {
  return updateDoc(doc(db, 'todos', id), {
    deletedAt: new Date().toISOString(),
    updatedAt: serverTimestamp(),
  });
}

export async function addFinance(uid: string, data: Omit<LSyncFinance, 'id' | 'userId' | 'isExcluded' | 'createdAt' | 'updatedAt'>) {
  return addDoc(collection(db, 'finance'), {
    ...data,
    userId: uid,
    isExcluded: false,
    createdAt: serverTimestamp(),
    updatedAt: serverTimestamp(),
  });
}

export async function deleteFinance(id: string) {
  return deleteDoc(doc(db, 'finance', id));
}
