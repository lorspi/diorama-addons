# Claims: permissions

## Model

- The unit of claiming is the **chunk** (full 16×16 column), per dimension.
- A **claim** has an id, a name, an owner (UUID + last known name), a dimension, a set of chunks,
  members with their own permissions, and a public permission set.
- Limits (server config, `serverconfig/dioaddons_claims-server.toml`):
  - `maxChunksPerPlayer` — total chunks a player can own across all of their claims;
  - `maxClaimsPerPlayer` — number of claims;
  - `requireContiguous` — chunks of one claim must share an edge, and releasing a chunk that would split a
    claim is refused;
  - `minSpawnDistance` — minimum distance in chunks from the overworld spawn (0 = off);
  - `maxMembersPerClaim`.
- **Being a member of someone else's claim never counts towards your own limits.**
- A chunk owned by someone else can never be claimed.
- When you claim a chunk without choosing a claim, it joins one of your claims that touches it, or a new
  claim is created.

## Permissions

| Id | Scope | Covers |
|---|---|---|
| `break_blocks` | player | Breaking blocks |
| `place_blocks` | player | Placing blocks |
| `interact_blocks` | player | Doors, buttons, levers, beds and every other block with a right-click action that is not a container |
| `open_containers` | player | Blocks with an inventory: vanilla containers and any modded block exposing an item handler capability |
| `use_items` | player | Items used on a block (flint and steel, hoes, axes stripping, shovels, bone meal…) and buckets/boats used in the air |
| `interact_entities` | player | Right-clicking entities (villagers, armor stands, item frames, riding), and hitting non-living entities |
| `damage_animals` | player | Hurting non-hostile living entities (melee and projectiles) |
| `trample_farmland` | player | Turning farmland into dirt by jumping on it |
| `pvp` | claim | Players can hurt each other inside the claim |
| `explosions` | claim | Explosions break blocks and hurt non-hostile entities inside the claim |
| `fire_spread` | claim | Fire spreads to and burns blocks inside the claim (also lava igniting blocks) |
| `mob_griefing` | claim | Mobs change blocks (endermen, withers, ravagers, mob farmland trampling…) |

*Player* permissions are given to members and to the public. *Claim* permissions are claim-wide settings:
they are stored in the public permission set and apply to everyone, including the owner.

## Resolution order

For a player permission, at a position inside a claim:

1. **Owner** → always allowed.
2. **Member** → allowed if the member has the permission **or** the public has it (a friend never has fewer
   rights than a stranger).
3. **Anyone else** → allowed only if the public has it.

Outside claims everything is allowed. Operators with **bypass** on (`/claims admin bypass`) ignore every
check and can edit any claim from the commands and the GUI.

## Admin mode (operators)

Operators (permission level `adminPermissionLevel`, 2 by default) see an **Admin mode** button in the claims
screen. It toggles the same bypass as `/claims admin bypass`. While it is on:

- The claims list shows **every claim on the server**, the operator's own first, then by owner and name,
  with a search box (claim or owner name). Other players' claims are written as `Owner: Claim` in orange.
- The selected claim can be renamed, deleted, given or denied members and public permissions, and extended
  or shrunk from the grid, ignoring limits. **Go** teleports to the middle of the claim; **Transfer** gives it
  to another player (online or known to the server), dropping that player's member entry.
- Whenever the selected claim belongs to someone else, a "⚠ Editing X's claim" banner is shown; the panel has
  an orange frame and the title says "ADMIN MODE". Claims of others on the grid are orange.

The server checks the operator level again for every admin action; a client cannot unlock admin mode.

## Templates

| Template | Permissions |
|---|---|
| Visitor | `interact_blocks` |
| Member | `interact_blocks`, `open_containers`, `use_items`, `interact_entities` |
| Trusted | every player permission |

Templates are shortcuts: after applying one, each permission can still be toggled individually.

## Border rules (not configurable)

- **Fluids** cannot flow from a chunk into a claimed chunk of a different owner.
- **Pistons** cannot push or pull blocks across into, or out of, a claim of a different owner (all positions
  touched by the piston must be unclaimed or belong to the piston's owner).

## Commands

| Command | Description |
|---|---|
| `/claim [name]` | Claim the current chunk |
| `/unclaim` | Release the current chunk |
| `/claims menu` | Open the GUI (client mod required) |
| `/claims list` | Your claims and limits |
| `/claims info` | Claim at your position and your effective permissions |
| `/claims rename <name>` / `/claims delete` | Edit the claim at your position |
| `/claims trust <player> [visitor\|member\|trusted]` | Add a friend (default: member) |
| `/claims untrust <player>` | Remove a friend |
| `/claims permission public <permission> <true\|false>` | Public permissions and claim settings |
| `/claims permission member <player> <permission> <true\|false>` | One member permission |
| `/claims permission template <player> <template>` | Apply a template to a member |
| `/claims settings` | Show the notifications and alarm of the claim at your position |
| `/claims settings welcome\|farewell title <text>` / `text <text>` / `clear` | Welcome / farewell notification |
| `/claims settings alarm owner\|members <true\|false>` | Visitor alarm |
| `/claims admin bypass` | Toggle protection bypass (operators) |
| `/claims admin list [player]` | List all claims, or one player's |
| `/claims admin delete` / `/claims admin unclaim` | Remove the claim / chunk at your position |
| `/claims admin transfer <player>` | Give the claim at your position to another player |
| `/claims admin limits <player> [chunks\|claims\|members <n\|default>\|reset]` | A player's own limits |

## Claim settings

Each claim can have a **welcome** and a **farewell** notification (title and message) and a **visitor alarm**.
Only the owner (or an operator in admin mode) changes them, from the screen's *Settings* tab or with
`/claims settings`. Texts accept `{player}`, `{claim}` and `{owner}`, colour codes `&0`–`&f`, `&#RRGGBB`, and `&l`
bold, `&o` italic, `&n` underline, `&m` strikethrough, `&r` reset. An empty message means no notification; an
empty title shows the claim's name. Notifications are vanilla titles (or action bar / chat, `greetings.display`).
The alarm tells the owner and/or the members, with a chat line and a bell, when someone who is neither comes in;
the same visitor is reported at most once every `greetings.alarmCooldownSeconds` per claim.

## Players' own limits

`maxChunksPerPlayer`, `maxClaimsPerPlayer` and `maxMembersPerClaim` are server limits. Operators can give one
player different values with `/claims admin limits`; `default` (or `reset` for all three) goes back to the server
limits. Being a member of other players' claims never counts towards any limit.

Messages are translated on the server (English and Spanish) so players without the mod read them in their
language.

## Known limitations

- Projectiles breaking item frames or paintings are not blocked.
- Other mods that change blocks without firing NeoForge events (some machines) are not covered.
- Fake players (machines acting as players) are treated as the public.
- The client may briefly show a predicted action (for example a door opening) before the server reverts it.
