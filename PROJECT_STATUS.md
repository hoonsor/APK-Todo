# PROJECT_STATUS — 待辦事項（APK-Todo）

- 目前版本：v0.1.0（正式版已發佈到 Releases，共用簽章，Obtainium 可直接追蹤；尚未實機驗證）
- 程式碼：`D:\dev\APK-Todo`；專案資料夾 `D:\01-Project\57-待辦事項`（CODE_LOCATION.txt 指過來）
- 套件：`tw.hoonsor.todo`（LAB：`tw.hoonsor.todo.lab`）
- 後端：Supabase 專案 Raphael_Evaluation（`nhjyucwvqdkihklbaleo`，東京），表 `todo_members`、`todo_lists`、`todo_tasks`、`todo_steps`
- 設計稿：https://claude.ai/artifact/JsZCDwjHePjyJ4wjSHv4Vs（方案 A）

## 任務
- [x] 意圖訪談、FEATURES.md 確認（2026-10-24）
- [x] 版面三方案 → 選定方案 A＋依到期分段；補清單總覽、多選、空狀態、網頁三欄
- [x] 資料表與 RLS 套用到 Supabase；RLS 用臨時帳號在交易內驗證 9 項後回滾
- [x] Android：清單、智慧清單、待辦、詳細頁（步驟、我的一天、提醒、到期、重複、移動、備註、刪除）、排序（含手動拖曳）、到期分段、已完成收合、長按多選、刪除復原、搜尋、空狀態建議
- [x] 離線優先同步（Room＋outbox、只送改過欄位、Realtime、WorkManager）
- [x] Email 驗證碼登入、Keystore 保存權杖、成員白名單提示
- [x] 提醒通知（精準鬧鐘、完成／延後 10 分鐘、開機重排）
- [x] Glance 桌面小工具（切換清單、打勾、新增、各自記住清單）＋設定頁「加入桌面」
- [x] Agent API（list_list、todo_list、todo_add、todo_complete、todo_delete）＋測試台；語音總控已能掃描到
- [x] 語音總控說明書收錄待辦事項（hoonsor/APK-VoiceHub 22f4e9a）
- [x] App 內更新（鬧鐘 APP 模組）、健康檢查頁
- [x] 單元測試 26 個（SortKey、Repeat、到期分段、名稱比對、智慧清單、建議、搜尋、JSON 對應、manifest 一致性）
- [x] 建立 public repo `hoonsor/APK-Todo`、CI 綠燈、發佈 latest（2026-10-24）
- [x] `hoonsor/APK-Todo` 已加進 `setup-hoonsor-signing.ps1` 的 `$Repos`
- [ ] 倬在電腦 A 執行 `setup-hoonsor-signing.ps1`（寫入共用簽章 Secrets 後重跑 CI，latest 才是正式簽章，才能和語音總控互相呼叫）
- [ ] Supabase 後台設定（倬，見下方）
- [ ] 倬第一次登入後，把帳號加進 `todo_members`
- [ ] 實機驗證
- [ ] 備份四件套（T62，P1）
- [ ] 網頁版（另一個工作階段：`hoonsor/todo-web`）
- [ ] Obsidian 唯讀快照（第三階段）

## 倬要做的設定（Supabase 後台，Raphael_Evaluation 專案）
1. Authentication › Emails › Templates：**Magic Link** 範本內文加上 `{{ .Token }}`（驗證碼），否則信裡只有連結、APP 沒辦法輸入
2. Authentication › SMTP Settings：建議設自訂 SMTP（Gmail：`smtp.gmail.com`、Port 587、應用程式密碼）；預設寄信額度很低
3. 第一次在 APP 登入後，設定頁會顯示使用者 ID → 交給 Claude 執行：
   `insert into public.todo_members (user_id, email) values ('<使用者 ID>', '<Email>');`

## 版本歷程
| 版本 | 日期 | commit | 變更 | 為什麼 |
|---|---|---|---|---|
| v0.1.0 | 2026-10-24 | 初始 | 第一版：見上方任務 | 倬要 Microsoft To Do 的替代品，並能用語音總控新增、查詢、打勾 |

