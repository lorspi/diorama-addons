# Changelog — Diorama Addons: Client Map

All notable changes to `clientmap`. Releases are tagged `clientmap-v<version>`. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/) and versions follow [Semantic Versioning](https://semver.org/).

## [Unreleased]

## [0.3.0] - 2026-11-03

- **Share your own waypoints from Diorama's waypoint screen**: a "Share: Private / Public / Group" button under
  "Show on maps". Renaming, moving, recolouring or deleting the waypoint in Diorama updates (or withdraws) it for
  the others. Replaces "Add shared waypoint here" in the map menu and `/sharedwaypoint add|rename`;
  `/sharedwaypoint list|remove` stay.
- On short screens (big GUI scale) the share button sits between Save and Cancel instead of pushing them off
  the screen.
- **Map settings** (map right-click menu "Map settings…", `/clientmap settings` or the mods list): show claims on
  the map, show claims on the minimap, share my location and share my explored map, all on by default.
  `/mapexplored share|keep` sets the last one without the client mod.
- What a player explores is always kept in their own pool too, so turning sharing on, or joining a group, hands
  over what they explored before. Several pools can be synced at once.
- Leaving or being removed from a map group withdraws the waypoints shared with that group.
- Map protocol 2 and storage format 2: server and clients must be updated together.

## [0.2.0] - 2026-11-02

- Support Diorama 0.7.0 (NeoForge 1.21.1), whose map right-click menu changed shape; Diorama 0.6.0 and
  0.6.1 stay supported.
- With Diorama 0.7.0's view of another dimension's map, "Add shared waypoint" adds it in the dimension
  shown; claiming and releasing are offered only in the player's own dimension.

## [0.1.0] - 2026-11-01

- Integration with Diorama 0.6.0 (NeoForge 1.21.1): claims on the map, claim/release from the right-click
  menu, shared explored map, shared waypoints and player positions with per-player privacy.
- Sharing modes per server: disabled, global or by groups (`/mapgroup`).
- Commands: `/sharedwaypoint`, `/maplocation`, client `/clientmap status|resync`.
