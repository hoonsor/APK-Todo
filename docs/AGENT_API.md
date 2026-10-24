# Agent API — 待辦事項

狀態：**已實作**（v0.1.0，2026-10-24；語音總控說明書已收錄）。協定、SDK、撰寫指南、相容規則見語音總控的 `docs/AGENT_PROTOCOL.md`（唯一來源），本檔只列本 APP 的動作。
動作由 APP 的 Repository 執行（先寫 Room，再背景同步到 Supabase），所以斷網時語音照樣能新增與打勾。

| 動作 | risk | 參數 | 範例句 | 回傳 data |
|---|---|---|---|---|
| list_list | read | — | 我有哪些清單 | lists[{id,name,count}] |
| todo_list | read | scope?: my_day／important／planned／all／list，list?: 清單名稱，includeDone?: false | 我今天有哪些待辦／工作清單還有什麼／這週有哪些要到期 | tasks[{id,title,list,due,important}]、total |
| todo_add | reversible | title，list?（名稱，模糊比對；省略＝預設清單），due?（yyyy-MM-dd），remindAt?（yyyy-MM-ddTHH:mm），important?，myDay?，repeat?（daily／weekdays／weekly／monthly／yearly），note? | 待辦，明天下午三點前交人事月報／提醒我週五下午四點買牛奶，放日常清單／每週一早上九點開晨會，重要 | id、list；undo＝todo_delete |
| todo_complete | reversible | id? 或 title（模糊比對），undone?: false | 交人事月報那件做完了／把買牛奶打勾／取消剛剛那件的完成 | id；多筆符合回 AMBIGUOUS（candidates）；undo＝todo_complete undone=true |
| todo_delete | destructive | id | （只給 undo 用，不提供語音例句） | — |

summary 範例（會被直接唸出）：
- todo_add：「已在工作清單新增『交人事月報』，明天到期，下午三點提醒」
- todo_complete：「『買牛奶』完成了，還剩 3 件」；重複待辦：「完成了，下一次是下週一」
- todo_list：「我的一天有 4 件：交人事月報、買牛奶…」（超過 5 件只唸前 5 件與總數）

錯誤：
- 清單名稱找不到 → NOT_FOUND「找不到叫『家務』的清單，現在有：工作、日常、投資」
- 沒登入 → PERMISSION「待辦 APP 還沒登入，請先打開 APP 登入」
