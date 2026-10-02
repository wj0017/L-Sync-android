# Step 0: next-alarm

## 목표

반복 일정의 오늘 발생분을 시각이 지나지 않았다면 알람 대상으로 선택한다. `exdates`, 발생별 `hasAlarm`, 제목·시각 override를 적용하고, 현재 시각 이전 발생은 건너뛴다.

## 구현 범위

- `EventRecurrence.nextOccurrence`에 발생 필터를 받아 한 번의 RRULE 순회에서 다음 유효 발생을 고른다.
- 순수 함수 `notification/NextAlarm.kt`가 기기 시간대의 종일 일정 09:00과 시간 지정 일정의 저장된 offset을 시각으로 계산한다.
- 단발, 오늘 전/후, 발생별 알람 off/on, 제목·시각 변경, exdate, 종료, 날짜/offset 경계를 JVM 테스트로 확인한다.

## 완료 조건

- 오늘 10:00 반복 일정은 오늘 10:00 전 재등록해도 오늘 알람을 유지한다.
- 현재 시각이 지난 발생이나 알람을 끈 발생은 건너뛰고 다음 유효 발생을 고른다.
- `NextAlarmTest`를 포함한 단위 테스트가 통과한다.
