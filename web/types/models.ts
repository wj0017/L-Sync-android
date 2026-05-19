export interface LSyncEvent {
  id: string;
  userId: string;
  title: string;
  isAllDay: boolean;
  startDate: string;
  endDate: string;
  timezone?: string;
  rrule?: string;
  hasAlarm?: boolean;
  deletedAt?: string | null;
  createdAt?: string;
  updatedAt?: string;
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
  completedAt?: string | null;
  deletedAt?: string | null;
  createdAt?: string;
  updatedAt?: string;
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
  createdAt?: string;
  updatedAt?: string;
}
