-- Permit Android chat video uploads in the private bucket, capped at 25 MB.
update storage.buckets
set allowed_mime_types = array[
  'image/webp', 'image/avif', 'image/jpeg', 'image/png', 'image/gif', 'image/heic',
  'audio/mp4', 'audio/m4a', 'audio/aac', 'audio/ogg', 'audio/opus', 'audio/3gpp', 'audio/amr', 'audio/mpeg', 'audio/wav',
  'video/mp4', 'video/quicktime', 'video/webm', 'video/x-m4v', 'video/x-matroska', 'video/x-msvideo',
  'application/pdf', 'application/msword',
  'application/vnd.openxmlformats-officedocument.wordprocessingml.document',
  'application/vnd.ms-excel',
  'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
  'application/vnd.ms-powerpoint',
  'application/vnd.openxmlformats-officedocument.presentationml.presentation',
  'text/plain', 'application/zip', 'application/x-zip-compressed'
]
where id = 'chat-media';

alter table public.media_objects
  drop constraint if exists media_objects_category_check,
  drop constraint if exists media_objects_mime_type_check;

alter table public.media_objects
  add constraint media_objects_category_check
    check (category in ('image', 'voice', 'document', 'video', 'avatar', 'story', 'post')),
  add constraint media_objects_mime_type_check
    check (
      (category = 'video' and mime_type ilike 'video/%')
      or (category <> 'video' and mime_type not ilike 'video/%')
    );
