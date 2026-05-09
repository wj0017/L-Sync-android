이 프로젝트의 변경 사항을 리뷰하라.

먼저 다음 문서들을 읽어라:
- `CLAUDE.md`
- `docs/ARCHITECTURE.md`
- `docs/TechSpec.md`

그런 다음 변경된 파일들을 확인하고, 아래 체크리스트로 검증하라:

## 체크리스트

1. **아키텍처 준수**: ARCHITECTURE.md에 정의된 디렉토리 구조를 따르고 있는가?
2. **CRITICAL 규칙**: CLAUDE.md의 CRITICAL 규칙을 위반하지 않았는가? (특히 Room SSOT, Todo↔Finance 생명주기)
3. **데이터 무결성**: Todo 삭제/미완료 시 Finance 데이터를 올바르게 처리하는가?
4. **Offline-First**: 네트워크 없이도 핵심 기능이 동작하는가? (UI가 Room Flow를 구독하는가?)
5. **Hilt 연결**: 새 ViewModel은 `@HiltViewModel`이 있는가? 새 DAO/Repository는 `AppModule.kt`에 `@Provides`가 추가됐는가?
6. **빌드 가능**: `./gradlew assembleDebug`가 에러 없이 통과하는가?

## 출력 형식

| 항목 | 결과 | 비고 |
|------|------|------|
| 아키텍처 준수 | ✅/❌ | {상세} |
| CRITICAL 규칙 | ✅/❌ | {상세} |
| 데이터 무결성 | ✅/❌ | {상세} |
| Offline-First | ✅/❌ | {상세} |
| Hilt 연결 | ✅/❌ | {상세} |
| 빌드 가능 | ✅/❌ | {상세} |

위반 사항이 있으면 수정 방안을 구체적으로 제시하라.
