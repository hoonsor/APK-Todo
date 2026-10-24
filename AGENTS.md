# AGENTS.md — 待辦事項（APK-Todo，57 號專案）

> 給接手的 AI（Claude、Antigravity、Codex、DeepSeek Harness、B 電腦）。不看對話紀錄也要能接手。

## 專案目的
Microsoft To Do 的替代品。Supabase 是**唯一資料來源**，入口有四個：本 Android APP（含可切換清單的桌面小工具）、
電腦網頁（另一個 repo `hoonsor/todo-web`，D:\dev\todo-web）、語音總控（`hoonsor/APK-VoiceHub` 透過本 APP 的 Agent API 新增／查詢／打勾）。
Obsidian 之後只會顯示由 N100 產生的唯讀快照（第三階段，未做）。

## 先讀這些（唯一來源）
| 文件 | 內容 |
|---|---|
| `docs/FEATURES.md` | 功能清單與倬的確認紀錄 |
| `docs/DATA_MODEL.md` | 資料表、同步、排序鍵、到期分段、重複待辦規則。**網頁版也照這份，兩端規則必須一致** |
| `docs/DESIGN.md` | 方案 A 色票、字級、元件規則；設計稿連結 |
| `docs/AGENT_API.md` ＋ `docs/agent-manifest.json` | 語音動作。協定本身見語音總控的 `docs/AGENT_PROTOCOL.md` |
| `supabase/migrations/` | 已套用的 SQL（含回滾檔 `.down.sql`） |

## 技術棧
- Kotlin、Compose＋Material3、Room（本機快取＋outbox）、WorkManager（同步）、Glance（小工具）、OkHttp（REST＋Realtime WebSocket）
- minSdk 30／targetSdk 35；AGP 8.7.3、Kotlin 2.1.0、Gradle 8.11.1；版本集中在 `gradle/libs.versions.toml`
- flavor：`prod`（tw.hoonsor.todo，「待辦事項」）、`lab`（tw.hoonsor.todo.lab，「待辦事項 LAB」）
- Supabase 專案 Raphael_Evaluation（與拉斐爾評估網站共用，表名一律 `todo_` 前綴）；APP 內嵌 publishable key（設計上可公開，權限靠 RLS）

## 建置與測試
```powershell
$env:JAVA_HOME="C:\Users\hoonsor\.jdks\jbr-21.0.11"; .\gradlew.bat testProdDebugUnitTest assembleRelease
```
- **不要用 Android Studio 內附的 JBR 25**（Gradle 8.11 不支援）；程式碼放英文路徑
- 改了 Agent API 動作：`$env:UPDATE_MANIFEST="1"; .\gradlew.bat testProdDebugUnitTest` 重新產生 `docs/agent-manifest.json`，並同步改語音總控的 `docs/manual/MANUAL.md`
- CI：`.github/workflows/build.yml`（相容檢查 → 單元測試 → 正式版＋LAB 版；main → latest、vX.Y.Z tag → Release）；共用簽章由 Secrets 提供
- **LAB 預覽模式**（模擬器測畫面用，不用登入、不同步、會放 21 筆示範資料）：
  `adb shell am start -n tw.hoonsor.todo.lab/tw.hoonsor.todo.ui.MainActivity --ez preview true`。正式登入時會先清掉本機資料，預覽資料不會上傳

## 目錄
```
app/src/main/java/
├─ tw/hoonsor/agent/HoonsorAgent.kt   共用 Agent SDK（從語音總控複製，不要在這裡改）
└─ tw/hoonsor/todo/
   ├─ App.kt            Graph 單例；前景時連 Realtime、背景交給 WorkManager
   ├─ domain/           Rules.kt（SortKey、Repeat、DueSection、NameMatcher）、Views.kt（智慧清單、排序、分段、建議、搜尋）— 純邏輯、有測試
   ├─ data/             Db.kt（Room：lists、tasks、steps、outbox）、Repository.kt（唯一寫入入口）、Prefs.kt（設定、Keystore 金庫）
   ├─ sync/             Supabase.kt（Email 驗證碼登入、REST）、Mapping.kt（實體↔JSON、只送改過欄位）、SyncEngine.kt（推送／拉取、Worker、Realtime）
   ├─ reminder/         精準鬧鐘、通知（完成／延後 10 分鐘）、開機重排
   ├─ widget/           Glance 小工具、清單選擇視窗、打勾 callback
   ├─ agent/            TodoAgentCatalog（describe）、AgentProvider（list_list、todo_list、todo_add、todo_complete、todo_delete）
   ├─ update/           App 內更新（從鬧鐘 APP 複製）
   └─ ui/               AppState（導覽、復原）、各畫面、Icons（手繪）、theme（唯一色票）
```

## 關鍵設計決策（＋原因）
- **Supabase 是唯一來源，Obsidian 只看**：倬要 To Do 式的小工具、提醒、語音打勾，都是「改一筆的一個欄位」，資料庫天生適合；Git＋MD 會一直合併衝突（2026-10-24 與倬討論後決定）
- **只送改過的欄位（PATCH）**：兩端同時改同一筆的不同欄位都能保留；`updated_at` 由伺服器觸發器設定，不信手機時鐘
- **離線優先**：所有修改先寫 Room＋outbox，再背景推送；所以小工具、通知按鈕、語音在斷網時都能用
- **重複待辦由「完成的那一端」產生下一筆**，`(repeat_root, repeat_seq)` 唯一索引擋重複；收到 409 就丟掉本機那筆
- **語音總控不直接連 Supabase**，透過本 APP 的 Agent API（一份登入、一套權限）
- **提醒只由手機響**，網頁不推播，避免重複
- **小工具用 Glance＋狀態版本號**：Glance 小工具執行中時 `update` 只會重畫，不會重跑 `provideGlance`；所以每次 `refresh` 都在小工具狀態寫入新版本號，內容用 `LaunchedEffect(version)` 重新讀 Room
- **成員白名單 todo_members**：共用的 Supabase 專案裡還有其他應用，不能只靠「有登入」

## 絕對不要重踩的坑
- Glance 的 `background` 要 import `androidx.glance.background`（Compose 的同名函式會被誤用）
- Compose PathParser 不吃壓縮的弧線旗標（`a6 6 0 0112 0`），手繪圖示路徑要寫成 `0 0 1 12 0`
- 模擬器重裝 APP 後，桌面上的小工具可能被移除，要重新加入（設定頁有「加入桌面」）
- 用 adb 測「復原」時注意只有 10 秒
- `.gitignore` 已含 `*.jks`、`local.properties`；不要把任何 key 或 service role 金鑰放進 repo（publishable key 例外）

## 倬的偏好
繁體中文（台灣）、深色質感、HSL 色票、手繪圖示、長按多選、刪除可復原、每個設定有測試按鈕、做不到要直說。完整見 `36-Claude使用流程改善/AI-PROFILE.md`。

## Always／Ask／Never
- **Always**：改程式前 commit；改資料規則先改 `docs/DATA_MODEL.md` 並通知網頁版；改 Agent API 依語音總控 `AGENT_PROTOCOL.md` 的相容規則並更新說明書；每版更新 PROJECT_STATUS.md
- **Ask**：改資料表（新增 migration）、刪資料、對外公開 repo、改套件名、換簽章
- **Never**：在 APP 裡放 service role key；實體刪除資料（一律軟刪除）；用 `--force` push
