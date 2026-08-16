import {
  collection,
  query,
  where,
  updateDoc,
  setDoc,
  doc,
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

// ── 앱과의 쓰기 계약 ───────────────────────────────────────────────────────────
// 안드로이드 FirestoreDataSource는 문서 ID = 엔티티 id로 쓰고, 읽을 때는
// getString("id") / getLong("createdAt") 로 역매핑한다. 타입이 하나라도 어긋나면
// runCatching + mapNotNull 조합에 걸려 그 문서가 통째로 조용히 버려지므로:
//   · 문서 ID와 본문 `id` 필드를 동일한 UUID로 채운다 (랜덤 doc ID 자동 생성 금지)
//   · 시각 필드는 서버 타임스탬프/ISO 문자열이 아닌 epoch millis Number로 쓴다
//   · 날짜 필드(date/dueDate/startDate/endDate)는 Floating Time 문자열 그대로 둔다
//   · 삭제는 hard delete가 아닌 tombstone(deletedAt) — 앱 pull은 upsert-only다

export async function addEvent(uid: string, data: Omit<LSyncEvent, 'id' | 'userId' | 'createdAt' | 'updatedAt'>) {
  const id = crypto.randomUUID();
  const now = Date.now();
  await setDoc(doc(db, 'events', id), {
    rrule: null,
    exdatesJson: null,
    overridesJson: null,
    hasAlarm: false,
    deletedAt: null,
    ...data,
    id,
    userId: uid,
    createdAt: now,
    updatedAt: now,
  });
  return id;
}

export async function deleteEvent(id: string) {
  const now = Date.now();
  return updateDoc(doc(db, 'events', id), {
    deletedAt: now,
    updatedAt: now,
  });
}

export async function addTodo(uid: string, data: { title: string; dueDate?: string }) {
  const id = crypto.randomUUID();
  const now = Date.now();
  await setDoc(doc(db, 'todos', id), {
    isCompleted: false,
    financeIsLinked: false,
    deletedAt: null,
    ...data,
    id,
    userId: uid,
    createdAt: now,
    updatedAt: now,
  });
  return id;
}

export async function toggleTodo(id: string, isCompleted: boolean) {
  const now = Date.now();
  return updateDoc(doc(db, 'todos', id), {
    isCompleted,
    completedAt: isCompleted ? now : null,
    updatedAt: now,
  });
}

export async function deleteTodo(id: string) {
  const now = Date.now();
  return updateDoc(doc(db, 'todos', id), {
    deletedAt: now,
    updatedAt: now,
  });
}

export async function addFinance(uid: string, data: Omit<LSyncFinance, 'id' | 'userId' | 'isExcluded' | 'createdAt' | 'updatedAt'>) {
  const id = crypto.randomUUID();
  const now = Date.now();
  await setDoc(doc(db, 'finance', id), {
    isExcluded: false,
    settlementGroupId: null,
    sourceTodoId: null,
    deletedAt: null,
    ...data,
    id,
    userId: uid,
    createdAt: now,
    updatedAt: now,
  });
  return id;
}

// hard delete하면 앱 Room에 남은 행이 다시 push되어 부활한다 — tombstone으로 전파.
export async function deleteFinance(id: string) {
  const now = Date.now();
  return updateDoc(doc(db, 'finance', id), {
    deletedAt: now,
    updatedAt: now,
  });
}
