# Fish Game (CIS045-3 coursework)

Count the fish in the picture and choose 0-9. Pictures and answers come from the
Fish Game web service (https://marcconrad.com/uob/fish).

Author: Vinod Perera (Perera1325)

## Structure (low coupling, high cohesion)

| Package | Responsibility |
|---|---|
| `model` | Plain data: `Game` (image + solution) |
| `service` | Getting games: `GameProvider` interface, `FishApiClient` (HTTP), `FishApiParser` (data format) |
| `engine` | Game rules: score, lives, events (`GameEngine`, `GameListener`) |

## Stages

- [x] Stage 1: project, engine, API client, unit tests
- [x] Stage 2: Swing GUI and events
- [x] Stage 3: login, sign-up, hashed passwords, database, sessions
- [ ] Stage 4: leaderboard, timer, polish

## Sources and attribution

- Idea of a game as "image + integer solution", API URL and CSV/Base64 format:
  the unit's example code by Marc Conrad (provided on BREO) and the Fish Game API docs.

