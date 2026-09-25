# Conversation Decision Log

Important project decisions preserved from development:
- Started from a stable Essae Android v4 base.
- Supabase was added as an audit/admin layer rather than replacing local operation.
- An earlier oversized Supabase/manager-login architecture was abandoned.
- Store login was rejected; stores operate without login.
- Admin login was retained and later hidden behind a 10-second long press on Scale Manager.
- Admin Push was removed during iteration and later restored.
- Revert-on-restart behavior was rejected because pending price state must survive until the upload result is known.
- Version 4.1.0 was considered broken and is not the stable reference.
- Several Gradle/import issues were fixed without changing the core architecture.
- Correct Supabase project URL was restored after a wrong-host DNS error.
