# PurpleWave Remastered — BWAPI 연결 진단

StarCraft: Remastered 로컬 환경에서 JBWAPI 클라이언트와 BWAPI 호환 브리지의 연결·데이터 수신을 확인하는 **읽기 전용 진단 도구(Probe)**입니다.

이 저장소 자체가 PurpleWave 전체 포팅 프로젝트는 아니며, Remastered 브리지 소스나 바이너리를 포함하지 않습니다. 호환성 검토는 [호환성 감사 문서](docs/compatibility-audit.md)를 참고하세요.

## 구성과 역할

| 구성 요소 | 역할 |
|---|---|
| 이 저장소의 Probe | 경기 시작 감지, 게임 데이터 읽기, 실행별 진단 로그 기록 |
| 외부 BWAPI 호환 브리지 | Remastered와 JBWAPI 사이의 데이터·명령 연결 |
| PurpleWave AI | 별도로 실행하는 전략·유닛 제어 클라이언트 |

전체 AI 실행은 Probe와 별도입니다. Pluto의 `jbwapi_server_v2`를 통한 독점 AI 명령 경로는 [PurpleWave AI 연결 문서](docs/purplewave-ai-command-path.md)를 참고하세요.

## 주요 기능

- PurpleWave와 동일한 JBWAPI 커밋 `d6003b0b3a6a27944c979fd8dbc6ec8e3c2c753f`를 고정하고 실행 시 확인합니다.
- 기준 환경 재현을 위해 JNA 버전을 PurpleWave에서 사용하는 `5.1.0`으로 맞춥니다. JBWAPI POM의 `5.6.0`과 구분하세요.
- 실행마다 `connection-probe-<runId>.jsonl`을 만들고 모든 행에 `runId`를 기록합니다.
- 경기 시작까지 대기하며, 별도로 전체 실행 제한 시간을 설정할 수 있습니다. 전체 실행 제한의 `0`은 비활성화입니다.
- 경기 시작 시 프레임, 플레이어 ID, 종족, 미네랄, 가스, 보급량, 아군 유닛 수를 읽습니다.
- 필드 읽기 실패 시 해당 값은 `null`로 남기고, 필드명과 원인을 제한된 빈도로 경고 로그에 기록합니다.
- 시간 초과 시 스크립트가 시작한 Probe 프로세스만 종료합니다.

경기 시작 대기는 IPC 연결 성공 확인과 다릅니다. 공개 JBWAPI에는 연결 콜백이 없으므로 Probe는 `CONNECTED` 이벤트를 기록하지 않습니다.

### 하지 않는 작업

- 이동·공격·채집·생산·건설 명령 발행
- 게임 속도 변경, 경기 자동 시작·종료, PurpleWave AI 실행
- 임의 IPC 이름·구조체 도입 또는 고정 버전을 넘는 JBWAPI 업그레이드
- 버전·해시 검사 우회, 후킹·인젝션 추가

## 실행 환경과 빌드

- 실행 스크립트에는 **PowerShell 7 이상**이 필요합니다.
- 안전한 인수 전달을 위해 `ProcessStartInfo.ArgumentList.Add`를 사용합니다. Windows PowerShell 5.1은 경로 안전성을 보장하는 실행 환경으로 지원하지 않습니다.
- 게임 연결에는 JBWAPI 매핑·파이프와 클라이언트 버전 `10003`을 제공하는 Windows BWAPI 서버가 필요합니다.
- 게임 없는 Windows CI는 Java 8 x64를 기준 환경으로 사용합니다. 이것만으로 실제 Remastered 환경의 Java 비트수 호환성을 보장하지는 않습니다.

저장소 루트에서 실행하세요.

```powershell
git submodule update --init --recursive
pwsh -File scripts/Test-Environment.ps1
pwsh -File scripts/Build-Probe.ps1
```

Maven으로 직접 빌드할 수도 있습니다.

```bash
git submodule update --init --recursive
mvn verify
```

생성되는 JAR: `target/connection-probe-0.1.0.jar`

## Probe 실행

Windows에서 게임과 호환 브리지를 준비한 뒤 실행합니다. 아래 Java 및 게임 경로는 실제 설치 경로로 바꾸세요.

```powershell
pwsh -File scripts/Run-Probe.ps1 `
  -JavaPath "C:\Path\To\java.exe" `
  -GameWorkingDirectory "C:\Path\To\StarCraft" `
  -LogDirectory ".\logs" `
  -MatchStartWaitMs 60000 `
  -OverallRunTimeoutMs 0 `
  -StatusIntervalMs 1000
```

| 옵션 | 의미 |
|---|---|
| `JavaPath` | 사용할 Java 실행 파일 |
| `GameWorkingDirectory` | 게임 작업 디렉터리 |
| `LogDirectory` | 진단 로그 저장 디렉터리 |
| `MatchStartWaitMs` | 경기 시작 대기 제한 시간(ms) |
| `OverallRunTimeoutMs` | 전체 실행 제한 시간(ms), `0`이면 비활성화 |
| `StatusIntervalMs` | 상태 기록 간격(ms) |

`ConnectTimeoutMs`는 기존 호환성을 위한 `MatchStartWaitMs`의 별칭입니다. 이름과 달리 IPC 연결 시간이 아니라 경기 시작 대기 시간을 뜻합니다.

