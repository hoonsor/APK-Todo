-- 待辦事項（57 號專案）初始資料表
-- 唯一來源：hoonsor/APK-Todo  supabase/migrations/。共用 Supabase 專案 Raphael_Evaluation，表名一律 todo_ 前綴。
-- 回滾：執行同資料夾的 20261024000000_todo_init.down.sql

create schema if not exists todo_private;

-- 成員白名單：只有列在這裡的帳號能讀寫（這個 Supabase 專案也有其他應用，不能只靠「登入過」）
create table public.todo_members (
  user_id uuid primary key references auth.users(id) on delete cascade,
  email text not null unique,
  created_at timestamptz not null default now()
);
alter table public.todo_members enable row level security;
create policy todo_members_self_read on public.todo_members
  for select to authenticated using (user_id = (select auth.uid()));

create or replace function todo_private.is_member() returns boolean
language sql stable security definer set search_path = '' as $$
  select exists (select 1 from public.todo_members m where m.user_id = (select auth.uid()));
$$;
revoke all on function todo_private.is_member() from public;
grant usage on schema todo_private to authenticated;
grant execute on function todo_private.is_member() to authenticated;

-- updated_at 一律由伺服器設定（同步以伺服器時間為準，不信任手機時鐘）
create or replace function todo_private.touch() returns trigger
language plpgsql set search_path = '' as $$
begin
  new.updated_at := now();
  if tg_op = 'INSERT' then
    new.created_at := coalesce(new.created_at, now());
  end if;
  return new;
end;
$$;

-- 清單
create table public.todo_lists (
  id uuid primary key,                                   -- 客戶端產生，離線新增也不會撞號
  owner uuid not null default auth.uid() references auth.users(id) on delete cascade,
  name text not null check (char_length(name) between 1 and 100),
  color smallint not null default 0,
  icon text,
  is_default boolean not null default false,             -- 「工作」預設清單，不能刪
  sort_key text not null default 'm',
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  deleted_at timestamptz
);
create unique index todo_lists_one_default on public.todo_lists (owner) where is_default and deleted_at is null;
create index todo_lists_sync on public.todo_lists (owner, updated_at);

-- 待辦
create table public.todo_tasks (
  id uuid primary key,
  owner uuid not null default auth.uid() references auth.users(id) on delete cascade,
  list_id uuid not null references public.todo_lists(id),
  title text not null check (char_length(title) between 1 and 500),
  note text not null default '',
  important boolean not null default false,
  completed_at timestamptz,
  due_date date,
  remind_at timestamptz,
  repeat jsonb,                                           -- {"freq":"daily|weekdays|weekly|monthly|yearly","interval":1,"weekdays":[1..7]}
  repeat_root uuid,                                       -- 重複系列的第一筆 id
  repeat_seq integer,                                     -- 第幾次；與 repeat_root 一起唯一，避免兩端同時完成而產生兩筆下一次
  my_day_date date,                                       -- 等於今天＝在「我的一天」
  sort_key text not null default 'm',
  source text not null default 'app' check (source in ('app', 'web', 'voice', 'widget')),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  deleted_at timestamptz
);
create unique index todo_tasks_repeat_once on public.todo_tasks (repeat_root, repeat_seq) where repeat_root is not null;
create index todo_tasks_sync on public.todo_tasks (owner, updated_at);
create index todo_tasks_list on public.todo_tasks (list_id);

-- 步驟
create table public.todo_steps (
  id uuid primary key,
  owner uuid not null default auth.uid() references auth.users(id) on delete cascade,
  task_id uuid not null references public.todo_tasks(id),
  title text not null check (char_length(title) between 1 and 300),
  done boolean not null default false,
  sort_key text not null default 'm',
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  deleted_at timestamptz
);
create index todo_steps_sync on public.todo_steps (owner, updated_at);
create index todo_steps_task on public.todo_steps (task_id);

create trigger todo_lists_touch before insert or update on public.todo_lists for each row execute function todo_private.touch();
create trigger todo_tasks_touch before insert or update on public.todo_tasks for each row execute function todo_private.touch();
create trigger todo_steps_touch before insert or update on public.todo_steps for each row execute function todo_private.touch();

-- RLS：只能讀寫自己的資料，而且必須是成員；不開放實體刪除（一律軟刪除）
alter table public.todo_lists enable row level security;
alter table public.todo_tasks enable row level security;
alter table public.todo_steps enable row level security;

create policy todo_lists_select on public.todo_lists for select to authenticated
  using (owner = (select auth.uid()) and (select todo_private.is_member()));
create policy todo_lists_insert on public.todo_lists for insert to authenticated
  with check (owner = (select auth.uid()) and (select todo_private.is_member()));
create policy todo_lists_update on public.todo_lists for update to authenticated
  using (owner = (select auth.uid()) and (select todo_private.is_member()))
  with check (owner = (select auth.uid()));

create policy todo_tasks_select on public.todo_tasks for select to authenticated
  using (owner = (select auth.uid()) and (select todo_private.is_member()));
create policy todo_tasks_insert on public.todo_tasks for insert to authenticated
  with check (owner = (select auth.uid()) and (select todo_private.is_member()));
create policy todo_tasks_update on public.todo_tasks for update to authenticated
  using (owner = (select auth.uid()) and (select todo_private.is_member()))
  with check (owner = (select auth.uid()));

create policy todo_steps_select on public.todo_steps for select to authenticated
  using (owner = (select auth.uid()) and (select todo_private.is_member()));
create policy todo_steps_insert on public.todo_steps for insert to authenticated
  with check (owner = (select auth.uid()) and (select todo_private.is_member()));
create policy todo_steps_update on public.todo_steps for update to authenticated
  using (owner = (select auth.uid()) and (select todo_private.is_member()))
  with check (owner = (select auth.uid()));

-- 即時同步
alter publication supabase_realtime add table public.todo_lists, public.todo_tasks, public.todo_steps;
