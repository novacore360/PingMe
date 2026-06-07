-- ============================================================
-- Glimpse Messaging App - Supabase Database Schema
-- Run this in your Supabase SQL Editor
-- ============================================================

-- PROFILES
create table if not exists profiles (
  id uuid references auth.users(id) on delete cascade primary key,
  nickname text not null,
  username text unique not null,
  visibility text not null default 'public' check (visibility in ('public', 'private')),
  avatar_url text,
  created_at timestamptz default now()
);

-- Enable RLS
alter table profiles enable row level security;

create policy "Profiles are viewable by everyone"
  on profiles for select using (true);

create policy "Users can insert their own profile"
  on profiles for insert with check (auth.uid() = id);

create policy "Users can update their own profile"
  on profiles for update using (auth.uid() = id);


-- CONVERSATIONS
create table if not exists conversations (
  id uuid primary key default gen_random_uuid(),
  user1_id uuid references profiles(id) on delete cascade not null,
  user2_id uuid references profiles(id) on delete cascade not null,
  last_message text,
  last_message_at timestamptz,
  updated_at timestamptz default now(),
  created_at timestamptz default now(),
  unique(user1_id, user2_id)
);

alter table conversations enable row level security;

create policy "Users can view their conversations"
  on conversations for select
  using (auth.uid() = user1_id or auth.uid() = user2_id);

create policy "Users can create conversations"
  on conversations for insert
  with check (auth.uid() = user1_id or auth.uid() = user2_id);

create policy "Users can update their conversations"
  on conversations for update
  using (auth.uid() = user1_id or auth.uid() = user2_id);


-- MESSAGES
create table if not exists messages (
  id uuid primary key default gen_random_uuid(),
  conversation_id uuid references conversations(id) on delete cascade not null,
  sender_id uuid references profiles(id) on delete cascade not null,
  content text not null,
  status text not null default 'sent' check (status in ('sent', 'delivered', 'seen')),
  reply_to_id uuid references messages(id),
  reply_to_content text,
  reply_to_sender uuid,
  reactions jsonb,
  deleted_at timestamptz,
  created_at timestamptz default now()
);

alter table messages enable row level security;

create policy "Users can view messages in their conversations"
  on messages for select
  using (
    exists (
      select 1 from conversations c
      where c.id = messages.conversation_id
      and (c.user1_id = auth.uid() or c.user2_id = auth.uid())
    )
  );

create policy "Users can send messages"
  on messages for insert
  with check (
    auth.uid() = sender_id and
    exists (
      select 1 from conversations c
      where c.id = conversation_id
      and (c.user1_id = auth.uid() or c.user2_id = auth.uid())
    )
  );

create policy "Users can update messages"
  on messages for update
  using (
    exists (
      select 1 from conversations c
      where c.id = messages.conversation_id
      and (c.user1_id = auth.uid() or c.user2_id = auth.uid())
    )
  );


-- TYPING STATUS
create table if not exists typing_status (
  id uuid primary key default gen_random_uuid(),
  conversation_id uuid references conversations(id) on delete cascade not null,
  user_id uuid references profiles(id) on delete cascade not null,
  is_typing boolean default false,
  updated_at timestamptz default now(),
  unique(conversation_id, user_id)
);

alter table typing_status enable row level security;

create policy "Users can view typing status in their conversations"
  on typing_status for select
  using (
    exists (
      select 1 from conversations c
      where c.id = typing_status.conversation_id
      and (c.user1_id = auth.uid() or c.user2_id = auth.uid())
    )
  );

create policy "Users can upsert their own typing status"
  on typing_status for insert
  with check (auth.uid() = user_id);

create policy "Users can update their own typing status"
  on typing_status for update
  using (auth.uid() = user_id);


-- Enable Realtime for the tables
alter publication supabase_realtime add table messages;
alter publication supabase_realtime add table conversations;
alter publication supabase_realtime add table typing_status;
