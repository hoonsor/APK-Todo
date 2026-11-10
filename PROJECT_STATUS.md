# PROJECT_STATUS — 待辦事項（APK-Todo）

- 目前版本：v0.1.1（程式已 commit，尚未推 tag 發版；含 11/07 到期日＋清單色、11/09 仿 To Do 版面＋底部浮動面板）；已發佈：v0.1.0（共用簽章，Obtainium 可直接追蹤）
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
| v0.1.1 | 2026-11-07 | 見 git log | 小工具：待辦列右側顯示到期日（今天／明天／昨天／10/30，逾期紅、今天清單色、其他灰）；標題「清單名稱▾」與「＋」改用該清單在 APP 設定的顏色（智慧清單用固定色），切換清單跟著換；清單選擇視窗的色點改成各清單顏色、標示「目前」。11/09 再改：版面仿 Microsoft To Do（✓ 標誌、名稱⌄、重新整理／＋／設定三鈕、細線分隔、清單色圓圈、星號可切換重要）；「＋」、設定、切換清單改成桌面上的底部浮動面板 | 倬實際使用小工具後回報：看不到到期日、右上角顏色和 APP 清單色不一致；11/09 附 To Do 小工具截圖要求照它的樣式 |
| v0.1.0 | 2026-10-24 | 初始 | 第一版：見上方任務 | 倬要 Microsoft To Do 的替代品，並能用語音總控新增、查詢、打勾 |

## 待實機驗證（OPPO Reno 14F，請用編號回報）
1. [ ] Email 驗證碼登入、開通後同步正常
2. [ ] 新增、打勾、星號、刪除＋復原、移動清單
3. [ ] 依到期分段、各段收合、手動排序拖曳 ≡
4. [ ] 長按多選：完成、我的一天、移動、刪除
5. [ ] 提醒準時響（螢幕關閉、APP 不在前景時），通知上「完成」「10 分鐘後再提醒」有效
6. [ ] 重複待辦打勾後產生下一次（每月 1/31 的情況）
7. [ ] 小工具：切換清單、打勾、＋新增、點名稱開詳細；APP 改了小工具立刻更新；到期日與清單色正確、星號切換重要、重新整理；＋／設定／清單名稱跳出底部面板，快速新增可選清單、到期日、提醒（v0.1.1）
8. [ ] 斷網時新增／打勾，連網後自動同步；兩支手機或手機＋網頁同時開，幾秒內互相更新
9. [ ] 語音總控：「待辦，明天下午三點前交人事月報」「我今天有哪些待辦」「交人事月報那件做完了」
10. [ ] ColorOS 開機後提醒仍會響
11. [ ] 深色畫面、字級、間距與設計稿一致

## 已知問題
- 模擬器（API 37）以 LAB 預覽模式驗證過：我的一天（空狀態與分段）、清單總覽、工作清單分段、詳細頁、完成重複待辦產生下一次、多選＋刪除＋復原、小工具切換清單與打勾、測試通知、語音總控掃描到 5 個動作並能呼叫。**登入、同步、Realtime、精準提醒實際響鈴尚未在裝置上驗證**（需要 Supabase 後台設定與倬的信箱）
- 手動排序拖曳以固定列高估算位置，列高差很多（兩行標題）時落點可能差一格
- 網頁版尚未開始

## 下一步
0. 倬同意後推 `v0.1.1` tag 發版（小工具到期日＋清單色），手機更新後實機驗證第 7 項
1. 倬執行 `setup-hoonsor-signing.ps1` → 重跑 CI（Actions 頁按 Re-run，或推一個 commit）
2. 倬完成 Supabase 後台設定 → 登入 → Claude 加入 todo_members → 實機驗證清單
3. 啟動網頁版工作階段（`todo-web`）
4. 備份四件套

## ♻️ 可回收候選
- [ ] Supabase Email 驗證碼登入＋Keystore 權杖保存（Android 版）｜app/src/main/java/tw/hoonsor/todo/sync/Supabase.kt｜模組庫的 supabase-email-otp 只有 Next.js 版
- [ ] 離線優先 outbox 同步引擎（PostgREST PATCH＋游標拉取）｜sync/SyncEngine.kt、sync/Mapping.kt｜任何「Android＋Supabase」APP 都能用
- [ ] SortKey 字串排序鍵｜domain/Rules.kt｜拖曳排序通用

