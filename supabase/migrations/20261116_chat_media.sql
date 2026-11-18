-- PrivacyX Supabase Storage + 7-Day Cloud Media Expiration Schema
-- Private bucket: chat-media (never public)

insert into storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
values (
  'chat-media',
  'chat-media',
  false,
  26214400, -- 25 MB max
  array[
    'image/webp',
    'image/avif',
    'image/jpeg',
    'image/png',
    'image/gif',
    'image/heic',
    'audio/mp4',
    'audio/m4a',
    'audio/aac',
    'audio/ogg',
    'audio/opus',
    'audio/3gpp',
    'audio/amr',
    'audio/mpeg',
    'audio/wav',
    'application/pdf',
    'application/msword',
    'application/vnd.openxmlformats-officedocument.wordprocessingml.document',
    'application/vnd.ms-excel',
    'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
    'application/vnd.ms-powerpoint',
    'application/vnd.openxmlformats-officedocument.presentationml.presentation',
    'text/plain',
    'application/zip',
    'application/x-zip-compressed'
  ]
)
on conflict (id) do update
set public = false,
    file_size_limit = excluded.file_size_limit,
    allowed_mime_types = excluded.allowed_mime_types;

create table if not exists public.media_objects (
  media_id text primary key,
  object_path text not null unique,
  thumb_path text,
  owner_uid text not null,
  conversation_id text not null,
  message_id text,
  category text not null check (category in ('image', 'voice', 'document', 'avatar', 'story', 'post')),
  mime_type text not null check (mime_type not ilike 'video/%'),
  size_bytes bigint not null check (size_bytes >= 0 and size_bytes <= 26214400),
  created_at timestamptz not null default now(),
  expires_at timestamptz not null default (now() + interval '7 days'),
  deleted_from_storage boolean not null default false,
  deleted_at timestamptz
);

create index if not exists idx_media_objects_expires
  on public.media_objects (expires_at)
  where deleted_from_storage = false;

create index if not exists idx_media_objects_owner
  on public.media_objects (owner_uid, deleted_from_storage);

alter table public.media_objects enable row level security;

-- Lock down direct anon/authenticated access: AS RESTRICTIVE ensures no other permissive policy can bypass this lock.
drop policy if exists "Deny all anon media_objects" on public.media_objects;
drop policy if exists "Restrict direct media_objects access" on public.media_objects;
create policy "Restrict direct media_objects access"
  on public.media_objects
  as restrictive
  for all
  to anon, authenticated
  using (false)
  with check (false);

-- Ensure storage.objects inside chat-media cannot be read/written directly without signed URLs.
-- Uses AS RESTRICTIVE so it never opens other buckets and blocks chat-media even if other permissive policies exist.
drop policy if exists "Deny direct chat-media access" on storage.objects;
drop policy if exists "Restrict direct chat-media access" on storage.objects;
create policy "Restrict direct chat-media access"
  on storage.objects
  as restrictive
  for all
  to anon, authenticated
  using (bucket_id <> 'chat-media')
  with check (bucket_id <> 'chat-media');