## 待實機驗證（OPPO Reno 14F，請用編號回報）
1. [ ] Email 驗證碼登入、開通後同步正常
2. [ ] 新增、打勾、星號、刪除＋復原、移動清單
3. [ ] 依到期分段、各段收合、手動排序拖曳 ≡
4. [ ] 長按多選：完成、我的一天、移動、刪除
5. [ ] 提醒準時響（螢幕關閉、APP 不在前景時），通知上「完成」「10 分鐘後再提醒」有效
6. [ ] 重複待辦打勾後產生下一次（每月 1/31 的情況）
7. [ ] 小工具：切換清單、打勾、＋新增、點名稱開詳細；APP 改了小工具立刻更新
8. [ ] 斷網時新增／打勾，連網後自動同步；兩支手機或手機＋網頁同時開，幾秒內互相更新
9. [ ] 語音總控：「待辦，明天下午三點前交人事月報」「我今天有哪些待辦」「交人事月報那件做完了」
10. [ ] ColorOS 開機後提醒仍會響
11. [ ] 深色畫面、字級、間距與設計稿一致

## 已知問題
- 模擬器（API 37）以 LAB 預覽模式驗證過：我的一天（空狀態與分段）、清單總覽、工作清單分段、詳細頁、完成重複待辦產生下一次、多選＋刪除＋復原、小工具切換清單與打勾、測試通知、語音總控掃描到 5 個動作並能呼叫。**登入、同步、Realtime、精準提醒實際響鈴尚未在裝置上驗證**（需要 Supabase 後台設定與倬的信箱）
- 手動排序拖曳以固定列高估算位置，列高差很多（兩行標題）時落點可能差一格
- 網頁版尚未開始

## 下一步
1. 倬執行 `setup-hoonsor-signing.ps1` → 重跑 CI（Actions 頁按 Re-run，或推一個 commit）
2. 倬完成 Supabase 後台設定 → 登入 → Claude 加入 todo_members → 實機驗證清單
3. 啟動網頁版工作階段（`todo-web`）
4. 備份四件套

## ♻️ 可回收候選
- [ ] Supabase Email 驗證碼登入＋Keystore 權杖保存（Android 版）｜app/src/main/java/tw/hoonsor/todo/sync/Supabase.kt｜模組庫的 supabase-email-otp 只有 Next.js 版
- [ ] 離線優先 outbox 同步引擎（PostgREST PATCH＋游標拉取）｜sync/SyncEngine.kt、sync/Mapping.kt｜任何「Android＋Supabase」APP 都能用
- [ ] SortKey 字串排序鍵｜domain/Rules.kt｜拖曳排序通用

## 交接紀錄
### 2026-10-24（續）／電腦 A
- 完成：建立 public repo、CI 綠燈並發佈 latest（debug 簽章）；修正相容檢查測試寫死語音總控動作的問題（兩個 repo 同步修正）
- 教訓：隨工具複製到各 APP 的測試不能讀所在 repo 的資料，要自帶範例

### 2026-10-24／電腦 A
- 完成：v0.1.0 全部程式、Supabase 資料表與 RLS、26 個單元測試、模擬器驗證、語音總控說明書、所有文件
- 決定＋原因：
  - Supabase 唯一來源、Obsidian 只看、電腦改待辦用網頁（倬 2026-10-24 決定；避免 Git 合併衝突）
  - 共用 Raphael_Evaluation（免費方案只能 2 個啟用專案；表名 todo_ 前綴＋RLS＋成員白名單隔開）
  - 只送改過欄位、伺服器設定 updated_at、重複待辦唯一索引去重（兩端同時操作也不會亂）
  - 語音總控透過 Agent API 操作，不直接連資料庫
  - LAB 預覽模式（adb 才能開、不同步、登入時清空本機）：讓模擬器能不登入就測完所有畫面
- 放棄＋原因：
  - Obsidian 插件直接改資料庫：倬改成用網頁改，Obsidian 只看
  - Obsidian MD＋Git 當資料庫：修改既有待辦（打勾）會一直衝突
  - 新建 Supabase 專案：要暫停既有專案或付費
- 未完成：GitHub repo、共用簽章、Supabase 後台設定、實機驗證、網頁版、備份
- 環境注意：本機建置用 `C:\Users\hoonsor\.jdks\jbr-21.0.11`；模擬器 `Medium_Phone_API_37.0`；沒有新增環境變數（publishable key 寫在 build.gradle.kts）