## 交接紀錄
### 2026-11-10／電腦 A
- 完成：倬要小工具多顯示幾筆。改成緊湊版：列高 52→40dp、待辦字 15→14sp、到期日 12→11sp、標題 17→16sp，圓圈 20dp、星號 18dp，標題列按鈕 40→36dp，外框上下內距 6→4dp
- 決定＋原因：圓圈與星號的點按區保留 40dp（倬說不要小到難點），所以列高不再往下壓
- 驗證：測試全過、assembleRelease 成功；模擬器 LAB 截圖確認版面
- 未完成：倬手機實測；推 `v0.1.1` tag 發版（要先問倬）

### 2026-11-09（續）／電腦 A
- 完成：倬在手機測了 latest（To Do 風格版面 OK），再附 To Do 截圖：按小工具的＋、設定會在桌面跳出底部浮動面板。新增 `widget/WidgetSheetActivity.kt`（透明 Activity＋底部面板，三種模式 add／settings／lists），取代舊的 `WidgetListPickerActivity`（同一檔案裡的類別，已移除；Manifest 一併換掉）
- 快速新增：圓圈＋輸入框（自動彈出鍵盤）＋↑送出；清單、設定到期日（今天／明天／下週一／選日期）、提醒我（重用 DetailScreen 的 `DueDatePicker`、`ReminderPicker`，改成 internal）；送出後清空可連續新增，`source = "widget"`
- 設定面板：倬說「選項維持原本設計」，所以不照搬 To Do 的不透明度、字型大小等選項；只放顯示的清單、同步狀態＋立即同步、開啟 APP 完整設定
- 驗證：34 個測試全過、assembleRelease 成功；模擬器 LAB 驗證三種面板、新增「明天到期」的待辦後小工具立刻出現
- 未完成：倬手機實測；推 `v0.1.1` tag 發版（要先問倬）

### 2026-11-09／電腦 A
- 完成：小工具版面改成仿 Microsoft To Do（倬附 To Do 小工具截圖）：標題列 ✓ 標誌＋清單名稱⌄＋件數，右邊重新整理、＋、設定；待辦列去掉卡片底改細線分隔、圓圈與星號用清單色、星號可直接切換重要；到期日保留在星號左邊
- 新增：`StarAction`（切換重要）、`RefreshAction`（已登入就立即同步，再重畫）、`MainActivity.EXTRA_SETTINGS`（小工具開設定頁）；手繪圖示 `ic_w_*`（標誌、下拉、重新整理、設定、星號空心／實心），`ic_plus`、`ic_circle` 改白色底圖由 ColorFilter 上色（只有小工具用）
- 驗證：34 個測試全過、assembleRelease 成功；模擬器 LAB 預覽看過版面、星號切換、設定鍵開設定頁、重新整理有觸發
- 決定＋原因：清單名稱仍用清單色（To Do 是白字，但 11/07 倬要求標題用清單色，兩者並存）；保留件數與到期日（To Do 小工具沒有，但是倬 11/07 要的資訊）
- 未完成：push 與推 `v0.1.1` tag（要先問倬）；實機驗證第 7 項
- 環境注意：模擬器 debug 版小工具點擊後約 3～4 秒才重畫，屬正常

### 2026-11-07／電腦 A
- 完成：v0.1.1 小工具到期日＋清單色（`widget/TodoWidget.kt`）；新增 `WidgetTest.kt`（8 個，總共 34 個測試全過）；模擬器 LAB 預覽模式驗證「我的一天」（金色、今天）與「日常生活」（綠色、昨天紅、明天灰、11/16 灰）
- 決定＋原因：
  - 日期文字重用 `ui/Common.kt` 的 `dayLabel`，加 `weekday` 參數；小工具省略星期（列寬小），APP 照舊顯示「10/30（五）」
  - 標題與＋用 `listColor`（清單標題色 75%／70%，與 APP 清單畫面一致）；選擇視窗的色點用新增的 `listDotColor`（點 60%／62%）
  - 「＋」的 drawable 原本把藍色寫死，改用 Glance `ColorFilter.tint`，不必為每個顏色做一個 drawable
  - 今天到期用清單色（對應 APP 依到期分段「今天（清單色）」）
- 放棄：在小工具另寫一套日期格式與顏色表（會跟 APP 分歧）
- 未完成：推 `v0.1.1` tag 發版（要先問倬）；實機驗證第 7 項
- 下一步：倬同意後 `git push` 與 `git tag v0.1.1 && git push origin v0.1.1`，手機用 Obtainium 更新後看小工具
- 環境注意：本機 Gradle daemon 曾在 G1 GC 閃退（EXCEPTION_ACCESS_VIOLATION），重跑一次就過，與程式無關

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
