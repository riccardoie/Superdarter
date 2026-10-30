# Match starting order

New games draw one starting position uniformly from all participants, including
guests. The draw happens during creation and is stored in
`matches.starting_player_offset` by migration V13. Starting a pending game,
reloading it, or undoing a round never redraws the starter.

Participant order and ownership do not change. Leg N starts at
`(starting_player_offset + N - 1) % player_count`, so everyone takes a turn
starting and the rotation wraps around. Undoing and replaying a checkout restores
the same leg order. A rematch is a new match and gets a fresh draw.

Existing games receive offset zero, preserving their original order and rotation.
Solo games always start with their only participant. The API already exposes
the correct current player and leg-specific positions, so no frontend changes
are required.
