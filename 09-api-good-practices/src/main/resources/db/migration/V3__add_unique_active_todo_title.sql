-- Enforce, at the database level, the business rule that a user cannot have two
-- ACTIVE todos with the same title (case-insensitive). This closes the TOCTOU race
-- that the in-service check in TodoService.create/update cannot prevent on its own.
--
-- Partial + functional index:
--   - lower(title)        -> case-insensitive, matching existsByUser...TitleIgnoreCase
--   - WHERE completed = false -> only ACTIVE todos, matching ...AndCompletedFalse
-- Completed todos are intentionally excluded, so a user may re-create a todo with the
-- same title after the previous one was completed.
CREATE UNIQUE INDEX IF NOT EXISTS uq_todo_user_title_active
    ON todo (user_id, lower(title))
    WHERE completed = false;
