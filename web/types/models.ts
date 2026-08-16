// 시각 필드(createdAt/updatedAt/deletedAt/completedAt)는 앱과 동일하게 epoch millis Number다.
// 앱의 FirestoreDataSource가 getLong()으로 읽으므로 문자열/Timestamp를 쓰면 문서가 드롭된다.
// 반면 date/dueDate/startDate/endDate는 Floating Time "YYYY-MM-DD" 문자열을 유지한다.

export interface LSyncEvent {
  id: string;
  userId: string;
  title: string;
  isAllDay: boolean;
  startDate: string;
  endDate: string;
  timezone?: string;
  rrule?: string;
  // 반복 발생 예외 — exdates는 문자열 배열 JSON, overrides는 날짜→객체 맵 JSON (앱이 기록한다)
  exdatesJson?: string | null;
  overridesJson?: string | null;
  hasAlarm?: boolean;
  deletedAt?: number | null;
  createdAt?: number;
  updatedAt?: number;
}

export interface LSyncTodo {
  id: string;
  userId: string;
  title: string;
  isCompleted: boolean;
  dueDate?: string;
  financeIsLinked: boolean;
  financeType?: string;
  financeCategory?: string;
  financeAmount?: number;
  linkedFinanceId?: string;
  completedAt?: number | null;
  deletedAt?: number | null;
  createdAt?: number;
  updatedAt?: number;
}

export interface LSyncFinance {
  id: string;
  userId: string;
  type: 'INCOME' | 'EXPENSE';
  amount: number;
  category: string;
  date: string;
  note?: string;
  sourceTodoId?: string | null;
  isExcluded: boolean;
  settlementGroupId?: string | null;
  deletedAt?: number | null;
  createdAt?: number;
  updatedAt?: number;
}
