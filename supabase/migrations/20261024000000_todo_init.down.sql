-- 回滾 20261024000000_todo_init.sql（會刪除所有待辦資料，執行前先匯出備份）
alter publication supabase_realtime drop table public.todo_lists, public.todo_tasks, public.todo_steps;
drop table if exists public.todo_steps;
drop table if exists public.todo_tasks;
drop table if exists public.todo_lists;
drop table if exists public.todo_members;
drop function if exists todo_private.touch();
drop function if exists todo_private.is_member();
drop schema if exists todo_private;
