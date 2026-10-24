# 資料設計 — 待辦事項

狀態：**已套用**（2026-10-24，Supabase 專案 Raphael_Evaluation，`https://nhjyucwvqdkihklbaleo.supabase.co`，東京區）。
SQL 唯一來源：`supabase/migrations/`（本 repo）。改資料表一律新增 migration 檔，並同步改本文件。
**APP 與網頁都必須照本文件實作**，兩端的規則不一致就會互相覆蓋。

## 原則
- Supabase（Postgres）是唯一來源；Android APP 用 Room 當快取＋離線佇列（outbox）；網頁直接讀寫 Supabase
- 每筆資料都有 `id`（uuid v4，客戶端產生）、`created_at`、`updated_at`（**由伺服器觸發器設定**，客戶端送的值會被忽略）、`deleted_at`（軟刪除，不做實體刪除）
- 修改一律**只送有改的欄位**（PostgREST `PATCH ?id=eq.<id>`），所以兩端同時改同一筆的不同欄位時兩邊都保留；同一欄位以伺服器最後收到的為準
- 新增用 `POST`（`Prefer: resolution=merge-duplicates` 讓重送安全）
- 權限：RLS 只允許 `owner = auth.uid()` 且是 `todo_members` 的成員。新帳號第一次登入後要由管理者把 `user_id, email` 加進 `todo_members` 才能讀寫（APP 與網頁要顯示「帳號尚未開通」）
- 只有一位使用者，但 RLS 照開（這個 Supabase 專案還有其他應用）

## 資料表
| 表 | 主要欄位 |
|---|---|
| todo_members | user_id（auth.users）、email |
| todo_lists | id、owner、name（1–100 字）、color（色票索引）、icon、is_default、sort_key |
| todo_tasks | id、owner、list_id、title（1–500 字）、note、important、completed_at、due_date（date）、remind_at（timestamptz）、repeat（jsonb）、repeat_root、repeat_seq、my_day_date（date）、sort_key、source（app／web／voice／widget） |
| todo_steps | id、owner、task_id、title（1–300 字）、done、sort_key |

## 預設清單
- 每位使用者恰好一個 `is_default = true` 的清單，名稱「工作」，不能刪除、可以改名（資料庫有唯一索引保證只有一個）
- 第一次登入、同步完發現沒有預設清單時，由客戶端建立（id 隨機）。兩端同時建立時，後到的會被唯一索引擋下，客戶端重新拉取即可

## 排序鍵 sort_key（清單內手動排序）
- 字串比較排序（`ORDER BY sort_key, created_at`），字元集 `0-9a-z`（36 進位小數，`'m'` 約等於中間）
- 插入在 A、B 之間：產生一個字典序介於兩者之間的最短字串（`SortKey.between(a, b)`，見 APP 的 `SortKey.kt` 與測試）；放最前用 `between(null, first)`、最後用 `between(last, null)`
- 新增待辦預設放在**最上面**（同 To Do）

## 到期分段（顯示用，不存資料庫）
依裝置的當地日期：已逾期（due < 今天）、今天、明天、之後（> 明天）、沒有到期日。完成的待辦不參與分段，放在「已完成」。

## 我的一天
`my_day_date = 今天` 才算在我的一天；隔天自然失效，不需要排程清除。
空狀態的「建議」＝未完成且（已逾期 或 今天到期 或 昨天在我的一天但沒做完）的待辦。

## 重複待辦
`repeat` 格式：`{"freq": "daily|weekdays|weekly|monthly|yearly", "interval": 1, "weekdays": [1..7]}`（1＝週一，`weekdays` 只用於 weekly）。
- 完成一筆有 `repeat` 的待辦時，**完成它的那一端**建立下一筆：
  - `id` 新產生；`repeat_root` ＝ 原本的 `repeat_root ?? 原本的 id`；`repeat_seq` ＝ `(原本的 repeat_seq ?? 0) + 1`
  - 複製 title、note、important、list_id、repeat、步驟（全部改成未完成）；`my_day_date` 不複製
  - 到期日：從「原到期日（沒有就用完成當天）」依規則往後推一次（見下）；提醒時間：若原本有提醒，保留時刻、日期改成新的到期日（原本沒到期日就以提醒日期推算）
  - 資料庫唯一索引 `(repeat_root, repeat_seq)` 擋下重複產生；收到衝突錯誤＝另一端已經建立，忽略即可
- 取消完成**不會**刪除已產生的下一筆（同 To Do）
- 推算規則（`Repeat.next(date)`）：
  - daily：+interval 天
  - weekdays：下一個週一到週五
  - weekly：在 `weekdays` 裡找同一週之後的下一天；沒有就跳 interval 週後的第一個勾選日（`weekdays` 空＝原本那天的星期）
  - monthly：+interval 月，日期超過該月天數時用月底（1/31 → 2/28 → 3/28 照新的日期繼續）
  - yearly：+interval 年（2/29 → 2/28）

## 同步（Android APP）
1. 本機修改：寫 Room＋在 `outbox` 加一筆（insert＝整筆、update＝只有改的欄位），然後排一次立即同步
2. 推送：依 outbox 順序送出；成功就刪該筆 outbox；401 先刷新 token 再重試；其他錯誤保留下次再送
3. 拉取：每張表 `GET ?updated_at=gt.<游標>&order=updated_at.asc&limit=500`，游標＝已收到的最大 `updated_at`（伺服器時間）；**本機還有該筆的 outbox 時跳過**（等推送完再以伺服器版本為準）
4. 時機：APP 開啟與回到前景、每 15 分鐘（WorkManager）、本機修改後、Realtime 收到變更通知時（只在 APP 前景時連線）
5. 拉取後：更新小工具、重排提醒

## 提醒
只有 Android APP 會響（精準鬧鐘＋通知，含「完成」「延後 10 分鐘」按鈕）；網頁不推播。

## 登入
Supabase Email 驗證碼（OTP）。APP 的 refresh token 存 Keystore AES-GCM；語音總控透過 APP 的 Agent API 操作，不直接連 Supabase。
APP 內嵌的是 **publishable key**（設計上可公開，權限靠 RLS）。
