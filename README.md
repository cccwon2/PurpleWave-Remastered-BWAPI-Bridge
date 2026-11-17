# PurpleWave Remastered BWAPI Bridge

PurpleWave AI를 StarCraft: Remastered에서 구동하기 위한 BWAPI/JBWAPI 연동 프로젝트입니다. 연결 진단부터 AI 명령 전달과 실게임 검증까지, 연동에 필요한 도구와 문서를 관리합니다.

이 저장소에는 연결 진단용 Probe, 실행·검증 스크립트, 호환성 조사 및 AI 연결 문서가 있습니다. 실제 Remastered 측 서버·브리지 구현은 별도 [Pluto-Remastered-v2](https://github.com/cccwon2/Pluto-Remastered-v2) 저장소에서 관리합니다. 이 저장소만 빌드한다고 전체 PurpleWave AI 실행 환경이 완성되는 것은 아닙니다.

## 1. 구조와 현재 상태

### 구성 요소

```text
PurpleWave AI / 연결 진단 Probe
              │ JBWAPI
              ▼
Pluto BWAPI 호환 서버·브리지
              │
              ▼
StarCraft: Remastered
```

위 그림은 구성 관계를 나타냅니다. Probe와 PurpleWave AI는 서로 다른 역할의 클라이언트이며, 동시 실행 지침이 아닙니다.

| 구성 요소 | 역할 |
|---|---|
| PurpleWave AI | 전략 판단 및 생산·채집·건설·이동·전투 명령 |
| JBWAPI | Java/Scala 클라이언트의 BWAPI 인터페이스 |
| Pluto | Remastered 측 게임 데이터 제공 및 명령 연결 |
| 이 저장소의 Probe | 읽기 전용 연결·데이터 수신 진단 |
| 이 저장소의 스크립트·문서 | 실행 준비, 테스트, 호환성 및 검증 기록 |

### 진행 상황 — 2026-11-17 인계 기준

| 항목 | 상태 |
|---|---|
| 로컬 PurpleWave 플레이 | 사용자 관찰로 생산·채집·건설·이동·전투 동작 확인 |
| 마지막 적 건물 제거 | 구석 건물 하나를 제거하지 못하는 현상, 원인 미확정 |
| 경기 종료까지 검증 | 미완료 |
| regions1 소스 | Pluto PR #45로 보존, 인계 시점 병합 보류 |
| 실게임 DLL과 PR 소스 대응 | 미확인 |
| 해당 PR HEAD 재빌드 | VM의 libstdc++ 링크 문제로 미완료 |

위 상태는 사용자 관찰 및 인계 기록입니다. 전체 PurpleWave 호환성이나 모든 맵에서의 정상 동작을 보장하지 않습니다.

[Pluto PR #45](https://github.com/cccwon2/Pluto-Remastered-v2/pull/45)의 인계 HEAD는 `4ef34187fcbe09e9dc7a57f6465f459c0a34d4d4`입니다. 이전 `region_shim_contract_test` PASS와 이 HEAD의 검증은 구분해야 합니다.

`regions1`은 누락된 지역 정보를 채우기 위해 맵을 하나의 연결된 지역으로 취급하는 임시 호환 처리입니다. 정식 지역·경로 구현이 아니며, 분리된 지형을 연결된 것으로 잘못 판단할 수 있습니다. 마지막 건물 문제와의 인과관계는 확인되지 않았습니다.

## 2. PurpleWave AI 연결

전체 AI 실행 경로는 [PurpleWave AI 명령 경로](docs/purplewave-ai-command-path.md)를 참고하세요. Pluto의 `jbwapi_server_v2`를 통한 독점 AI 명령 경로를 다루며, 아래 Probe 실행 명령과는 별도입니다.

- [Windows 실환경 가이드](docs/windows-real-env-guide.md): 환경 준비와 운영 체크리스트
- [호환성 감사](docs/compatibility-audit.md):): 지원 범위와 제약 검토
- [브리지 후보 검토](docs/bridge-selection.md): 브리지 선택 배경
- [Probe 검증 기록](docs/first-probe-results.md): 연결 진단 결과

현재 동작하는 DLL/JAR는 보존하세요. 다음 검증은 산출물 백업 후 별도 작업 디렉터리에서 고정된 PR HEAD의 Windows 빌드·테스트·실게임 재검증 순서로 진행합니다. 문서별 작성 시점과 검증 대상을 확인하세요.

## 3. 연결 진단 도구 — Probe

Probe는 전체 연동을 점검하는 읽기 전용 도구입니다. 게임 데이터를 읽고 로그를 남기며, 이동·공격·채집·생산·건설 명령을 발행하거나 PurpleWave AI를 실행하지 않습니다. 게임 속도 변경 및 경기 자동 시작·종료도 수행하지 않습니다.

### 기준 버전과 요구 사항

- JBWAPI 고정 커밋: `d6003b0b3a6a27944c979fd8dbc6ec8e3c2c753f`. 실행 시 일치 여부를 확인합니다.
- JNA: PurpleWave 기준 `5.1.0`. JBWAPI POM의 `5.6.0`과 구분합니다.
- PowerShell 7 이상. 안전한 인수 전달에 `ProcessStartInfo.ArgumentList.Add`를 사용하며 Windows PowerShell 5.1은 해당 실행 환경으로 지원하지 않습니다.
- 실제 연결에는 JBWAPI 매핑·파이프와 클라이언트 버전 `10003`을 제공하는 Windows BWAPI 서버가 필요합니다.
- 게임 없는 Windows CI는 Java 8 x64 기준입니다. Remastered 환경의 Java 32·64비트 일반 호환성은 별도 검증 대상입니다.
- 임의 IPC 구조 도입, 고정 버전을 넘는 JBWAPI 업그레이드, 버전·해시 검사 우회 또는 후킹·인젝션 추가는 Probe의 범위가 아닙니다.

### 빌드

저장소 루트에서 실행합니다.

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

산출물: `target/connection-probe-0.1.0.jar`

### 실행

게임과 호환 브리지를 준비하고, 아래 예시 경로를 실제 설치 경로로 바꾸세요. 이 명령은 Probe만 실행합니다.

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
| `JavaPath` | Java 실행 파일 |
| `GameWorkingDirectory` | 게임 작업 디렉터리 |
| `LogDirectory` | 로그 저장 디렉터리 |
| `MatchStartWaitMs` | 경기 시작 대기 제한(ms) |
| `OverallRunTimeoutMs` | 전체 실행 제한(ms), `0`은 비활성화 |
| `StatusIntervalMs` | 상태 기록 간격(ms) |

`ConnectTimeoutMs`는 `MatchStartWaitMs`의 호환 별칭입니다. 경기 시작 대기이지 IPC 연결 성공 확인이 아닙니다. 공개 JBWAPI에는 연결 콜백이 없습니다.

공백·한글 경로는 `ArgumentList.Add`로 보존합니다. 스크립트에 사용자별 드라이브 경로를 하드코딩하지 마세요. `BWClient`에는 공개 중지 API가 없으므로 시간 초과 시 스크립트가 시작한 Probe PID만 종료합니다.

## 4. Probe 로그와 종료 코드

실행별 로그: `logs/connection-probe-<runId>.jsonl`. 모든 행에 `runId`를 기록합니다.

```text
timestamp, level, event, runId, elapsedMs, frame, playerId, race,
minerals, gas, supplyUsed, supplyTotal, selfUnitCount, errorCode, message
```

- 경기 시작 시 프레임, 플레이어 ID, 종족, 자원, 보급량, 아군 유닛 수를 읽습니다.
- 읽을 수 없는 값은 `null`이며, 필드명과 원인을 빈도 제한된 WARN 로그로 남깁니다.
- 보급량은 BWAPI 내부 단위입니다. 화면 표시량은 대략 내부 값의 절반입니다.
- 이벤트: `PROCESS_STARTED`, `WAITING_FOR_BRIDGE`, `MATCH_STARTED`, `MATCH_ENDED`, `FAILED`. `CONNECTED`는 기록하지 않습니다.

분류 가능한 오류 코드: `WINDOWS_API_UNAVAILABLE`, `MATCH_START_WAIT_EXCEEDED`, `NO_BRIDGE_OR_WAITING`, `OVERALL_RUN_TIMEOUT`, `JBWAPI_PIN_MISMATCH`.

| 종료 코드 | 의미 |
|---|---|
| 0 | Probe 오류 없이 완료 |
| 1 | 프로세스 시작 실패, 지원하지 않는 호스트 또는 잘못된 Java |
| 2 | 사용법 오류 또는 JAR 누락 |
| 3 | 설정 범위 또는 파싱 오류 |
| 4 | 로그 디렉터리 생성 실패 |
| 5 | 잘못된 작업 디렉터리 |
| 6 | 경기 시작 대기 또는 전체 실행 시간 초과 |
| 7 | 실행 실패. 예: Linux에서 Windows API 사용 불가 |
| 8 | JBWAPI 서브모듈 SHA 불일치 |

## 5. 테스트와 검증 원칙

```bash
mvn test
mvn verify
```

- `mvn test`: 단위·프로세스 내부 오프라인 테스트
- `mvn verify`: 브리지 없는 프로세스 통합 테스트 포함

```powershell
pwsh -File src/test/powershell/Harden.Tests.ps1
pwsh -File src/test/powershell/ScriptSmoke.Tests.ps1
```

`Harden.Tests.ps1`은 supervisor·인수 전달·시간 초과 회귀를 MOCK 환경에서 확인합니다. 게임 없는 Windows CI는 `.github/workflows/windows-probe.yml`에 있습니다.

빌드 성공, MOCK·오프라인 테스트 PASS, Probe 실데이터 수신, PurpleWave 실게임 동작, 경기 종료 검증은 서로 다른 단계입니다. 한 단계의 성공을 전체 호환성 PASS로 확대 해석하지 마세요.
