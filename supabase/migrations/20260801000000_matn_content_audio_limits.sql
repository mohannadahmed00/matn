-- Phase 12 (`contracts/storage-contract.md` §2): widen `matn-content` to admit per-verse audio.
-- 10 MB matches FR-004's per-verse ceiling (FR-004b: the server must be at least as permissive as
-- the tool). The 300 MB continuous-recording ceiling needs no server counterpart because the
-- source recording is never uploaded (FR-018) — the largest object this bucket ever receives is
-- one verse.
--
-- This covers hosted projects, where the bucket already exists. A **local** stack creates the
-- bucket from `supabase/config.toml` instead, so the same two values live there too and the pair
-- has to be changed together — updating only this file is what left CI's bucket images-only.
update storage.buckets
set file_size_limit = 10 * 1024 * 1024,
    allowed_mime_types = array['image/png', 'image/jpeg', 'image/webp', 'audio/mpeg']
where id = 'matn-content';
