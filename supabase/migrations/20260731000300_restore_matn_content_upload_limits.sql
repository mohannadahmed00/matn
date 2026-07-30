-- FR-016 was enforced server-side by the retired Firebase Storage rules
-- (`request.resource.size < 5MB` and an `image/(png|jpeg|webp)` content-type match) and was lost
-- when Storage moved to Supabase. Buckets carry the equivalent constraints natively, so a
-- malformed upload cannot land even if the client-side check is bypassed.
--
-- Phase 12 adds per-verse audio to this same bucket and will extend `allowed_mime_types`.
update storage.buckets
set file_size_limit = 5 * 1024 * 1024,
    allowed_mime_types = array['image/png', 'image/jpeg', 'image/webp']
where id = 'matn-content';
