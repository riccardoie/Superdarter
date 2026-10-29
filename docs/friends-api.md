# Friends API

All endpoints require a bearer token. The acting user comes from that token;
clients cannot send requests or accept invitations on another user's behalf.
Responses expose usernames and relationship timestamps, never user entities,
passwords, or email addresses. Usernames use the same case-sensitive identity as login.

## Endpoints

| Method | Path | Result |
| --- | --- | --- |
| GET | `/api/friends` | 200: accepted friends, sorted by username |
| GET | `/api/friends/requests` | 200: pending `incoming` and `outgoing` invitations |
| POST | `/api/friends/requests` | 201: send an invitation to `{"username":"bob"}` |
| POST | `/api/friends/requests/{id}/accept` | 200: recipient accepts the invitation |
| DELETE | `/api/friends/requests/{id}` | 204: recipient declines or sender withdraws |

Sending a request requires an existing account. Leading/trailing whitespace is
trimmed; blank names, names longer than 255 characters, and self-requests return 400.
An unknown username or request ID returns 404. Unauthenticated calls return 401.
Unrelated users cannot accept or dismiss requests (403), including administrators.

## Request lifecycle

Sending an invitation creates a `PENDING` row, not a friendship. Only the recipient
can accept it. Acceptance changes the status to `ACCEPTED`, records `acceptedAt`,
and makes each user appear in the other's friends list. Accepted requests no longer
appear in the pending lists. Acceptance does not create a second reciprocal row.

Only one pending request or friendship can exist for a user pair, regardless of
direction. Sending a duplicate or reverse-direction request returns 409; crossed
invitations never automatically accept one another. The recipient must explicitly
use the accept endpoint. Repeated acceptance returns 409.

Declining or withdrawing removes the pending row, allowing a future invitation.
Accepted friendships cannot be dismissed through this endpoint (409). Unfriending,
blocking, notifications, and request history are not implemented by this change.

### Invitation response

```json
{
  "requestId": 12,
  "sender": "alice",
  "recipient": "bob",
  "status": "PENDING",
  "createdAt": "2026-10-29T14:00:00Z",
  "acceptedAt": null
}
```

After acceptance, the same shape has `status: "ACCEPTED"` and an `acceptedAt` timestamp.

### Pending invitations

```json
{
  "incoming": [],
  "outgoing": []
}
```

Each list contains invitation responses, newest first. These lists and the friends
endpoint are always scoped to the authenticated user, even if a client supplies
additional query parameters.

### Friends response

```json
[
  { "username": "bob", "friendsSince": "2026-10-29T14:05:00Z" }
]
```

## Storage and migration

`V11__friend_requests.sql` adds a new table without changing existing users or game
data. Flyway applies it on application startup. Both participants reference user
IDs with cascading deletion. A unique unordered-pair index prevents reciprocal
duplicates, and constraints enforce no self-requests and valid acceptance state.
User rows are locked in ID order when sending requests; invitation rows are locked
when accepting or dismissing them, keeping concurrent operations consistent.

The Friends frontend loads these endpoints, sends invitations through the Add
friend dialog, and displays incoming Accept/Decline and outgoing Withdraw actions.
Only accepted friends appear in the friends list. It refreshes after actions,
when the page becomes visible or regains focus, and every 15 seconds while visible.
