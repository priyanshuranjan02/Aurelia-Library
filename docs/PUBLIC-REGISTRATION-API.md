# Public student registration API addendum

`POST /api/auth/register` is public but CSRF-protected. Supply JSON fields `username`, `password`, `name`, `email`, and `phone`. A successful registration returns `201 Created` with a safe student profile (never a password or password hash). The server always assigns `STUDENT`; client-supplied role values are not used. Duplicate usernames return a conflict response.

Public signup does not create administrator or librarian accounts. Staff accounts remain administrator-managed. Browser clients should first call `GET /api/auth/csrf` and send the returned `XSRF-TOKEN` cookie as `X-XSRF-TOKEN` on the registration POST.
