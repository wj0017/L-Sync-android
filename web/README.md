# L-Sync 웹

안드로이드 앱과 같은 Firebase 프로젝트의 일정·할 일·가계부 데이터를 보는 보조 클라이언트입니다. Google 계정으로 로그인하며, Firestore 규칙은 로그인한 사용자의 UID와 문서의 `userId`가 일치할 때만 접근을 허용합니다.

## 로컬 실행

1. Firebase Console에서 이 프로젝트의 Authentication → Sign-in method → Google을 활성화합니다.
2. Authentication → Settings → Authorized domains에 개발 주소(`localhost`)와 배포 도메인을 등록합니다.
3. Firebase 웹 앱 설정을 `web/.env.local`에 다음 이름으로 넣습니다. 이 파일은 Git에서 제외됩니다.

   ```text
   NEXT_PUBLIC_FIREBASE_API_KEY=
   NEXT_PUBLIC_FIREBASE_AUTH_DOMAIN=
   NEXT_PUBLIC_FIREBASE_PROJECT_ID=
   NEXT_PUBLIC_FIREBASE_STORAGE_BUCKET=
   NEXT_PUBLIC_FIREBASE_MESSAGING_SENDER_ID=
   NEXT_PUBLIC_FIREBASE_APP_ID=
   ```

4. `pnpm install` 후 `pnpm dev`를 실행하고 `http://localhost:3000`에서 앱과 같은 Google 계정으로 로그인합니다.

웹과 안드로이드는 동일한 Firebase 프로젝트를 사용해야 합니다. 웹은 Firestore에 올라온 데이터만 볼 수 있으며, 앱의 통독 계획과 메모는 로컬 전용입니다. 안드로이드는 앱 시작 또는 로그인 시 원격 데이터를 가져오므로 웹에서 작성한 항목을 앱에서 확인하려면 앱을 다시 실행합니다.

## 확인 명령

```bash
pnpm exec tsc --noEmit
pnpm build
```
