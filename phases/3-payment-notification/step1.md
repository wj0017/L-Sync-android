# Step 1: notification-service

## 읽어야 할 파일

먼저 아래 파일들을 읽고 프로젝트의 아키텍처와 설계 의도를 파악하라:

- `CLAUDE.md`
- `docs/ARCHITECTURE.md`
- `app/src/main/java/com/lsync/app/notification/PaymentNotificationParser.kt` (Step 0에서 생성됨)
- `app/src/main/java/com/lsync/app/notification/AlarmReceiver.kt` (알림 채널 생성 패턴 참고)
- `app/src/main/java/com/lsync/app/data/repository/FinanceRepository.kt` (create() 시그니처 확인)
- `app/src/main/AndroidManifest.xml` (삽입 위치 확인)
- `app/src/main/java/com/lsync/app/MainActivity.kt` (패키지명 확인)

## 작업

### 1. PaymentNotificationService 생성

`app/src/main/java/com/lsync/app/notification/PaymentNotificationService.kt`를 신규 생성하라.

```kotlin
@AndroidEntryPoint
class PaymentNotificationService : NotificationListenerService() {

    @Inject lateinit var financeRepository: FinanceRepository

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNotificationPosted(sbn: StatusBarNotification)

    override fun onListenerConnected()

    override fun onDestroy()

    private fun postConfirmationNotification(amount: Long)

    private fun ensurePaymentChannel()

    companion object {
        const val PAYMENT_CHANNEL_ID = "lsync_payment_auto"
        const val PAYMENT_CHANNEL_NAME = "결제 자동 등록"
    }
}
```

**onNotificationPosted() 구현 규칙:**
1. `sbn.packageName`으로 packageName 추출.
2. `sbn.notification.extras`에서 `Notification.EXTRA_TITLE`(title)과 `Notification.EXTRA_TEXT`(text) 추출. null이면 빈 문자열로 처리.
3. `PaymentNotificationParser.parse(packageName, title, text)` 호출. `null`이면 즉시 return.
4. `serviceScope.launch { }` 안에서:
   - `financeRepository.create("EXPENSE", parsed.amount, parsed.category, LocalDate.now().toString(), parsed.note)` 호출.
   - 완료 후 `postConfirmationNotification(parsed.amount)` 호출.

**onDestroy():** `serviceScope.cancel()` 호출.

**postConfirmationNotification():**
- `ensurePaymentChannel()` 호출.
- `NotificationCompat.Builder(this, PAYMENT_CHANNEL_ID)`:
  - title: `"결제 자동 등록됨"`
  - text: `"%,d원 가계부에 추가되었습니다".format(amount)`
  - priority: `NotificationCompat.PRIORITY_DEFAULT` (HIGH 금지 — 정보성 알림)
  - `setAutoCancel(true)`
  - small icon: `R.drawable.ic_notification` (기존 AlarmReceiver와 동일한 아이콘)
- Notification ID: `PAYMENT_CHANNEL_ID.hashCode()`

**ensurePaymentChannel():**
- Android O+ (`Build.VERSION.SDK_INT >= Build.VERSION_CODES.O`)에서만 채널 생성.
- `NotificationChannel(PAYMENT_CHANNEL_ID, PAYMENT_CHANNEL_NAME, NotificationManager.IMPORTANCE_DEFAULT)`.
- 기존 `lsync_alarms` 채널과 별도 채널 — 절대 혼용하지 마라.

**날짜:** `LocalDate.now().toString()` — 타임존 변환 없음.

### 2. AndroidManifest.xml 수정

`BootReceiver` `<receiver>` 블록 아래에 추가하라:

```xml
<service
    android:name=".notification.PaymentNotificationService"
    android:exported="true"
    android:permission="android.permission.BIND_NOTIFICATION_LISTENER_SERVICE">
    <intent-filter>
        <action android:name="android.service.notification.NotificationListenerService" />
    </intent-filter>
</service>
```

## Acceptance Criteria

```bash
./gradlew assembleDebug   # 컴파일 에러 없음, Manifest merge 성공
```

## 검증 절차

1. 위 AC 커맨드를 실행한다.
2. 체크리스트:
   - `PaymentNotificationService`에 `@AndroidEntryPoint`가 있는가?
   - `serviceScope`가 `SupervisorJob() + Dispatchers.IO`로 생성되고 `onDestroy()`에서 취소되는가?
   - 날짜가 `LocalDate.now().toString()`인가? (타임존 변환 없음)
   - 확인 알림 채널이 `lsync_payment_auto`이고 `IMPORTANCE_DEFAULT`인가?
   - AndroidManifest에 서비스가 올바르게 선언됐는가?
   - CLAUDE.md CRITICAL 규칙을 위반하지 않았는가?
3. 결과에 따라 `phases/3-payment-notification/index.json`의 step 1을 업데이트한다:
   - 성공 → `"status": "completed"`, `"summary": "산출물 한 줄 요약"`
   - 실패 → `"status": "error"`, `"error_message": "구체적 에러 내용"`

## 금지사항

- `BIND_NOTIFICATION_LISTENER_SERVICE`를 `<uses-permission>`에 추가하지 마라. 이유: 서비스 선언의 `android:permission` 속성이지, 앱이 요청하는 권한이 아니다. 추가하면 lint 에러 발생.
- `GlobalScope`를 사용하지 마라. 이유: 서비스 종료 후에도 코루틴이 계속 실행되어 메모리 누수가 발생한다. `serviceScope`를 사용하라.
- 기존 `lsync_alarms` 채널을 재사용하지 마라. 이유: 기존 채널은 `IMPORTANCE_HIGH`(마감 알림용)이고, 결제 확인은 `IMPORTANCE_DEFAULT`여야 한다.
- `AlarmReceiver.kt`, `AlarmScheduler.kt`, `BootReceiver.kt`를 수정하지 마라. 이유: 이 step 범위 밖이다.
- `userId`를 직접 명시하지 마라. 이유: `FinanceRepository.create()`가 내부에서 `"local_user"`를 처리한다.
- 기존 코드를 리팩토링하지 마라. 이 step의 범위만 작업하라.
