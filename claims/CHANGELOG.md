# Changelog — Diorama Addons: Claims

All notable changes to `claims`. Releases are tagged `claims-v<version>`. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/) and versions follow [Semantic Versioning](https://semver.org/).

## [Unreleased]

## [0.3.0] - 2026-11-03

- **Claim settings tab** in the claims screen: a welcome and a farewell notification for whoever enters or leaves
  the claim (title and message, with `{player}`, `{claim}`, `{owner}` and colour codes such as `&a` or `&#FF8800`;
  without a title the claim's name is used) and a **visitor alarm** that tells the owner and/or the members when
  someone who is neither comes in (chat message and bell, at most once per visitor every
  `greetings.alarmCooldownSeconds`). Sent with vanilla packets, so players without the mod see them too; shown as
  title, action bar or chat (`greetings.display`). Same from commands: `/claims settings ...`.
- **Players' own limits**: operators can give a player more or fewer chunks, claims or members per claim than the
  server limits with `/claims admin limits <player> <chunks|claims|members> <n|default>` (or `reset`). The
  screen and `/claims list` show each player's own limits.
- The side panel is a bit wider and its four tabs are sized to their labels, so none is cut off in Spanish.
- Long texts in the settings boxes show their beginning, not their end.
- Storage format 2 (claim settings and players' limits; version 1 data loads as is). Network protocol 3: clients
  and servers must both run 0.3.x to use the screen.

## [0.2.0] - 2026-11-02

- **Admin mode in the claims screen** (operators): a button turns it on and off (same as
  `/claims admin bypass`). It lists every claim on the server, searchable by claim or owner name, and lets
  operators rename, delete, edit members and public permissions, add or release chunks, teleport to a claim
  (**Go**) and transfer it to another player. Other players' claims are drawn in orange, the panel gets an
  orange frame and a "⚠ Editing X's claim" banner.
- New `/claims admin transfer <player>`; `/claims help` lists the admin commands to operators.
- The result of an action stays visible in the screen for a few seconds.
- Network protocol version 2: clients and servers must both run 0.2.x to use the screen.

## [0.1.0] - 2026-11-01

- Chunk-based claims with per-player limits (chunks and claims), contiguity and optional spawn distance.
- Friends with per-member permissions and templates (Visitor, Member, Trusted); public permissions and
  claim settings (PvP, explosions, fire spread, mob griefing).
- Claims screen: the real terrain around you in the style of the Diorama map (texture colours, biome tints,
  sun and shadows, relief) under a chunk grid with claim colours and borders; drag selection, claim list,
  members and public tabs (key **G** or `/claims menu`).
- Commands for players without the client mod: `/claim`, `/unclaim`, `/claims ...`, `/claims admin ...`.
- Protection for blocks, containers, items, entities, animals, farmland, explosions, fire, fluids, pistons
  and mob griefing.
- Messages translated on the server (English, Spanish), so vanilla clients read them too.
- Versioned storage; game tests for protection and persistence.