공백이나 한글이 포함된 경로는 `ArgumentList.Add`로 보존합니다. 저장소의 스크립트에는 사용자별 드라이브 경로를 하드코딩하지 마세요.

### 종료 코드

| 코드 | 의미 |
|---|---|
| 0 | Probe 오류 없이 완료. 예: 경기 종료 후 클라이언트 반환 |
| 1 | 프로세스 시작 실패, 지원하지 않는 호스트 또는 잘못된 Java |
| 2 | 사용법 오류 또는 JAR 누락 |
| 3 | 설정 범위 또는 파싱 오류 |
| 4 | 로그 디렉터리 생성 실패 |
| 5 | 잘못된 작업 디렉터리 |
| 6 | 경기 시작 대기 또는 전체 실행 시간 초과 |
| 7 | 실행 실패. 예: Linux에서 Windows API 사용 불가 |
| 8 | JBWAPI 서브모듈 SHA가 고정 커밋과 불일치 |

## 로그 형식

로그 경로: `logs/connection-probe-<runId>.jsonl`

주요 필드:

```text
timestamp, level, event, runId, elapsedMs, frame, playerId, race,
minerals, gas, supplyUsed, supplyTotal, selfUnitCount, errorCode, message
```

- 읽을 수 없는 값은 `null`입니다. 해당 필드는 빈도 제한이 적용된 WARN 로그로 표시됩니다.
- `supplyUsed`와 `supplyTotal`은 BWAPI 내부 단위입니다. 화면 표시량은 대략 내부 값의 절반입니다.
- 이벤트: `PROCESS_STARTED`, `WAITING_FOR_BRIDGE`, `MATCH_STARTED`, `MATCH_ENDED`, `FAILED`
- `CONNECTED` 이벤트는 발생시키지 않습니다.

분류 가능한 오류 코드:

```text
WINDOWS_API_UNAVAILABLE
MATCH_START_WAIT_EXCEEDED
NO_BRIDGE_OR_WAITING
OVERALL_RUN_TIMEOUT
JBWAPI_PIN_MISMATCH
```

## 테스트와 검증 범위

```bash
mvn test
mvn verify
```

- `mvn test`: 단위 테스트 및 프로세스 내부 오프라인 테스트
- `mvn verify`: 브리지 없이 수행하는 프로세스 통합 테스트 포함

PowerShell 스크립트 테스트:

```powershell
pwsh -File src/test/powershell/Harden.Tests.ps1
pwsh -File src/test/powershell/ScriptSmoke.Tests.ps1
```

`Harden.Tests.ps1`은 supervisor·인수 전달·시간 초과 회귀를 MOCK 환경에서 확인합니다. 게임 없는 Windows CI 설정은 `.github/workflows/windows-probe.yml`에 있습니다.

**MOCK·오프라인 테스트 통과는 Remastered 실게임 호환성 검증과 다릅니다.** 빌드 성공, Probe 데이터 수신, 전체 AI 동작, 경기 종료 검증을 각각 구분해야 합니다.

### 사용자 실게임 관찰 — 2026-11-17

- 별도 PurpleWave·Pluto 로컬 구성에서 생산·채집·건설·이동·전투 동작이 사용자에게 확인됐습니다. 이 저장소의 Probe가 해당 명령을 실행했다는 뜻은 아닙니다.
- 구석의 남은 건물 하나를 제거하지 못해 경기가 끝나지 않는 현상이 있으며, 원인은 미확정입니다.
- 관련 Pluto 변경은 [regions1 PR #45](https://github.com/cccwon2/Pluto-Remastered-v2/pull/45)에 보존됐습니다. 인계 시점의 HEAD는 `4ef34187fcbe09e9dc7a57f6465f459c0a34d4d4`이며, 당시 병합은 보류됐습니다.
- `regions1`은 맵을 하나의 연결된 지역으로 취급하는 임시 호환 처리입니다. 정식 지역·경로 구현이 아니며, 분리 지형을 연결된 것으로 잘못 판단할 수 있습니다.
- PC 실게임 DLL과 해당 PR 소스의 대응은 미확인입니다. 이번 PR HEAD의 VM 재빌드는 libstdc++ 링크 문제로 완료되지 않았으며, 이전 계약 테스트 PASS를 이번 HEAD의 검증으로 간주하지 않습니다.
- 위 내용은 사용자 관찰과 인계 기록이며, 전체 PurpleWave 호환성 또는 경기 종료까지의 PASS 선언이 아닙니다.

## 제한 사항

- 브리지 구현은 이 저장소에 포함되지 않습니다.
- `BWClient`에는 공개 중지 API가 없습니다. 스크립트는 자신이 시작한 Probe PID만 종료합니다.
- Remastered 환경에서 Java 32비트·64비트 선택의 일반적 호환성은 검증되지 않았습니다.
- 현재 동작하는 DLL/JAR는 보존하고, 새 소스 검증은 백업 후 별도 작업 디렉터리에서 진행하세요.

## 관련 문서

- [호환성 감사](docs/compatibility-audit.md)
- [브리지 후보 검토](docs/bridge-selection.md)
- [Windows 실환경 실행 가이드](docs/windows-real-env-guide.md)
- [Probe 실게임 검증 기록](docs/first-probe-results.md)
- [PurpleWave AI 명령 경로](docs/purplewave-ai-command-path.md)

문서별 작성 시점과 검증 대상을 확인하세요. Probe 검증 결과를 전체 AI 검증 결과로 확대 해석하지 마세요.
