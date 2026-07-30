-- The three dropped policies predate the Firestore -> Postgres migration: they were added for the
-- earlier "Storage only" swap and gate on nothing but "is signed in", which under the old Firebase
-- third-party-auth setup meant any Firebase account. Permissive RLS policies OR together, so they
-- defeated the teacher-only policies added alongside `public.matns`.
drop policy if exists "authenticated callers can upload matn-content" on storage.objects;
drop policy if exists "authenticated callers can overwrite matn-content" on storage.objects;
drop policy if exists "matn-content is publicly readable" on storage.objects;

-- FR-040 for binary objects: a cover is anonymously readable exactly when its matn is published.
-- Object paths are `matns/{matnId}/cover.{ext}`, so element 2 of the folder path is the matn id.
create policy matn_content_published_read on storage.objects
    for select to anon, authenticated
    using (
        bucket_id = 'matn-content'
        and exists (
            select 1 from public.matns m
            where m.id = (storage.foldername(name))[2]
              and m.published
        )
    );
